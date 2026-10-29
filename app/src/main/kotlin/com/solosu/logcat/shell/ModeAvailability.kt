package com.solosu.logcat.shell

/** 各模式的可用性快照,用于设置页展示与自动降级。 */
data class ModeAvailability(
    val root: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuUsable: Boolean = false,
) {
    fun isAvailable(mode: ShellMode): Boolean = when (mode) {
        ShellMode.DEFAULT -> true
        ShellMode.ROOT -> root
        ShellMode.SHIZUKU -> shizukuUsable
    }

    /** 请求的模式不可用时回退到默认模式,并返回是否为降级。 */
    fun fallback(mode: ShellMode): Pair<ShellMode, Boolean> =
        if (isAvailable(mode)) mode to false else ShellMode.DEFAULT to (mode != ShellMode.DEFAULT)
}
