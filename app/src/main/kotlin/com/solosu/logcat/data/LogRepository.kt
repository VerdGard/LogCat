package com.solosu.logcat.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.solosu.logcat.crash.CrashStore
import com.solosu.logcat.log.LogBuffer
import com.solosu.logcat.log.LogEntry
import com.solosu.logcat.log.LogLevel
import com.solosu.logcat.log.LogSource
import com.solosu.logcat.log.rawLine
import com.solosu.logcat.shell.ShellMode
import com.solosu.logcat.shell.Shells
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 全局唯一日志通道:页面切换不中断日志流,崩溃页与日志页共享同一份数据。
 *
 * 设计要点:
 * 1. logcat 输出先进 Channel,消费协程按约 120ms 批量提交,避免高频重组;
 * 2. 通过 maxLines 做环形裁剪,控制内存与重组成本;
 * 3. FATAL 级别日志自动沉淀为崩溃记录。
 */
class LogRepository(
    private val logSource: LogSource,
    private val crashStore: CrashStore,
    private val settingsProvider: () -> AppSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val entries: SnapshotStateList<LogEntry> = mutableStateListOf()

    var running by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    var activeMode by mutableStateOf(ShellMode.DEFAULT)
        private set
    var droppedLines by mutableStateOf(0)
        private set

    var activeBuffer by mutableStateOf(LogBuffer.ALL)
        private set

    private var streamJob: Job? = null

    /**
     * 日志流代际号。每次 start()/stop() 递增,协程在 finally 中只有确认自己
     * 仍是当前代际才允许改写 running。
     *
     * 原因:start() 会先 stop() 再启动,而 cancel() 是异步的,旧协程的 finally
     * 可能在新协程启动之后才执行。没有这道判断时,旧协程会把新流刚设好的
     * running=true 覆盖成 false,表现为「界面已暂停但实际仍在采集」。
     */
    private var generation = 0

    /** 全局自增序号:日志流重启后解析器的 seq 会从 1 重新计数,这里统一重编号以保证 LazyColumn key 唯一。 */
    private var seqCounter = 0L

    fun start(mode: ShellMode, buffer: LogBuffer, pid: Int? = Shells.resolvePid(mode, android.os.Process.myPid())) {
        stop()
        val myGeneration = ++generation
        activeMode = mode
        activeBuffer = buffer
        lastError = null
        running = true
        AppLog.info("开始读取日志:模式=${mode.label},缓冲=${buffer.label}" + (pid?.let { ",pid=$it" } ?: ""))

        val channel = Channel<LogEntry>(Channel.UNLIMITED)

        streamJob = scope.launch {
            val pump = launch {
                while (isActive) {
                    val first = channel.receiveCatching().getOrNull() ?: break
                    val batch = ArrayList<LogEntry>(PUMP_BATCH)
                    batch += first
                    while (batch.size < PUMP_BATCH) {
                        val more = channel.tryReceive().getOrNull() ?: break
                        batch += more
                    }
                    append(batch)
                    delay(PUMP_INTERVAL_MS)
                }
            }

            try {
                logSource.stream(mode, buffer, pid).collect { entry ->
                    channel.trySend(entry)
                    detectCrash(entry)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                val reason = throwable.message?.takeIf { it.isNotBlank() } ?: throwable.javaClass.simpleName
                // 被自己 cancel 掉的流不是错误,否则切换模式时会闪一条假错误
                if (throwable !is kotlinx.coroutines.CancellationException && generation == myGeneration) {
                    lastError = "${mode.label}:$reason"
                    AppLog.error("日志流中断:$reason")
                }
            } finally {
                channel.close()
                pump.join()
                // 只有仍是当前代际才清理状态,避免旧协程覆盖新流
                if (generation == myGeneration) running = false
            }
        }
    }

    fun stop() {
        val wasRunning = running
        generation++
        streamJob?.cancel()
        streamJob = null
        running = false
        if (wasRunning) AppLog.info("已停止读取日志")
    }

    fun clearLocal() {
        entries.clear()
        droppedLines = 0
        seqCounter = 0L
        AppLog.info("已清空本地日志缓冲")
    }

    fun clearRemote(mode: ShellMode, buffer: LogBuffer, onResult: (Result<Unit>) -> Unit) {
        scope.launch {
            val result = logSource.clear(buffer, mode)
            if (result.isSuccess) clearLocal()
            onResult(result)
        }
    }

    /** 供外部(MCP)在设置变化后按需重启。 */
    fun restartIfRunning(mode: ShellMode, buffer: LogBuffer) {
        if (running) start(mode, buffer)
    }

    fun reapplyLimit(maxLines: Int) {
        val overflow = entries.size - maxLines
        if (overflow > 0) {
            entries.removeRange(0, overflow)
            droppedLines += overflow
        }
    }

    /** 当前缓冲快照(供 MCP / 导出使用,避免外部直接触碰 SnapshotStateList)。 */
    fun snapshot(): List<LogEntry> = entries.toList()

    /** 导出当前可见缓冲为文本。 */
    fun exportVisible(visible: List<LogEntry>): String =
        visible.joinToString(separator = "\n") { entry -> entry.rawLine() }

    private fun append(batch: List<LogEntry>) {
        val limit = settingsProvider().maxLines
        entries.addAll(batch.map { entry -> entry.copy(seq = ++seqCounter) })
        val overflow = entries.size - limit
        if (overflow > 0) {
            entries.removeRange(0, overflow)
            droppedLines += overflow
        }
    }

    /**
     * FATAL 级别或 FATAL EXCEPTION 段落 → 存档为崩溃记录。
     *
     * 去重:同一次崩溃在 logcat 里会伴随多行 stack,每一行若都带 FATAL 字样
     * 就会生成多条记录。这里用「时间 + tag + 异常首行」做短期指纹,
     * 同一个进程在 [CRASH_DEDUP_WINDOW_MS] 内只记一条。
     */
    private fun detectCrash(entry: LogEntry) {
        if (!settingsProvider().captureCrashes) return
        val isFatal = entry.level == LogLevel.FATAL || entry.message.contains("FATAL EXCEPTION")
        if (!isFatal) return
        val record = CrashStore.newSystemRecord(
            time = entry.clock,
            tag = entry.tag.ifBlank { "system" },
            message = entry.message,
        )
        val fingerprint = "${record.time}|${record.thread}|${record.exception}"
        val now = android.os.SystemClock.elapsedRealtime()
        val last = crashFingerprint
        if (fingerprint == last?.first && now - last.second < CRASH_DEDUP_WINDOW_MS) return
        crashFingerprint = fingerprint to now
        scope.launch(Dispatchers.IO) {
            // 写盘失败已在 CrashStore 内部记入 AppLog,这里只需消费掉 Result
            crashStore.add(record)
        }
    }

    private var crashFingerprint: Pair<String, Long>? = null

    private companion object {
        const val PUMP_BATCH = 400
        const val PUMP_INTERVAL_MS = 120L
        const val CRASH_DEDUP_WINDOW_MS = 2_000L
    }
}
