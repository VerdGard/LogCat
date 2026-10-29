package com.solosu.logcat.shell

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/** Shizuku/Sui 的授权状态快照。 */
data class ShizukuState(
    val installed: Boolean = false,
    val binderAlive: Boolean = false,
    val granted: Boolean = false,
) {
    val usable: Boolean get() = installed && binderAlive && granted
}

/**
 * Shizuku / Sui 授权与远程进程封装。
 * Shizuku 未安装或服务未启动时必须安全降级,绝不抛出到 UI 层。
 */
object ShizukuManager {

    const val PERMISSION_REQUEST_CODE = 0x5A1

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        grantedHint = grantResult == PackageManager.PERMISSION_GRANTED
        refresh()
    }

    private var grantedHint: Boolean? = null

    fun install(context: Context) {
        _state.value = _state.value.copy(installed = isInstalled(context))
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        refresh()
    }

    fun refresh() {
        val installed = _state.value.installed
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = if (alive) {
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                .getOrElse { grantedHint == true }
        } else {
            false
        }
        _state.value = ShizukuState(installed = installed, binderAlive = alive, granted = granted)
    }

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun hasPermission(): Boolean = _state.value.granted

    fun isBinderAlive(): Boolean = _state.value.binderAlive

    fun shouldShowRationale(): Boolean = runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
    }

    /**
     * 通过 Shizuku 启动远程进程。
     * Shizuku 13.x 未公开 newProcess,沿用社区惯用的反射调用;失败时抛出可读异常。
     */
    fun newProcess(command: List<String>): Process {
        check(isBinderAlive()) { "Shizuku 服务未运行" }
        check(hasPermission()) { "尚未授予 Shizuku 权限" }
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        val value = method.invoke(null, command.toTypedArray(), null, null)
        check(value is Process) { "Shizuku 返回了非预期类型:${value?.javaClass?.name}" }
        return value
    }
}
