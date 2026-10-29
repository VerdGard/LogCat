package com.solosu.logcat.log

import com.solosu.logcat.shell.ShellMode

/** 构造 logcat 命令。级别与关键字过滤放在客户端做,切换筛选时无需重启进程。 */
object LogcatCommand {

    fun stream(mode: ShellMode, buffer: LogBuffer, pid: Int? = null): List<String> {
        val args = mutableListOf("logcat", "-v", "threadtime")
        if (buffer == LogBuffer.ALL) {
            args += listOf("-b", "main", "-b", "system", "-b", "crash")
        } else {
            args += listOf("-b", buffer.id)
        }
        // 默认模式只可能读到自身日志,但显式限定 pid 更直观也更省资源。
        if (pid != null) args += listOf("--pid", pid.toString())
        args += "*:V"
        return args
    }

    /** 一次性导出当前缓冲区(崩溃页使用)。 */
    fun dump(buffer: LogBuffer, lines: Int = 2000, level: LogLevel = LogLevel.ERROR): List<String> {
        val args = mutableListOf("logcat", "-d", "-v", "threadtime", "-t", lines.toString())
        if (buffer == LogBuffer.ALL) {
            args += listOf("-b", "main", "-b", "system", "-b", "crash")
        } else {
            args += listOf("-b", buffer.id)
        }
        args += "*:${level.letter}"
        return args
    }

    fun clear(buffer: LogBuffer): List<String> {
        val args = mutableListOf("logcat", "-c")
        if (buffer == LogBuffer.ALL) {
            args += listOf("-b", "main", "-b", "system", "-b", "crash")
        } else {
            args += listOf("-b", buffer.id)
        }
        return args
    }
}
