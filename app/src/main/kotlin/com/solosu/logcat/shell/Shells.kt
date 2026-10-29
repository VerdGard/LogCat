package com.solosu.logcat.shell

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 按模式创建进程,统一返回可读取 stdout 的 [Process]。
 * 三种模式的差异只在这里体现,上层无需关心是 su 还是 Shizuku。
 */
object Shells {

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/debug_ramdisk/su",
    )

    fun hasRootBinary(): Boolean = SU_PATHS.any { File(it).canExecute() }

    /**
     * 默认模式限定 pid 为本进程,保证「仅查看自身日志」;
     * root / shizuku 模式传 null 以读取全部日志。
     */
    fun resolvePid(mode: ShellMode, selfPid: Int): Int? =
        if (mode == ShellMode.DEFAULT) selfPid else null

    /** 启动一条日志流进程,调用方负责关闭。 */
    fun startLogStream(mode: ShellMode, command: List<String>): Process = when (mode) {
        ShellMode.DEFAULT -> ProcessBuilder(command).redirectErrorStream(true).start()
        ShellMode.ROOT -> startWithRoot(command)
        ShellMode.SHIZUKU -> ShizukuManager.newProcess(listOf("sh", "-c", command.joinToString(" ")))
    }

    /** 执行一次性命令并返回输出(logcat -c / -d 等)。 */
    fun execOnce(mode: ShellMode, command: List<String>, timeoutSeconds: Long = 15): String {
        val process = startLogStream(mode, command)
        return try {
            val text = process.inputStream.bufferedReader().use { it.readText() }
            runCatching { process.waitFor(timeoutSeconds, TimeUnit.SECONDS) }
            text
        } finally {
            runCatching { process.destroy() }
        }
    }

    private fun startWithRoot(command: List<String>): Process {
        val script = command.joinToString(" ") { arg ->
            if (arg.any(Char::isWhitespace)) "'${arg.replace("'", "'\\''")}'" else arg
        }
        return ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
    }
}
