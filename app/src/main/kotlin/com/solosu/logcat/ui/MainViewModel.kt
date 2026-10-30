package com.solosu.logcat.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solosu.logcat.LogCatApplication
import com.solosu.logcat.crash.CrashRecord
import com.solosu.logcat.data.AppLog
import com.solosu.logcat.data.AppSettings
import com.solosu.logcat.data.LogRepository
import com.solosu.logcat.log.LogBuffer
import com.solosu.logcat.log.LogLevel
import com.solosu.logcat.shell.ModeAvailability
import com.solosu.logcat.shell.ShizukuManager
import com.solosu.logcat.shell.ShellMode
import com.solosu.logcat.shell.Shells
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 编译期常量:是否 64 位构建,用于设置页展示。 */
object BuildInfo {
    val is64Bit: Boolean by lazy {
        android.os.Process.is64Bit() &&
            android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    }

    val abis: String get() = android.os.Build.SUPPORTED_ABIS.joinToString(", ")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LogCatApplication
    private val settingsRepo = app.settings
    private val crashStore = app.crashStore

    private val _settings = MutableStateFlow(settingsRepo.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _availability = MutableStateFlow(ModeAvailability())
    val availability: StateFlow<ModeAvailability> = _availability.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val logRepository: LogRepository = LogRepository(
        logSource = app.logSource,
        crashStore = crashStore,
        settingsProvider = { _settings.value },
    )

    val crashes: StateFlow<List<CrashRecord>> = crashStore.records
    val appLogLines = AppLog.lines

    init {
        ShizukuManager.install(getApplication<Application>())
        refreshAvailability()
        viewModelScope.launch {
            ShizukuManager.state.collect { refreshAvailability() }
        }
        startLogging()
    }

    // ---------- 日志 ----------

    fun startLogging() {
        val target = resolveTargetMode()
        logRepository.start(target, _settings.value.buffer)
    }

    fun toggleLogging() {
        if (logRepository.running) {
            logRepository.stop()
        } else {
            startLogging()
        }
    }

    fun clearLogBuffer(clearSystem: Boolean) {
        val mode = resolveTargetMode()
        if (!clearSystem) {
            logRepository.clearLocal()
            return
        }
        logRepository.clearRemote(mode, _settings.value.buffer) { result ->
            result.onFailure { throwable ->
                _message.value = "清空系统日志失败:${throwable.readable()}"
            }
        }
    }

    fun setLevel(level: LogLevel) = update { it.copy(level = level) }

    fun setBuffer(buffer: LogBuffer) = update { it.copy(buffer = buffer) }

    fun setKeyword(keyword: String) = update { it.copy(keyword = keyword) }

    fun setMaxLines(lines: Int) = update { it.copy(maxLines = lines.coerceIn(500, 20_000)) }

    fun setWrapLines(wrap: Boolean) = update { it.copy(wrapLines = wrap) }

    fun setShowTimestamp(show: Boolean) = update { it.copy(showTimestamp = show) }

    fun setShowPidTid(show: Boolean) = update { it.copy(showPidTid = show) }

    fun setAutoScroll(enabled: Boolean) = update { it.copy(autoScroll = enabled) }

    fun setMode(mode: ShellMode) {
        val (effective, downgraded) = _availability.value.fallback(mode)
        if (downgraded) {
            _message.value = "${mode.label} 当前不可用(${unavailableReason(mode)}),已回退到默认模式"
        }
        update(restart = true) { it.copy(mode = effective) }
    }

    fun requestShizukuPermission() {
        when {
            !_availability.value.shizukuInstalled -> _message.value = "未检测到 Shizuku,请先安装 Shizuku 或使用 Sui"
            !ShizukuManager.isBinderAlive() -> _message.value = "Shizuku 服务未运行,请在 Shizuku 管理器中启动服务"
            ShizukuManager.hasPermission() -> _message.value = "Shizuku 权限已授予"
            else -> {
                ShizukuManager.requestPermission()
                _message.value = "已发起 Shizuku 授权请求"
            }
        }
    }

    fun refreshModes() {
        ShizukuManager.refresh()
        refreshAvailability()
        _message.value = buildString {
            append("Root:").append(if (_availability.value.root) "可用" else "未检测到")
            append(";Shizuku:").append(
                when {
                    !_availability.value.shizukuInstalled -> "未安装"
                    !ShizukuManager.isBinderAlive() -> "服务未运行"
                    _availability.value.shizukuUsable -> "已授权"
                    else -> "未授权"
                }
            )
        }
    }

    private fun resolveTargetMode(): ShellMode {
        val requested = _settings.value.mode
        val (effective, downgraded) = _availability.value.fallback(requested)
        if (downgraded) {
            _message.value = "${requested.label} 不可用(${unavailableReason(requested)}),本次以默认模式读取"
        }
        return effective
    }

    private fun unavailableReason(mode: ShellMode): String = when (mode) {
        ShellMode.ROOT -> "未检测到 su"
        ShellMode.SHIZUKU -> when {
            !_availability.value.shizukuInstalled -> "Shizuku 未安装"
            !ShizukuManager.isBinderAlive() -> "Shizuku 服务未运行"
            else -> "未授予权限"
        }
        ShellMode.DEFAULT -> ""
    }

    private fun refreshAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            val context: Context = getApplication<Application>()
            // su 只查几个固定路径,但仍在 IO 线程执行,避免主线程做文件 stat
            val snapshot = ModeAvailability(
                root = Shells.hasRootBinary(),
                shizukuInstalled = ShizukuManager.isInstalled(context),
                shizukuUsable = ShizukuManager.hasPermission(),
            )
            withContext(Dispatchers.Main) { _availability.value = snapshot }
        }
    }

    // ---------- 崩溃 ----------

    fun removeCrash(id: String) = crashStore.remove(id)

    fun clearCrashes() = crashStore.clear()

    fun exportCrashes(onReady: (String) -> Unit, record: CrashRecord?) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.Default) {
                if (record != null) {
                    record.toText()
                } else {
                    crashes.value.joinToString("\n\n") { it.toText() }
                }
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

    // ---------- 绑定生命周期 ----------

    fun onResume() {
        if (!logRepository.running) startLogging()
    }

    override fun onCleared() {
        logRepository.stop()
        super.onCleared()
    }

    private fun update(restart: Boolean = false, transform: (AppSettings) -> AppSettings) {
        val previous = _settings.value
        val next = transform(previous)
        if (next == previous) return
        _settings.value = next
        settingsRepo.save(next)

        if (next.maxLines != previous.maxLines) logRepository.reapplyLimit(next.maxLines)

        val needRestart = restart ||
            next.mode != previous.mode ||
            next.buffer != previous.buffer
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
