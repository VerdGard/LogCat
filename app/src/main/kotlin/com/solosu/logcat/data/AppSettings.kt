package com.solosu.logcat.data

import android.content.Context
import com.solosu.logcat.log.LogBuffer
import com.solosu.logcat.log.LogLevel
import com.solosu.logcat.shell.ShellMode

/** 主题模式:跟随系统 / 强制浅色 / 强制深色。 */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** 用户可持久化配置。用 SharedPreferences 足够,避免引入 DataStore/Room 的额外构建开销。 */
data class AppSettings(
    // 外观
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val monetEnabled: Boolean = true,
    val paletteId: String = "green",
    val logFontSize: Int = 12,
    // 读取
    val mode: ShellMode = ShellMode.DEFAULT,
    val buffer: LogBuffer = LogBuffer.ALL,
    val maxLines: Int = 3000,
    // 显示
    val level: LogLevel = LogLevel.VERBOSE,
    val keyword: String = "",
    val wrapLines: Boolean = true,
    val showTimestamp: Boolean = true,
    val showPidTid: Boolean = true,
    val autoScroll: Boolean = true,
    // 崩溃
    val captureCrashes: Boolean = true,
    // MCP
    val mcpEnabled: Boolean = false,
    val mcpPort: Int = 8765,
    val mcpToken: String = "",
)

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        themeMode = ThemeMode.fromId(prefs.getString(KEY_THEME_MODE, null)),
        monetEnabled = prefs.getBoolean(KEY_MONET, true),
        paletteId = prefs.getString(KEY_PALETTE, "green").orEmpty().ifBlank { "green" },
        logFontSize = prefs.getInt(KEY_FONT_SIZE, 12).coerceIn(MIN_FONT, MAX_FONT),
        mode = ShellMode.fromId(prefs.getString(KEY_MODE, null)),
        buffer = LogBuffer.fromId(prefs.getString(KEY_BUFFER, null)),
        maxLines = prefs.getInt(KEY_MAX_LINES, 3000).coerceIn(MIN_LINES, MAX_LINES),
        level = LogLevel.entries.firstOrNull { it.letter.toString() == prefs.getString(KEY_LEVEL, null) }
            ?: LogLevel.VERBOSE,
        keyword = prefs.getString(KEY_KEYWORD, "").orEmpty(),
        wrapLines = prefs.getBoolean(KEY_WRAP, true),
        showTimestamp = prefs.getBoolean(KEY_TIMESTAMP, true),
        showPidTid = prefs.getBoolean(KEY_PID_TID, true),
        autoScroll = prefs.getBoolean(KEY_AUTO_SCROLL, true),
        captureCrashes = prefs.getBoolean(KEY_CAPTURE_CRASH, true),
        mcpEnabled = prefs.getBoolean(KEY_MCP_ENABLED, false),
        mcpPort = prefs.getInt(KEY_MCP_PORT, DEFAULT_MCP_PORT).coerceIn(MIN_PORT, MAX_PORT),
        mcpToken = prefs.getString(KEY_MCP_TOKEN, "").orEmpty(),
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.id)
            .putBoolean(KEY_MONET, settings.monetEnabled)
            .putString(KEY_PALETTE, settings.paletteId)
            .putInt(KEY_FONT_SIZE, settings.logFontSize)
            .putString(KEY_MODE, settings.mode.id)
            .putString(KEY_BUFFER, settings.buffer.id)
            .putInt(KEY_MAX_LINES, settings.maxLines)
            .putString(KEY_LEVEL, settings.level.letter.toString())
            .putString(KEY_KEYWORD, settings.keyword)
            .putBoolean(KEY_WRAP, settings.wrapLines)
            .putBoolean(KEY_TIMESTAMP, settings.showTimestamp)
            .putBoolean(KEY_PID_TID, settings.showPidTid)
            .putBoolean(KEY_AUTO_SCROLL, settings.autoScroll)
            .putBoolean(KEY_CAPTURE_CRASH, settings.captureCrashes)
            .putBoolean(KEY_MCP_ENABLED, settings.mcpEnabled)
            .putInt(KEY_MCP_PORT, settings.mcpPort)
            .putString(KEY_MCP_TOKEN, settings.mcpToken)
            .apply()
    }

    companion object {
        const val MIN_LINES = 500
        const val MAX_LINES = 20_000
        const val MIN_FONT = 10
        const val MAX_FONT = 20
        const val DEFAULT_MCP_PORT = 8765
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535

        private const val PREFS = "logcat_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MONET = "monet"
        private const val KEY_PALETTE = "palette"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_MODE = "mode"
        private const val KEY_BUFFER = "buffer"
        private const val KEY_MAX_LINES = "max_lines"
        private const val KEY_LEVEL = "level"
        private const val KEY_KEYWORD = "keyword"
        private const val KEY_WRAP = "wrap"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_PID_TID = "pid_tid"
        private const val KEY_AUTO_SCROLL = "auto_scroll"
        private const val KEY_CAPTURE_CRASH = "capture_crash"
        private const val KEY_MCP_ENABLED = "mcp_enabled"
        private const val KEY_MCP_PORT = "mcp_port"
        private const val KEY_MCP_TOKEN = "mcp_token"
    }
}
