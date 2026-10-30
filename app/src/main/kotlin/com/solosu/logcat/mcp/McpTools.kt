package com.solosu.logcat.mcp

import com.solosu.logcat.LogCatApplication
import com.solosu.logcat.data.AppSettings
import com.solosu.logcat.log.LogBuffer
import com.solosu.logcat.log.LogEntry
import com.solosu.logcat.log.LogLevel
import com.solosu.logcat.log.rawLine
import com.solosu.logcat.shell.ShellMode
import com.solosu.logcat.shell.Shells
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把应用能力映射成 MCP 工具。所有读写都走 Application 单例里的同一份
 * LogRepository / CrashStore / settingsState,因此与 UI 完全同步。
 */
class McpTools(private val app: LogCatApplication) {

    /**
     * MCP 请求在 IO 线程处理;凡是会改动 UI 可见状态(日志列表、采集标志、设置)的操作,
     * 都切回主线程执行,避免与重组并发写快照。读操作仍在 IO 线程,保证大结果集不卡主线程。
     */
    private fun <T> onMain(block: () -> T): T = runBlocking(Dispatchers.Main) { block() }

    fun all(): List<ToolSpec> = listOf(
        queryTool(),
        statsTool(),
        tagsTool(),
        clearTool(),
        streamTool(),
        dumpTool(),
        processesTool(),
        crashListTool(),
        crashDetailTool(),
        crashClearTool(),
        settingsGetTool(),
        settingsSetTool(),
        appLogTool(),
        deviceInfoTool(),
    )

    // ---------- 日志 ----------

    private fun queryTool() = ToolSpec(
        name = "logcat_query",
        title = "查询日志",
        description = "按级别、tag、关键字、进程等条件从当前日志缓冲中检索日志。支持分页。",
        inputSchema = schema(
            mapOf(
                "level" to prop("string", "最小日志级别", listOf("V", "D", "I", "W", "E", "F"), "V"),
                "tag" to prop("string", "tag 包含匹配(区分大小写,子串)"),
                "keyword" to prop("string", "消息内容关键字(不区分大小写)"),
                "pid" to propInt("按进程 PID 过滤"),
                "limit" to propInt("返回条数上限", 200),
                "offset" to propInt("从末尾向前跳过的条数,用于分页", 0),
                "tail" to propBool("为 true 时返回最新的 N 条(默认 true)", true),
            ),
        ),
    ) { args ->
        val minLevel = LogLevel.entries.firstOrNull { it.letter.toString() == args.optString("level", "V") }
            ?: LogLevel.VERBOSE
        val tag = args.optStringOrNull("tag")
        val keyword = args.optStringOrNull("keyword")?.lowercase()
        val pid = args.optInt("pid", -1)
        val limit = args.optInt("limit", 200).coerceIn(1, 2000)
        val offset = args.optInt("offset", 0).coerceAtLeast(0)

        val matched = app.logRepository.snapshot().filter { entry ->
            entry.level.order >= minLevel.order &&
                (tag == null || entry.tag.contains(tag)) &&
                (keyword == null || entry.message.lowercase().contains(keyword)) &&
                (pid <= 0 || entry.pid == pid)
        }
        val sliced = if (args.optBoolean("tail", true)) {
            val end = (matched.size - offset).coerceAtLeast(0)
            val start = (end - limit).coerceAtLeast(0)
            matched.subList(start, end)
        } else {
            matched.drop(offset).take(limit)
        }
        textContent(
            JSONObject()
                .put("totalMatched", matched.size)
                .put("returned", sliced.size)
                .put("bufferSize", app.logRepository.entries.size)
                .put("mode", app.logRepository.activeMode.id)
                .put("logs", JSONArray(sliced.map { it.toJson() }))
        )
    }

    private fun statsTool() = ToolSpec(
        name = "logcat_stats",
        title = "日志统计",
        description = "统计当前缓冲内各级别数量、来源进程数、采集状态。",
        inputSchema = schema(emptyMap()),
    ) {
        val entries = app.logRepository.snapshot()
        val byLevel = JSONObject()
        LogLevel.entries.forEach { level ->
            byLevel.put(level.letter.toString(), entries.count { it.level == level })
        }
        val pids = entries.map { it.pid }.distinct()
        textContent(
            JSONObject()
                .put("buffered", entries.size)
                .put("byLevel", byLevel)
                .put("distinctPids", pids.size)
                .put("running", app.logRepository.running)
                .put("mode", app.logRepository.activeMode.id)
                .put("buffer", app.logRepository.activeBuffer.id)
                .put("dropped", app.logRepository.droppedLines)
                .put("lastError", app.logRepository.lastError ?: JSONObject.NULL)
        )
    }

    private fun tagsTool() = ToolSpec(
        name = "logcat_tags",
        title = "Tag 排行",
        description = "统计出现次数最多的 tag,便于快速定位噪声来源。",
        inputSchema = schema(mapOf("limit" to propInt("返回数量", 30))),
    ) { args ->
        val limit = args.optInt("limit", 30).coerceIn(1, 500)
        val counts = app.logRepository.snapshot()
            .groupingBy { it.tag }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
        val array = JSONArray()
        counts.forEach { (tag, count) -> array.put(JSONObject().put("tag", tag).put("count", count)) }
        textContent(JSONObject().put("tags", array))
    }

    private fun clearTool() = ToolSpec(
        name = "logcat_clear",
        title = "清空日志",
        description = "清空本地缓冲;clearDevice 为 true 时同时清除设备 logcat 缓冲区(需要 Root 或 Shizuku)。",
        inputSchema = schema(mapOf("clearDevice" to propBool("是否同时清除设备缓冲区", false))),
    ) { args ->
        onMain { app.logRepository.clearLocal() }
        if (args.optBoolean("clearDevice", false)) {
            val mode = app.settingsState.value.mode
            if (mode == ShellMode.DEFAULT) {
                return@ToolSpec textContent(
                    JSONObject().put("ok", true).put("local", true).put("device", false)
                        .put("note", "默认模式无权限清除系统缓冲区")
                )
            }
            Shells.execOnce(mode, com.solosu.logcat.log.LogcatCommand.clear(LogBuffer.ALL))
            textContent(JSONObject().put("ok", true).put("local", true).put("device", true))
        } else {
            textContent(JSONObject().put("ok", true).put("local", true).put("device", false))
        }
    }

    private fun streamTool() = ToolSpec(
        name = "logcat_stream",
        title = "采集控制",
        description = "启动、停止日志采集,或切换缓冲区。action: start | stop | status。",
        inputSchema = schema(
            mapOf(
                "action" to prop("string", "操作类型", listOf("start", "stop", "status"), "status"),
                "buffer" to prop("string", "目标缓冲区", listOf("all", "main", "system", "crash")),
                "mode" to prop("string", "读取模式", listOf("default", "shizuku", "root")),
            ),
            required = listOf("action"),
        ),
    ) { args ->
        val settings = app.settingsState.value
        when (args.optString("action", "status")) {
            "start" -> {
                val buffer = args.optStringOrNull("buffer")?.let { LogBuffer.fromId(it) } ?: settings.buffer
                val mode = args.optStringOrNull("mode")?.let { ShellMode.fromId(it) } ?: settings.mode
                val (effective, downgraded) = app.modeAvailability().fallback(mode)
                onMain { app.logRepository.start(effective, buffer) }
                textContent(
                    JSONObject().put("ok", true).put("mode", effective.id)
                        .put("buffer", buffer.id)
                        .put("downgraded", downgraded)
                )
            }
            "stop" -> {
                onMain { app.logRepository.stop() }
                textContent(JSONObject().put("ok", true).put("running", false))
            }
            else -> textContent(
                JSONObject()
                    .put("running", app.logRepository.running)
                    .put("mode", app.logRepository.activeMode.id)
                    .put("buffer", app.logRepository.activeBuffer.id)
                    .put("buffered", app.logRepository.entries.size)
            )
        }
    }

    private fun dumpTool() = ToolSpec(
        name = "logcat_dump",
        title = "导出设备缓冲区",
        description = "一次性读取设备 logcat 缓冲区(非本地缓存),需要 Root 或 Shizuku 才能拿到全部进程。",
        inputSchema = schema(
            mapOf(
                "lines" to propInt("拉取行数", 500),
                "level" to prop("string", "最小级别", listOf("V", "D", "I", "W", "E", "F"), "V"),
                "buffer" to prop("string", "缓冲区", listOf("all", "main", "system", "crash")),
            )
        ),
    ) { args ->
        val lines = args.optInt("lines", 500).coerceIn(1, 5000)
        val level = LogLevel.entries.firstOrNull { it.letter.toString() == args.optString("level", "V") }
            ?: LogLevel.VERBOSE
        val buffer = LogBuffer.fromId(args.optStringOrNull("buffer"))
        val mode = activeModeOrDefault()
        val command = com.solosu.logcat.log.LogcatCommand.dump(buffer, lines, level)
        val text = runCatching { Shells.execOnce(mode, command) }.getOrElse { "" }
        val json = JSONArray()
        // Sequence 没有 takeLast,先收集成 List 再取末尾
        text.lineSequence().filter { it.isNotBlank() }.toList().takeLast(lines).forEach { json.put(it) }
        textContent(
            JSONObject()
                .put("mode", mode.id)
                .put("buffer", buffer.id)
                .put("count", json.length())
                .put("lines", json)
        )
    }

    private fun processesTool() = ToolSpec(
        name = "logcat_processes",
        title = "进程列表",
        description = "列出设备进程(PID + 名称),便于结合 pid 过滤日志。需要 Root 或 Shizuku。",
        inputSchema = schema(emptyMap()),
    ) {
        val mode = activeModeOrDefault()
        if (mode == ShellMode.DEFAULT) {
            return@ToolSpec textContent(
                JSONObject().put("mode", mode.id).put("processes", JSONArray())
                    .put("note", "默认模式无法枚举其他进程")
            )
        }
        val raw = runCatching { Shells.execOnce(mode, listOf("ps", "-A", "-o", "PID,NAME")) }.getOrElse { "" }
        val array = JSONArray()
        raw.lineSequence().drop(1).forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size == 2 && parts[0].toIntOrNull() != null) {
                array.put(JSONObject().put("pid", parts[0].toInt()).put("name", parts[1]))
            }
        }
        textContent(JSONObject().put("mode", mode.id).put("count", array.length()).put("processes", array))
    }

    // ---------- 崩溃 ----------

    private fun crashListTool() = ToolSpec(
        name = "crash_list",
        title = "崩溃列表",
        description = "列出已记录的崩溃(应用未捕获异常 + 系统 FATAL 日志)。",
        inputSchema = schema(mapOf("limit" to propInt("返回条数", 50))),
    ) { args ->
        val limit = args.optInt("limit", 50).coerceIn(1, 200)
        val records = app.crashStore.records.value.take(limit)
        textContent(
            JSONObject()
                .put("total", app.crashStore.records.value.size)
                .put("items", JSONArray(records.map { record ->
                    JSONObject()
                        .put("id", record.id)
                        .put("time", record.time)
                        .put("title", record.title)
                        .put("module", record.module)
                        .put("fromSystem", record.fromSystem)
                        .put("topFrame", record.topFrameClass ?: JSONObject.NULL)
                }))
        )
    }

    private fun crashDetailTool() = ToolSpec(
        name = "crash_detail",
        title = "崩溃详情",
        description = "按 id 获取完整堆栈。",
        inputSchema = schema(mapOf("id" to prop("string", "崩溃记录 id")), required = listOf("id")),
    ) { args ->
        val id = args.requireString("id")
        val record = app.crashStore.find(id)
            ?: throw McpException(RpcError.INVALID_PARAMS, "未找到崩溃记录 $id")
        textContent(
            JSONObject()
                .put("id", record.id)
                .put("time", record.time)
                .put("thread", record.thread)
                .put("exception", record.exception)
                .put("module", record.module)
                .put("fromSystem", record.fromSystem)
                .put("stackTrace", record.stackTrace)
        )
    }

    private fun crashClearTool() = ToolSpec(
        name = "crash_clear",
        title = "清理崩溃",
        description = "清空全部崩溃记录,或按 id 删除单条。",
        inputSchema = schema(mapOf("id" to prop("string", "仅删除该 id,留空则清空全部"))),
    ) { args ->
        val id = args.optStringOrNull("id")
        if (id != null) {
            onMain { app.crashStore.remove(id) }
            textContent(JSONObject().put("ok", true).put("removed", id))
        } else {
            onMain { app.crashStore.clear() }
            textContent(JSONObject().put("ok", true).put("cleared", true))
        }
    }

    // ---------- 设置 ----------

    private fun settingsGetTool() = ToolSpec(
        name = "settings_get",
        title = "读取设置",
        description = "获取当前所有设置项。",
        inputSchema = schema(emptyMap()),
    ) {
        textContent(app.settingsState.value.toJson())
    }

    private fun settingsSetTool() = ToolSpec(
        name = "settings_set",
        title = "修改设置",
        description = "修改设置项,只传需要改变的字段。切换 mode 或 buffer 会自动重启采集。",
        inputSchema = schema(
            mapOf(
                "mode" to prop("string", "读取模式", listOf("default", "shizuku", "root")),
                "buffer" to prop("string", "缓冲区", listOf("all", "main", "system", "crash")),
                "level" to prop("string", "最小级别", listOf("V", "D", "I", "W", "E", "F")),
                "maxLines" to propInt("缓冲行数上限"),
                "keyword" to prop("string", "关键字过滤"),
                "wrapLines" to propBool("自动换行"),
                "showTimestamp" to propBool("显示时间戳"),
                "showPidTid" to propBool("显示 PID/TID"),
                "autoScroll" to propBool("自动滚动"),
                "captureCrashes" to propBool("记录崩溃"),
                "themeMode" to prop("string", "主题模式", listOf("system", "light", "dark")),
                "monetEnabled" to propBool("莫奈取色"),
                "paletteId" to prop("string", "预设色板", com.solosu.logcat.ui.theme.AppPalette.entries.map { it.id }),
                "logFontSize" to propInt("日志字号 sp"),
            )
        ),
    ) { args ->
        val next = onMain {
            val previous = app.updateSettings { current -> current.applyJson(args) }
            val updated = app.settingsState.value
            if (updated.mode != previous.mode || updated.buffer != previous.buffer) {
                app.logRepository.restartIfRunning(updated.mode, updated.buffer)
            }
            if (updated.maxLines != previous.maxLines) app.logRepository.reapplyLimit(updated.maxLines)
            updated
        }
        textContent(next.toJson())
    }

    // ---------- 诊断 ----------

    private fun appLogTool() = ToolSpec(
        name = "app_log",
        title = "工具自身日志",
        description = "LogCat 自身的运行日志,用于排查采集失败原因。",
        inputSchema = schema(mapOf("limit" to propInt("返回条数", 100))),
    ) { args ->
        val limit = args.optInt("limit", 100).coerceIn(1, 500)
        val array = JSONArray()
        com.solosu.logcat.data.AppLog.lines.value.takeLast(limit).forEach { line ->
            array.put(JSONObject().put("time", line.time).put("level", line.level.toString()).put("text", line.text))
        }
        textContent(JSONObject().put("lines", array))
    }

    private fun deviceInfoTool() = ToolSpec(
        name = "device_info",
        title = "设备信息",
        description = "设备型号、系统版本、ABI、权限可用性等。",
        inputSchema = schema(emptyMap()),
    ) {
        val availability = app.modeAvailability()
        textContent(
            JSONObject()
                .put("packageName", app.packageName)
                .put("model", android.os.Build.MODEL)
                .put("brand", android.os.Build.BRAND)
                .put("release", android.os.Build.VERSION.RELEASE)
                .put("sdkInt", android.os.Build.VERSION.SDK_INT)
                .put("abis", JSONArray(android.os.Build.SUPPORTED_ABIS.toList()))
                .put("is64Bit", android.os.Process.is64Bit())
                .put("rootAvailable", availability.root)
                .put("shizukuInstalled", availability.shizukuInstalled)
                .put("shizukuUsable", availability.shizukuUsable)
        )
    }

    private fun activeModeOrDefault(): ShellMode {
        val configured = app.settingsState.value.mode
        return app.modeAvailability().fallback(configured).first
    }

    private fun LogEntry.toJson(): JSONObject = JSONObject()
        .put("seq", seq)
        .put("time", clock)
        .put("pid", pid)
        .put("tid", tid)
        .put("level", level.letter.toString())
        .put("tag", tag)
        .put("message", message)
        .put("raw", rawLine())

    private fun AppSettings.toJson(): JSONObject = JSONObject()
        .put("mode", mode.id)
        .put("buffer", buffer.id)
        .put("level", level.letter.toString())
        .put("maxLines", maxLines)
        .put("keyword", keyword)
        .put("wrapLines", wrapLines)
        .put("showTimestamp", showTimestamp)
        .put("showPidTid", showPidTid)
        .put("autoScroll", autoScroll)
        .put("captureCrashes", captureCrashes)
        .put("themeMode", themeMode.id)
        .put("monetEnabled", monetEnabled)
        .put("paletteId", paletteId)
        .put("logFontSize", logFontSize)

    private fun AppSettings.applyJson(json: JSONObject): AppSettings {
        var result = this
        json.optStringOrNull("mode")?.let { result = result.copy(mode = ShellMode.fromId(it)) }
        json.optStringOrNull("buffer")?.let { result = result.copy(buffer = LogBuffer.fromId(it)) }
        json.optStringOrNull("level")?.let { value ->
            LogLevel.entries.firstOrNull { it.letter.toString() == value }?.let { result = result.copy(level = it) }
        }
        if (json.has("maxLines")) result = result.copy(maxLines = json.optInt("maxLines").coerceIn(500, 20_000))
        json.optStringOrNull("keyword")?.let { result = result.copy(keyword = it) }
        if (json.has("wrapLines")) result = result.copy(wrapLines = json.optBoolean("wrapLines"))
        if (json.has("showTimestamp")) result = result.copy(showTimestamp = json.optBoolean("showTimestamp"))
        if (json.has("showPidTid")) result = result.copy(showPidTid = json.optBoolean("showPidTid"))
        if (json.has("autoScroll")) result = result.copy(autoScroll = json.optBoolean("autoScroll"))
        if (json.has("captureCrashes")) result = result.copy(captureCrashes = json.optBoolean("captureCrashes"))
        json.optStringOrNull("themeMode")?.let {
            result = result.copy(themeMode = com.solosu.logcat.data.ThemeMode.fromId(it))
        }
        if (json.has("monetEnabled")) result = result.copy(monetEnabled = json.optBoolean("monetEnabled"))
        json.optStringOrNull("paletteId")?.let { result = result.copy(paletteId = it) }
        if (json.has("logFontSize")) result = result.copy(logFontSize = json.optInt("logFontSize").coerceIn(10, 20))
        return result
    }
}
