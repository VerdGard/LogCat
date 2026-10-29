package com.solosu.logcat.log

import com.solosu.logcat.shell.ShellMode
import com.solosu.logcat.shell.Shells
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader

/**
 * 日志流数据源:把 logcat 进程的 stdout 转成 [LogEntry] 流。
 * 三模式差异全部收敛在 [Shells],这里只关心命令与解析。
 *
 * 取消语义:收集被取消时 awaitClose 会 destroy 进程,从而让阻塞的 readLine 返回,
 * 避免后台残留 logcat 进程。
 */
class LogSource(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun stream(
        mode: ShellMode,
        buffer: LogBuffer,
        pid: Int? = null,
    ): Flow<LogEntry> = callbackFlow {
        val command = LogcatCommand.stream(mode, buffer, pid)
        var process: Process? = null
        var reader: BufferedReader? = null

        val job = launch(io) {
            try {
                val started = Shells.startLogStream(mode, command)
                process = started
                val bufferedReader = started.inputStream.bufferedReader()
                reader = bufferedReader
                val parser = LogcatParser()

                while (isActive) {
                    val line = bufferedReader.readLine() ?: break
                    parser.feed(line) { entry -> trySend(entry) }
                }
                parser.flush { entry -> trySend(entry) }
                if (isActive) {
                    val code = runCatching { started.exitValue() }.getOrDefault(-1)
                    val hint = if (mode == ShellMode.DEFAULT) {
                        "logcat 已退出(code=$code)。默认模式下仅能读取本应用日志"
                    } else {
                        "logcat 已退出(code=$code)"
                    }
                    close(IllegalStateException(hint))
                }
            } catch (throwable: Throwable) {
                if (isActive) close(throwable)
            }
        }

        awaitClose {
            job.cancel()
            runCatching { reader?.close() }
            runCatching { process?.destroy() }
        }
    }.flowOn(io)

    /** 一次性拉取系统缓冲区(崩溃页 / 导出使用)。 */
    suspend fun dump(buffer: LogBuffer, lines: Int, level: LogLevel, mode: ShellMode): List<LogEntry> =
        withContext(io) {
            val command = LogcatCommand.dump(buffer, lines, level)
            val text = runCatching { Shells.execOnce(mode, command) }
                .getOrElse { return@withContext emptyList() }
            val parser = LogcatParser()
            val result = ArrayList<LogEntry>()
            text.lineSequence().forEach { line -> parser.feed(line) { result += it } }
            parser.flush { result += it }
            result
        }

    suspend fun clear(buffer: LogBuffer, mode: ShellMode): Result<Unit> = withContext(io) {
        runCatching { Shells.execOnce(mode, LogcatCommand.clear(buffer)) }.map { }
    }
}
