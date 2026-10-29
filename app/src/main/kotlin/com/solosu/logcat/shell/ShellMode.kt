package com.solosu.logcat.shell

/**
 * 日志读取权限模式。
 * DEFAULT 无需任何授权,但系统只允许读取本应用自身的日志;
 * ROOT 通过 su 以 root 身份读取完整日志;
 * SHIZUKU 通过 Shizuku/Sui 以 adb(shell) 身份读取完整日志。
 */
enum class ShellMode(val id: String, val label: String, val description: String) {
    DEFAULT("default", "默认模式", "仅能读取本应用自身日志,无需授权"),
    SHIZUKU("shizuku", "Shizuku", "通过 Shizuku 或 Sui 授权,可读取全部日志"),
    ROOT("root", "Root", "通过 su 提权,可读取全部日志");

    companion object {
        fun fromId(id: String?): ShellMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
