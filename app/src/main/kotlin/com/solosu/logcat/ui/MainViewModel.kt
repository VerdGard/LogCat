package com.solosu.logcat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solosu.logcat.LogCatApplication
import com.solosu.logcat.crash.CrashRecord
import com.solosu.logcat.data.AppLog
import com.solosu.logcat.data.AppSettings
import com.solosu.logcat.data.LogRepository
import com.solosu.logcat.data.ThemeMode
import com.solosu.logcat.log.LogBuffer
import com.solosu.logcat.log.LogLevel
import com.solosu.logcat.shell.ModeAvailability
import com.solosu.logcat.shell.ShizukuManager
import com.solosu.logcat.shell.ShellMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 构建与运行环境信息,供设置页展示。 */
object BuildInfo {
    val is64Bit: Boolean by lazy {
        android.os.Process.is64Bit() && android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    }

    val abis: String get() = android.os.Build.SUPPORTED_ABIS.joinToString(", ")
}

/** MCP 服务的 UI 侧状态。 */
data class McpStatus(
    val running: Boolean = false,
    val port: Int = 0,
    val lastError: String? = null,
) {
    val endpoint: String get() = if (running) "http://127.0.0.1:$port/" else "—"
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LogCatApplication

    val logRepository: LogRepository = app.logRepository
    private val crashStore = app.crashStore

    val settings: StateFlow<AppSettings> = app.settingsState

    private val _availability = MutableStateFlow(ModeAvailability())
    val availability: StateFlow<ModeAvailability> = _availability.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _mcpStatus = MutableStateFlow(McpStatus())
    val mcpStatus: StateFlow<McpStatus> = _mcpStatus.asStateFlow()

    val crashes: StateFlow<List<CrashRecord>> = crashStore.records
    val appLogLines = AppLog.lines

    init {
        ShizukuManager.install(getApplication<Application>())
        refreshAvailability()
        viewModelScope.launch {
            ShizukuManager.state.collect { refreshAvailability() }
        }
        startLogging()
        // 上次退出时若开启了 MCP,启动时恢复
        if (app.settingsState.value.mcpEnabled) startMcp()
    }

    // ---------- 日志采集 ----------

    fun startLogging() {
        pausedByUser = false
        logRepository.start(resolveTargetMode(), settings.value.buffer)
    }

    /**
     * 用户是否主动暂停了采集。
     *
     * 之前 onResume() 只看 logRepository.running,用户在日志页按了暂停之后
     * 切到后台再回来会被强制恢复采集,暂停形同虚设。这里记住「是用户按的暂停」,
     * 回到前台时尊重这个意图;只有流因异常中断(非用户操作)才自动重启。
     */
    private var pausedByUser = false

    fun toggleLogging() {
        if (logRepository.running) {
            pausedByUser = true
            logRepository.stop()
        } else {
            pausedByUser = false
            startLogging()
        }
    }

    fun clearLogBuffer(clearSystem: Boolean) {
        if (!clearSystem) {
            logRepository.clearLocal()
            return
        }
        val mode = resolveTargetMode()
        logRepository.clearRemote(mode, settings.value.buffer) { result ->
            result.onFailure { throwable -> postMessage("清空系统日志失败:${throwable.readable()}") }
        }
    }

    // ---------- 设置:外观 ----------

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setMonetEnabled(enabled: Boolean) = update { it.copy(monetEnabled = enabled) }

    fun setPalette(paletteId: String) = update { it.copy(paletteId = paletteId) }

    fun setLogFontSize(size: Int) = update { it.copy(logFontSize = size.coerceIn(10, 20)) }

    // ---------- 设置:读取 ----------

    fun setMode(mode: ShellMode) {
        val (effective, downgraded) = availability.value.fallback(mode)
        if (downgraded) postMessage("${mode.label} 当前不可用(${unavailableReason(mode)}),已回退到默认模式")
        update(restart = true) { it.copy(mode = effective) }
    }

    fun setBuffer(buffer: LogBuffer) = update(restart = true) { it.copy(buffer = buffer) }

    fun setMaxLines(lines: Int) = update { it.copy(maxLines = lines.coerceIn(500, 20_000)) }

    // ---------- 设置:显示 ----------

    fun setLevel(level: LogLevel) = update { it.copy(level = level) }

    fun setKeyword(keyword: String) = update { it.copy(keyword = keyword) }

    fun setCompactRows(compact: Boolean) = update { it.copy(compactRows = compact) }

    fun setWrapLines(wrap: Boolean) = update { it.copy(wrapLines = wrap) }

    fun setShowTimestamp(show: Boolean) = update { it.copy(showTimestamp = show) }

    fun setShowPidTid(show: Boolean) = update { it.copy(showPidTid = show) }

    fun setAutoScroll(enabled: Boolean) = update { it.copy(autoScroll = enabled) }

    // ---------- 设置:崩溃 ----------

    fun setCaptureCrashes(enabled: Boolean) = update { it.copy(captureCrashes = enabled) }

    // ---------- 设置:MCP ----------

    fun setMcpEnabled(enabled: Boolean) {
        update { it.copy(mcpEnabled = enabled) }
        if (enabled) startMcp() else stopMcp()
    }

    fun setMcpPort(port: Int) {
        val clamped = port.coerceIn(1024, 65535)
        val wasRunning = app.mcpServer.isRunning
        update { it.copy(mcpPort = clamped) }
        if (wasRunning) {
            stopMcp()
            startMcp()
        }
    }

    fun setMcpToken(token: String) = update { it.copy(mcpToken = token) }

    fun regenerateMcpToken() {
        val token = buildString {
            repeat(32) { append("0123456789abcdef".random()) }
        }
        update { it.copy(mcpToken = token) }
        postMessage("已生成新的访问令牌")
    }

    fun startMcp() {
        val settings = settings.value
        val result = app.mcpServer.start(settings.mcpPort)
        result.onSuccess {
            _mcpStatus.value = McpStatus(
                running = true,
                port = app.mcpServer.boundPort,
                lastError = null,
            )
            postMessage("MCP 已启动:${_mcpStatus.value.endpoint}")
        }.onFailure { throwable ->
            _mcpStatus.value = McpStatus(running = false, port = 0, lastError = throwable.readable())
            postMessage("MCP 启动失败:${throwable.readable()}")
        }
    }

    fun stopMcp() {
        app.mcpServer.stop()
        _mcpStatus.value = McpStatus(running = false, port = 0, lastError = null)
    }

    fun toggleMcp() {
        if (app.mcpServer.isRunning) {
            setMcpEnabled(false)
        } else {
            setMcpEnabled(true)
        }
    }

    // ---------- 权限状态 ----------

    fun requestShizukuPermission() {
        when {
            !availability.value.shizukuInstalled -> postMessage("未检测到 Shizuku,请先安装 Shizuku 或使用 Sui")
            !ShizukuManager.isBinderAlive() -> postMessage("Shizuku 服务未运行,请在 Shizuku 管理器中启动服务")
            ShizukuManager.hasPermission() -> postMessage("Shizuku 权限已授予")
            else -> {
                ShizukuManager.requestPermission()
                postMessage("已发起 Shizuku 授权请求")
            }
        }
    }

    fun refreshModes() {
        ShizukuManager.refresh()
        refreshAvailability()
        postMessage(
            buildString {
                append("Root:").append(if (availability.value.root) "可用" else "未检测到")
                append(";Shizuku:").append(
                    when {
                        !availability.value.shizukuInstalled -> "未安装"
                        !ShizukuManager.isBinderAlive() -> "服务未运行"
                        availability.value.shizukuUsable -> "已授权"
                        else -> "未授权"
                    }
                )
            }
        )
    }

    private fun resolveTargetMode(): ShellMode {
        val requested = settings.value.mode
        val (effective, downgraded) = availability.value.fallback(requested)
        if (downgraded) postMessage("${requested.label} 不可用(${unavailableReason(requested)}),本次以默认模式读取")
        return effective
    }

    private fun unavailableReason(mode: ShellMode): String = when (mode) {
        ShellMode.ROOT -> "未检测到 su"
        ShellMode.SHIZUKU -> when {
            !availability.value.shizukuInstalled -> "Shizuku 未安装"
            !ShizukuManager.isBinderAlive() -> "Shizuku 服务未运行"
            else -> "未授予权限"
        }
        ShellMode.DEFAULT -> ""
    }

    private fun refreshAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = app.modeAvailability()
            withContext(Dispatchers.Main) { _availability.value = snapshot }
        }
    }

    // ---------- 崩溃 ----------

    fun removeCrash(id: String) {
        crashStore.remove(id).onFailure { throwable ->
            postMessage("删除崩溃记录失败:${throwable.readable()}")
        }
    }

    fun clearCrashes() {
        crashStore.clear().onFailure { throwable ->
            postMessage("清空崩溃记录失败:${throwable.readable()}")
        }
    }

    fun exportCrashes(onReady: (String) -> Unit, record: CrashRecord?) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.Default) {
                if (record != null) record.toText()
                else crashes.value.joinToString("\n\n") { it.toText() }
            }
            onReady(text)
        }
    }

    // ---------- 应用内日志 ----------

    fun clearAppLogs() = AppLog.clear()

    fun consumeMessage() {
        _message.value = null
    }

    fun postMessage(text: String) {
        _message.value = text
    }

    // ---------- 生命周期 ----------

    fun onResume() {
        // 用户主动暂停的不自动恢复;其余情况(流异常中断、进程刚起)自动拉起
        if (!logRepository.running && !pausedByUser) startLogging()
    }

    override fun onCleared() {
        // LogRepository 属于 Application,不随 ViewModel 销毁;仅在本进程退出时随进程结束
        super.onCleared()
    }

    private fun update(restart: Boolean = false, transform: (AppSettings) -> AppSettings) {
        val previous = app.updateSettings(transform)
        val next = app.settingsState.value
        if (next == previous) return

        if (next.maxLines != previous.maxLines) logRepository.reapplyLimit(next.maxLines)

        val needRestart = restart || next.mode != previous.mode || next.buffer != previous.buffer
        if (needRestart && logRepository.running) {
            logRepository.start(resolveTargetMode(), next.buffer)
        }
    }

    private fun Throwable.readable(): String = message ?: javaClass.simpleName
}

private fun CrashRecord.toText(): String = buildString {
    appendLine("时间: $time")
    appendLine("来源: $module" + if (fromSystem) "(系统日志)" else "(应用崩溃)")
    appendLine("线程/Tag: $thread")
    appendLine("异常: $exception")
    appendLine("---")
    append(stackTrace)
}
