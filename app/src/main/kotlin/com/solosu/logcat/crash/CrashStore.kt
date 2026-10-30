package com.solosu.logcat.crash

import android.content.Context
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

    fun add(record: CrashRecord) {
        synchronized(lock) {
            val current = readAll()
            val updated = (listOf(record) + current).take(MAX_RECORDS)
            writeAll(updated)
            _records.value = updated
        }
    }

    fun remove(id: String) {
        synchronized(lock) {
            val updated = readAll().filterNot { it.id == id }
            writeAll(updated)
            _records.value = updated
        }
    }

    fun clear() {
        synchronized(lock) {
            writeAll(emptyList())
            _records.value = emptyList()
        }
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
        }.getOrDefault(emptyList())
    }

    private fun writeAll(records: List<CrashRecord>) {
        runCatching {
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
            file.writeText(array.toString())
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
