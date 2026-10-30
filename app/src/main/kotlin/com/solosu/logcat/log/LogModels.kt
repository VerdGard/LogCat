package com.solosu.logcat.log

/**
 * logcat 在切换缓冲区时输出的分隔行前缀,例如 "--------- beginning of main"。
 * 这类行既不是日志头也不能算续行,解析与原始文本导出都必须剔除。
 */
const val LOGCAT_BUFFER_SEPARATOR = "--------- beginning of"

/** logcat 日志级别,order 用于“最小级别”过滤。 */
enum class LogLevel(val letter: Char, val label: String, val order: Int) {
    VERBOSE('V', "Verbose", 0),
    DEBUG('D', "Debug", 1),
    INFO('I', "Info", 2),
    WARN('W', "Warn", 3),
    ERROR('E', "Error", 4),
    FATAL('F', "Fatal", 5),
    ASSERT('A', "Assert", 5);

    companion object {
        fun fromLetter(letter: Char): LogLevel = entries.firstOrNull { it.letter == letter } ?: VERBOSE
    }
}

/** logcat 缓冲区。ALL 会同时读取 main/system/crash 等。 */
enum class LogBuffer(val id: String, val label: String) {
    ALL("all", "全部"),
    MAIN("main", "main"),
    SYSTEM("system", "system"),
    CRASH("crash", "crash");

    companion object {
        fun fromId(id: String?): LogBuffer = entries.firstOrNull { it.id == id } ?: ALL
    }
}

/**
 * 一条已解析的日志。[seq] 是单调递增序号,作为 LazyColumn 稳定 key。
 * [overflow] 表示单条日志过长被截断。
 */
data class LogEntry(
    val seq: Long,
    val date: String,
    val time: String,
    val pid: Int,
    val tid: Int,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val overflow: Boolean = false,
) {
    val clock: String get() = "$date $time"

    /**
     * 用于关键字匹配的小写文本。
     *
     * 写成类体里的普通 val 而非 `by lazy`:LogRepository 会为每条日志 copy 一次
     * 以重编 seq,`by lazy` 会在每次 copy 时重建 Lazy 对象并丢失已算结果,
     * 反而比直接计算更贵。放在类体(而非构造参数)可以避免污染 equals/copy。
     */
    val searchText: String = "$tag $message".lowercase()
}

/** 还原为接近 logcat -v threadtime 的单行文本,便于复制/导出。 */
fun LogEntry.rawLine(): String = buildString {
    append(date).append(' ').append(time)
    append(' ').append(pid.toString().padStart(5)).append(' ').append(tid.toString().padStart(5))
    append(' ').append(level.letter).append(' ').append(tag).append(": ").append(message)
}
