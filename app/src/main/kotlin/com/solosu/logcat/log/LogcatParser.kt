package com.solosu.logcat.log

/**
 * threadtime 格式解析器:
 * `MM-DD HH:MM:SS.mmm  PID  TID L TAG     : message`
 * 续行(含空行与堆栈)会附加到上一条日志,便于崩溃栈阅读。
 */
class LogcatParser(private val maxMessageLength: Int = 20_000) {

    private var seq = 0L
    private val pending = ArrayList<String>(64)
    private var last: LogEntry? = null

    // 以 "  PID  TID L " 开头,容忍前导空白(部分设备多一个空格)
    private val pattern = Regex(
        "^\\s*(\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFA])\\s+(.*?)\\s*: (.*)$"
    )

    /** 每行调用一次;遇到新的日志头时把上一条(含续行)通过 [onEntry] 提交。 */
    fun feed(line: String, onEntry: (LogEntry) -> Unit) {
        // logcat 在切换缓冲区时会输出 "--------- beginning of main/system/crash"。
        // 这类分隔行既不是日志头也不是续行,必须丢弃;否则会被并进上一条日志的
        // message 里,污染导出内容与崩溃堆栈。
        if (line.startsWith(LOGCAT_BUFFER_SEPARATOR)) return

        val match = pattern.matchEntire(line)
        if (match != null) {
            flush(onEntry)
            val (date, time, pid, tid, letter, tag, message) = match.destructured
            val trimmed = message.trimEnd()
            val overflow = trimmed.length > maxMessageLength
            last = LogEntry(
                seq = ++seq,
                date = date,
                time = time,
                pid = pid.toIntOrNull() ?: 0,
                tid = tid.toIntOrNull() ?: 0,
                level = LogLevel.fromLetter(letter[0]),
                tag = tag.trim(),
                message = if (overflow) trimmed.take(maxMessageLength) else trimmed,
                overflow = overflow,
            )
            return
        }
        // 续行:先并入 pending,等遇到下一条日志头再整体提交
        if (line.isNotBlank() || pending.isNotEmpty()) {
            if (pending.size < MAX_CONTINUATION_LINES) pending += line
        }
    }

    /** 把最后一条日志与其续行合并后输出。 */
    fun flush(onEntry: (LogEntry) -> Unit) {
        val base = last ?: return
        last = null
        if (pending.isEmpty()) {
            onEntry(base)
            return
        }
        val extra = pending.joinToString("\n")
        pending.clear()
        val merged = "${base.message}\n$extra"
        onEntry(if (merged.length > maxMessageLength) base.copy(message = merged.take(maxMessageLength), overflow = true) else base.copy(message = merged))
    }

    fun reset() {
        pending.clear()
        last = null
        seq = 0
    }

    private companion object {
        const val MAX_CONTINUATION_LINES = 400
    }
}
