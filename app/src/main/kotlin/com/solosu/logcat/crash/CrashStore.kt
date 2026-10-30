package com.solosu.logcat.crash

import android.content.Context
import com.solosu.logcat.data.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** 一条崩溃记录。[id] 稳定,可直接作为列表 key。 */
data class CrashRecord(
    val id: String,
    val time: String,
    val thread: String,
    val exception: String,
    val stackTrace: String,
    val module: String = "app",
    val fromSystem: Boolean = false,
) {
    val title: String get() = exception.lineSequence().firstOrNull()?.trim().orEmpty().ifEmpty { "未知异常" }

    /** 崩溃标签页可作为关键字使用的主类名,例如 com.example.Foo$Bar */
    val topFrameClass: String?
        get() = stackTrace.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("at ") && !it.startsWith("at android.") && !it.startsWith("at java.") && !it.startsWith("at kotlin.") }
            ?.removePrefix("at ")
            ?.substringBefore('(')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

/**
 * 崩溃记录仓库:本地 JSON 单文件持久化。
 * 不需要数据库,避免 Room/KSP 额外构建开销。
 */
class CrashStore(private val context: Context) {

    private val file = File(context.filesDir, "crashes.json")
    private val lock = Any()

    private val _records = MutableStateFlow<List<CrashRecord>>(emptyList())
    val records: StateFlow<List<CrashRecord>> = _records.asStateFlow()

    fun load() {
        val loaded = synchronized(lock) { readAll() }
        _records.value = loaded
    }

    /*
     * 下面三个写操作都在「先写盘、写成功才更新内存」的顺序下执行。
     * 反过来(先更新 _records 再写盘)会让磁盘失败时界面显示成功,
     * 用户重启后发现记录凭空消失,比直接报错更难排查。
     */
    fun add(record: CrashRecord): Result<Unit> = synchronized(lock) {
        val updated = (listOf(record) + readAll()).take(MAX_RECORDS)
        writeAll(updated).onSuccess { _records.value = updated }
    }

    fun remove(id: String): Result<Unit> = synchronized(lock) {
        val updated = readAll().filterNot { it.id == id }
        writeAll(updated).onSuccess { _records.value = updated }
    }

    fun clear(): Result<Unit> = synchronized(lock) {
        writeAll(emptyList()).onSuccess { _records.value = emptyList() }
    }

    fun find(id: String): CrashRecord? = _records.value.firstOrNull { it.id == id }

    private fun readAll(): List<CrashRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    this.add(
                        CrashRecord(
                            id = json.optString("id"),
                            time = json.optString("time"),
                            thread = json.optString("thread"),
                            exception = json.optString("exception"),
                            stackTrace = json.optString("stackTrace"),
                            module = json.optString("module", "app"),
                            fromSystem = json.optBoolean("fromSystem", false),
                        )
                    )
                }
            }
        }.getOrElse { throwable ->
            // 之前这里 getOrDefault 会静默吃掉解析错误:损坏的文件看起来像「没有崩溃记录」,
            // 用户下一次写入就把它覆盖掉了。现在先备份再当空处理。
            quarantineBrokenFile(throwable.message ?: throwable.javaClass.simpleName)
            emptyList()
        }
    }

    /**
     * 全量写回。返回是否成功。
     *
     * 原实现用 runCatching{} 包住后什么也不做:磁盘满或权限异常时记录直接丢失,
     * 界面上完全看不出来。这里把失败上报到 AppLog(设置页「工具日志」可见),
     * 并让调用方决定是否对外返回失败。
     *
     * 采用「先写临时文件再 rename」的方式,避免写到一半进程被杀导致 crashes.json
     * 变成半截 JSON(那样下次 readAll 解析失败会静默清空全部历史)。
     */
    private fun writeAll(records: List<CrashRecord>): Result<Unit> = runCatching {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("time", record.time)
                    put("thread", record.thread)
                    put("exception", record.exception)
                    put("stackTrace", record.stackTrace)
                    put("module", record.module)
                    put("fromSystem", record.fromSystem)
                }
            )
        }
        val payload = array.toString()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(payload)
        if (!tmp.renameTo(file)) {
            // 某些文件系统上 rename 到已存在目标会失败,退化为覆盖写
            file.writeText(payload)
            tmp.delete()
        }
    }.onFailure { throwable ->
        AppLog.error("崩溃记录写入失败:${throwable.message ?: throwable.javaClass.simpleName}")
    }

    /** 读档失败时保留原文件并改名备份,避免下次写入把用户历史直接覆盖掉。 */
    private fun quarantineBrokenFile(reason: String) {
        AppLog.warn("崩溃记录文件损坏,已备份:${file.name} ($reason)")
        runCatching {
            val backup = File(file.parentFile, "${file.name}.broken")
            if (backup.exists()) backup.delete()
            file.renameTo(backup)
        }
    }

    companion object {
        private const val MAX_RECORDS = 100

        fun newId(): String = UUID.randomUUID().toString()

        fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        /** 从 logcat 的 FATAL EXCEPTION 段落构造记录。 */
        fun newSystemRecord(time: String, tag: String, message: String): CrashRecord {
            val exception = message.lineSequence().firstOrNull { it.contains("Exception") || it.contains("Error") }
                ?: message.lineSequence().firstOrNull()
                ?: "未知异常"
            return CrashRecord(
                id = newId(),
                time = time,
                thread = tag,
                exception = exception,
                stackTrace = message,
                module = "system",
                fromSystem = true,
            )
        }
    }
}
