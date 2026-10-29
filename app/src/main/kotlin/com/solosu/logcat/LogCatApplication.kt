package com.solosu.logcat

import android.app.Application
import com.solosu.logcat.crash.CrashHandler
import com.solosu.logcat.crash.CrashStore
import com.solosu.logcat.data.AppLog
import com.solosu.logcat.data.SettingsRepository
import com.solosu.logcat.log.LogSource
import com.solosu.logcat.shell.ShizukuManager

/** 手动依赖容器。项目规模不大,不引入 Hilt,减少构建复杂度。 */
class LogCatApplication : Application() {

    lateinit var settings: SettingsRepository
        private set
    lateinit var crashStore: CrashStore
        private set
    lateinit var logSource: LogSource
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        crashStore = CrashStore(this)
        crashStore.load()
        logSource = LogSource()
        CrashHandler(this, crashStore).install()
        AppLog.info("LogCat 启动,${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE} " +
            "(${android.os.Build.SUPPORTED_ABIS.joinToString()})")
        AppLog.info("Shizuku 已安装=${ShizukuManager.isInstalled(this)},已授权=${ShizukuManager.hasPermission()}")
    }

    companion object {
        lateinit var instance: LogCatApplication
            private set
    }
}
