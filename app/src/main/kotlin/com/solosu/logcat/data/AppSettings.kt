package com.solosu.logcat.data

import android.content.Context
import com.solosu.logcat.log.LogBuffer
import com.solosu.logcat.log.LogLevel
import com.solosu.logcat.shell.ShellMode

/** 用户可持久化配置。用 SharedPreferences 足够,避免引入 DataStore/Room 的额外构建开销。 */
data class AppSettings(
    val mode: ShellMode = ShellMode.DEFAULT,
    val level: LogLevel = LogLevel.VERBOSE,
    val buffer: LogBuffer = LogBuffer.ALL,
    val maxLines: Int = 3000,
    val keyword: String = "",
    val wrapLines: Boolean = true,
    val showTimestamp: Boolean = true,
    val showPidTid: Boolean = true,
    val autoScroll: Boolean = true,
)

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        mode = ShellMode.fromId(prefs.getString(KEY_MODE, null)),
        level = LogLevel.entries.firstOrNull { it.letter.toString() == prefs.getString(KEY_LEVEL, null) }
            ?: LogLevel.VERBOSE,
        buffer = LogBuffer.fromId(prefs.getString(KEY_BUFFER, null)),
        maxLines = prefs.getInt(KEY_MAX_LINES, 3000).coerceIn(MIN_LINES, MAX_LINES),
        keyword = prefs.getString(KEY_KEYWORD, "").orEmpty(),
        wrapLines = prefs.getBoolean(KEY_WRAP, true),
        showTimestamp = prefs.getBoolean(KEY_TIMESTAMP, true),
        showPidTid = prefs.getBoolean(KEY_PID_TID, true),
        autoScroll = prefs.getBoolean(KEY_AUTO_SCROLL, true),
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_MODE, settings.mode.id)
            .putString(KEY_LEVEL, settings.level.letter.toString())
            .putString(KEY_BUFFER, settings.buffer.id)
            .putInt(KEY_MAX_LINES, settings.maxLines)
            .putString(KEY_KEYWORD, settings.keyword)
            .putBoolean(KEY_WRAP, settings.wrapLines)
            .putBoolean(KEY_TIMESTAMP, settings.showTimestamp)
            .putBoolean(KEY_PID_TID, settings.showPidTid)
            .putBoolean(KEY_AUTO_SCROLL, settings.autoScroll)
            .apply()
    }

    companion object {
        const val MIN_LINES = 500
        const val MAX_LINES = 20_000

        private const val PREFS = "logcat_settings"
        private const val KEY_MODE = "mode"
        private const val KEY_LEVEL = "level"
        private const val KEY_BUFFER = "buffer"
        private const val KEY_MAX_LINES = "max_lines"
        private const val KEY_KEYWORD = "keyword"
        private const val KEY_WRAP = "wrap"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_PID_TID = "pid_tid"
        private const val KEY_AUTO_SCROLL = "auto_scroll"
    }
}
