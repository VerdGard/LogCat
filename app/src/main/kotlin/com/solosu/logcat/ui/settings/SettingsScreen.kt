@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.solosu.logcat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solosu.logcat.data.SettingsRepository
import com.solosu.logcat.data.ThemeMode
import com.solosu.logcat.log.LogBuffer
import com.solosu.logcat.log.LogLevel
import com.solosu.logcat.shell.ShellMode
import com.solosu.logcat.ui.BuildInfo
import com.solosu.logcat.ui.MainViewModel
import com.solosu.logcat.ui.components.GroupLabel
import com.solosu.logcat.ui.components.SectionCard
import com.solosu.logcat.ui.components.SettingRow
import com.solosu.logcat.ui.components.SwitchRow
import com.solosu.logcat.ui.components.Tag
import com.solosu.logcat.ui.components.rememberTextClipboard
import com.solosu.logcat.ui.icons.AppIcons
import com.solosu.logcat.ui.theme.AppPalette
import kotlin.math.roundToInt

/** 分区标识,只用于 rememberSaveable 保存展开状态。 */
private enum class Section(val key: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    APPEARANCE("appearance", "外观与主题", AppIcons.Theme),
    CAPTURE("capture", "日志采集", AppIcons.Logs),
    DISPLAY("display", "日志显示", AppIcons.Search),
    CRASH("crash", "崩溃记录", AppIcons.Crash),
    MCP("mcp", "MCP 服务", AppIcons.Link),
    APP_LOG("applog", "工具日志", AppIcons.Info),
    ABOUT("about", "关于", AppIcons.Settings),
}

@Composable
fun SettingsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    val mcpStatus by viewModel.mcpStatus.collectAsStateWithLifecycle()
    val appLogs by viewModel.appLogLines.collectAsStateWithLifecycle()
    val copyText = rememberTextClipboard()

    // 展开状态:默认只展开「外观与主题」
    val expanded = remember { mutableStateListOf(Section.APPEARANCE) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun toggle(section: Section) {
        if (!expanded.remove(section)) expanded.add(section)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("设置", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${settings.mode.label} · ${settings.buffer.label} · ${settings.maxLines} 行",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ---------- 外观 ----------
            item {
                SectionCard(
                    title = Section.APPEARANCE.title,
                    subtitle = "莫奈取色 · 昼夜跟随 · 预设色板",
                    icon = Section.APPEARANCE.icon,
                    expanded = Section.APPEARANCE in expanded,
                    onToggle = { toggle(Section.APPEARANCE) },
                    trailing = { Tag(settings.themeMode.label) },
                ) {
                    GroupLabel("主题模式")
                    ChoiceRow(
                        options = ThemeMode.entries.map { it.id to it.label },
                        selected = settings.themeMode.id,
                        onSelect = { id -> viewModel.setThemeMode(ThemeMode.fromId(id)) },
                    )

                    SwitchRow(
                        title = "莫奈取色",
                        supporting = if (android.os.Build.VERSION.SDK_INT >= 31) {
                            "跟随系统壁纸生成配色"
                        } else {
                            "需要 Android 12 及以上,当前系统不支持"
                        },
                        checked = settings.monetEnabled,
                        onCheckedChange = viewModel::setMonetEnabled,
                        enabled = android.os.Build.VERSION.SDK_INT >= 31,
                    )

                    GroupLabel("预设色板")
                    if (!settings.monetEnabled || android.os.Build.VERSION.SDK_INT < 31) {
                        PaletteRow(
                            selected = settings.paletteId,
                            onSelect = viewModel::setPalette,
                        )
                    } else {
                        SettingRow(
                            title = "已被莫奈取色覆盖",
                            supporting = "关闭莫奈取色后可选择预设色板",
                        )
                    }

                    GroupLabel("日志字号")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Slider(
                            value = settings.logFontSize.toFloat(),
                            onValueChange = { viewModel.setLogFontSize(it.roundToInt()) },
                            valueRange = SettingsRepository.MIN_FONT.toFloat()..SettingsRepository.MAX_FONT.toFloat(),
                            steps = SettingsRepository.MAX_FONT - SettingsRepository.MIN_FONT - 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${settings.logFontSize}sp",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            // ---------- 采集 ----------
            item {
                SectionCard(
                    title = Section.CAPTURE.title,
                    subtitle = "权限模式 · 缓冲区 · 缓冲行数",
                    icon = Section.CAPTURE.icon,
                    expanded = Section.CAPTURE in expanded,
                    onToggle = { toggle(Section.CAPTURE) },
                    trailing = { Tag(settings.mode.label) },
                ) {
                    GroupLabel("读取模式")
                    ShellMode.entries.forEach { mode ->
                        ModeOption(
                            mode = mode,
                            selected = settings.mode == mode,
                            usable = availability.isAvailable(mode),
                            statusText = when (mode) {
                                ShellMode.DEFAULT -> "无需授权,仅本应用日志"
                                ShellMode.ROOT -> if (availability.root) "已检测到 su" else "未检测到 su"
                                ShellMode.SHIZUKU -> when {
                                    !availability.shizukuInstalled -> "未安装 Shizuku"
                                    availability.shizukuUsable -> "已授权"
                                    else -> "未授权 / 服务未运行"
                                }
                            },
                            onSelect = { viewModel.setMode(mode) },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { viewModel.requestShizukuPermission() }) { Text("申请授权") }
                        TextButton(onClick = { viewModel.refreshModes() }) { Text("刷新状态") }
                    }

                    GroupLabel("缓冲区")
                    ChoiceRow(
                        options = LogBuffer.entries.map { it.id to it.label },
                        selected = settings.buffer.id,
                        onSelect = { id -> viewModel.setBuffer(LogBuffer.fromId(id)) },
                    )

                    GroupLabel("缓冲行数上限")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Slider(
                            value = settings.maxLines.toFloat(),
                            onValueChange = { viewModel.setMaxLines(it.roundToInt()) },
                            valueRange = SettingsRepository.MIN_LINES.toFloat()..SettingsRepository.MAX_LINES.toFloat(),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${settings.maxLines}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(56.dp),
                        )
                    }
                    Text(
                        text = "超出上限后丢弃最早的日志,行数越大内存占用越高",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }

            // ---------- 显示 ----------
            item {
                SectionCard(
                    title = Section.DISPLAY.title,
                    subtitle = "级别、换行、时间戳、滚动",
                    icon = Section.DISPLAY.icon,
                    expanded = Section.DISPLAY in expanded,
                    onToggle = { toggle(Section.DISPLAY) },
                    trailing = { Tag(settings.level.label) },
                ) {
                    GroupLabel("最小级别")
                    ChoiceRow(
                        options = LogLevel.entries
                            .filterNot { it == LogLevel.ASSERT }
                            .map { it.letter.toString() to it.label },
                        selected = settings.level.letter.toString(),
                        onSelect = { letter ->
                            LogLevel.entries.firstOrNull { it.letter.toString() == letter }
                                ?.let(viewModel::setLevel)
                        },
                    )
                    SwitchRow(
                        title = "自动换行",
                        supporting = "关闭后每条日志只显示一行",
                        checked = settings.wrapLines,
                        onCheckedChange = viewModel::setWrapLines,
                    )
                    SwitchRow(
                        title = "显示时间戳",
                        checked = settings.showTimestamp,
                        onCheckedChange = viewModel::setShowTimestamp,
                    )
                    SwitchRow(
                        title = "显示 PID / TID",
                        checked = settings.showPidTid,
                        onCheckedChange = viewModel::setShowPidTid,
                    )
                    SwitchRow(
                        title = "自动滚动到最新",
                        supporting = "手动上滑后仍会跟随,可在日志页临时关闭",
                        checked = settings.autoScroll,
                        onCheckedChange = viewModel::setAutoScroll,
                    )
                    SettingRow(
                        title = "当前关键字",
                        supporting = if (settings.keyword.isBlank()) "未设置" else settings.keyword,
                        trailing = {
                            if (settings.keyword.isNotBlank()) {
                                TextButton(onClick = { viewModel.setKeyword("") }) { Text("清除") }
                            }
                        },
                    )
                }
            }

            // ---------- 崩溃 ----------
            item {
                SectionCard(
                    title = Section.CRASH.title,
                    subtitle = "自动记录 FATAL 与未捕获异常",
                    icon = Section.CRASH.icon,
                    expanded = Section.CRASH in expanded,
                    onToggle = { toggle(Section.CRASH) },
                ) {
                    SwitchRow(
                        title = "记录系统 FATAL 日志",
                        supporting = "从 logcat 中提取 FATAL EXCEPTION 段落,需要 Root 或 Shizuku 才能覆盖全部进程",
                        checked = settings.captureCrashes,
                        onCheckedChange = viewModel::setCaptureCrashes,
                    )
                    SettingRow(
                        title = "清空全部崩溃记录",
                        supporting = "删除本地保存的崩溃与堆栈",
                        onClick = { viewModel.clearCrashes() },
                        trailing = { Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            // ---------- MCP ----------
            item {
                SectionCard(
                    title = Section.MCP.title,
                    subtitle = if (mcpStatus.running) "运行中 ${mcpStatus.endpoint}" else "未启动",
                    icon = Section.MCP.icon,
                    expanded = Section.MCP in expanded,
                    onToggle = { toggle(Section.MCP) },
                    trailing = { Tag(if (mcpStatus.running) "运行中" else "已停止") },
                ) {
                    SwitchRow(
                        title = "启用 MCP 服务",
                        supporting = "在本机 127.0.0.1 上开放 JSON-RPC 接口,供 AI 客户端读取日志、崩溃与设置",
                        checked = settings.mcpEnabled,
                        onCheckedChange = viewModel::setMcpEnabled,
                    )

                    SettingRow(
                        title = "服务地址",
                        supporting = mcpStatus.endpoint,
                        trailing = {
                            TextButton(
                                onClick = {
                                    copyText(mcpStatus.endpoint)
                                    viewModel.postMessage("地址已复制")
                                },
                                enabled = mcpStatus.running,
                            ) { Text("复制") }
                        },
                    )

                    GroupLabel("端口")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = settings.mcpPort.toString(),
                            onValueChange = { text ->
                                text.toIntOrNull()?.let(viewModel::setMcpPort)
                            },
                            modifier = Modifier.width(120.dp),
                            singleLine = true,
                            label = { Text("端口") },
                        )
                        TextButton(onClick = { viewModel.setMcpPort(SettingsRepository.DEFAULT_MCP_PORT) }) {
                            Text("默认 ${SettingsRepository.DEFAULT_MCP_PORT}")
                        }
                    }

                    GroupLabel("访问令牌(可选)")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = settings.mcpToken,
                            onValueChange = viewModel::setMcpToken,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("留空表示不校验") },
                            label = { Text("Bearer Token") },
                        )
                        TextButton(onClick = { viewModel.regenerateMcpToken() }) { Text("生成") }
                    }

                    mcpStatus.lastError?.let { error ->
                        Text(
                            text = "启动失败:$error",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = { viewModel.toggleMcp() }) {
                            Text(if (mcpStatus.running) "停止服务" else "启动服务")
                        }
                        TextButton(onClick = { showMcpHelp(viewModel) }) { Text("用法示例") }
                    }

                    Text(
                        text = "接口:POST /  JSON-RPC 2.0,支持 initialize、tools/list、tools/call;" +
                            "工具覆盖日志查询、统计、tag 排行、清空、采集控制、缓冲区导出、进程列表、崩溃读写、设置读写、设备信息。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // ---------- 工具日志 ----------
            item {
                SectionCard(
                    title = Section.APP_LOG.title,
                    subtitle = "LogCat 自身动作记录,排查采集失败时用",
                    icon = Section.APP_LOG.icon,
                    expanded = Section.APP_LOG in expanded,
                    onToggle = { toggle(Section.APP_LOG) },
                    trailing = { Tag("${appLogs.size}") },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = {
                            copyText(appLogs.joinToString("\n") { "${it.time} ${it.level} ${it.text}" })
                            viewModel.postMessage("工具日志已复制")
                        }) { Text("复制") }
                        TextButton(onClick = { viewModel.clearAppLogs() }) { Text("清空") }
                    }
                    if (appLogs.isEmpty()) {
                        Text(
                            text = "暂无记录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    } else {
                        appLogs.takeLast(40).reversed().forEach { line ->
                            Text(
                                text = "${line.time}  ${line.level}  ${line.text}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }

            // ---------- 关于 ----------
            item {
                SectionCard(
                    title = Section.ABOUT.title,
                    subtitle = "版本、ABI、设备信息",
                    icon = Section.ABOUT.icon,
                    expanded = Section.ABOUT in expanded,
                    onToggle = { toggle(Section.ABOUT) },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AboutLine("应用", "LogCat")
                        AboutLine("包名", "com.solosu.logcat")
                        AboutLine("版本", "1.1.0")
                        AboutLine("ABI", BuildInfo.abis)
                        AboutLine("64 位进程", if (BuildInfo.is64Bit) "是" else "否")
                        AboutLine("系统", "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                        AboutLine("设备", "${android.os.Build.BRAND} ${android.os.Build.MODEL}")
                        Text(
                            text = "默认模式仅能读取本应用自身日志;Root 与 Shizuku 模式可读取整机日志。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun showMcpHelp(viewModel: MainViewModel) {
    viewModel.postMessage(
        "示例:tools/list 后调用 logcat_query {\"level\":\"E\",\"limit\":50};" +
            "crash_list;settings_set {\"mode\":\"root\"}"
    )
}

/** 横向可选项(Chip 风格)。 */
@Composable
private fun ChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (id, label) ->
            val isSelected = id == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickableNoRipple { onSelect(id) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 色板选择:圆形色块。 */
@Composable
private fun PaletteRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppPalette.entries.forEach { palette ->
            val isSelected = palette.id == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(palette.seed)
                        .clickableNoRipple { onSelect(palette.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    text = palette.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ModeOption(
    mode: ShellMode,
    selected: Boolean,
    usable: Boolean,
    statusText: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick = onSelect)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(mode.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (!usable) Tag("不可用", MaterialTheme.colorScheme.error)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(text = value, style = MaterialTheme.typography.labelSmall)
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}
