package com.solosu.logcat

import android.app.Application
import com.solosu.logcat.crash.CrashHandler
import com.solosu.logcat.crash.CrashStore
import com.solosu.logcat.data.AppLog
import com.solosu.logcat.data.AppSettings
import com.solosu.logcat.data.LogRepository
import com.solosu.logcat.data.SettingsRepository
import com.solosu.logcat.log.LogSource
import com.solosu.logcat.mcp.McpServer
import com.solosu.logcat.shell.ModeAvailability
import com.solosu.logcat.shell.ShizukuManager
import com.solosu.logcat.shell.Shells
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手动依赖容器。项目规模不大,不引入 Hilt,减少构建复杂度。
 *
 * LogRepository 上提到 Application 单例:MCP 服务需要与 UI 共享同一份日志通道,
 * 且日志流不能因为 ViewModel 销毁而中断。
 */
class LogCatApplication : Application() {

    lateinit var settings: SettingsRepository
        private set
    lateinit var crashStore: CrashStore
        private set
    lateinit var logSource: LogSource
        private set
    lateinit var logRepository: LogRepository
        private set
    lateinit var mcpServer: McpServer
        private set

    /** 全局设置状态,UI 与 MCP 共同读写。 */
    private val _settings = MutableStateFlow(AppSettings())
    val settingsState: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        _settings.value = settings.load()
        crashStore = CrashStore(this)
        crashStore.load()
        logSource = LogSource()
        logRepository = LogRepository(
            logSource = logSource,
            crashStore = crashStore,
            settingsProvider = { _settings.value },
        )
        mcpServer = McpServer(this)
        CrashHandler(this, crashStore).install()
        AppLog.info(
            "LogCat 启动,${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE} " +
                "(${android.os.Build.SUPPORTED_ABIS.joinToString()})"
        )
        AppLog.info("Shizuku 已安装=${ShizukuManager.isInstalled(this)}")
    }

    /** 当前各模式可用性(su 探测有文件 IO,调用方应避免在主线程高频调用)。 */
    fun modeAvailability(): ModeAvailability = ModeAvailability(
        root = Shells.hasRootBinary(),
        shizukuInstalled = ShizukuManager.isInstalled(this),
        shizukuUsable = ShizukuManager.hasPermission(),
    )

    /** 更新并持久化设置,返回旧值用于判断是否需要重启日志流。 */
    fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings {
        val previous = _settings.value
        val next = transform(previous)
        if (next == previous) return previous
        _settings.value = next
        settings.save(next)
        return previous
    }

    companion object {
        lateinit var instance: LogCatApplication
            private set
    }
}
