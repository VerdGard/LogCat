@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.solosu.logcat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solosu.logcat.data.SettingsRepository
import com.solosu.logcat.data.ThemeMode
import com.solosu.logcat.log.LogBuffer
import com.solosu.logcat.log.LogLevel
import com.solosu.logcat.shell.ShellMode
import com.solosu.logcat.ui.BuildInfo
import com.solosu.logcat.ui.MainViewModel
import com.solosu.logcat.ui.components.SettingRow
import com.solosu.logcat.ui.components.SwitchRow
import com.solosu.logcat.ui.components.Tag
import com.solosu.logcat.ui.components.rememberTextClipboard
import com.solosu.logcat.ui.icons.AppIcons
import com.solosu.logcat.ui.theme.AppPalette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.solosu.logcat.ui.components.PressableBox
import androidx.compose.animation.animateColorAsState
import com.solosu.logcat.ui.components.animatedSelectionColor
import com.solosu.logcat.ui.components.pressableClick
import com.solosu.logcat.ui.theme.Motion
import com.solosu.logcat.ui.theme.reduceMotion
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    val mcpStatus by viewModel.mcpStatus.collectAsStateWithLifecycle()
    val appLogs by viewModel.appLogLines.collectAsStateWithLifecycle()
    val copyText = rememberTextClipboard()

    var confirmClearCrashes by remember { mutableStateOf(false) }

    if (confirmClearCrashes) {
        AlertDialog(
            onDismissRequest = { confirmClearCrashes = false },
            title = { Text("清空崩溃记录?") },
            text = { Text("将删除本机保存的全部崩溃与堆栈,该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCrashes()
                    confirmClearCrashes = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCrashes = false }) { Text("取消") }
            },
        )
    }

    /*
     * 顶部快速跳转。
     *
     * 设置页共 7 个分组、滚动长度约 4 屏,逐屏找某一项的成本不低。
     * 这里在顶栏下挂一条 chip,点哪个就平滑滚到对应分组的 stickyHeader。
     *
     * 索引来源分两级:
     * 1. 首选 layoutInfo 反查 —— 分组 header 用 settingsItemKey(section) 作 key,
     *    若它当前可见,Compose 已实测出真实索引,直接拿来用,精确且不受改动影响;
     * 2. 回落 sectionStarts —— 按枚举顺序推算的起始索引。这条路径假设
     *    "每个分组固定占 1 个 header + 1 个卡片 item",若在某个分组内增删 item
     *    就会失准(跳偏,但不会崩)。
     *
     * 之所以保留回落而不是只靠 key:目标分组很远时不可能是可见项,
     * 而 Compose 没有「按 key 查索引」的公开 API,只能靠推算 + animateScrollToItem。
     */
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 每个分组的 header 在 LazyColumn 中的起始索引:
    // 每组的固定开销 = 1 个 stickyHeader + 1 个卡片 item;
    // 工具日志组在卡片之后还有最多 40 行日志。
    val sectionStarts = remember(appLogs.size) {
        var index = 0
        SettingsSection.entries.associateWith { section ->
            val start = index
            index += 2 // stickyHeader + card
            if (section == SettingsSection.APP_LOG) index += minOf(appLogs.size, 40)
            start
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
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
                )

                SectionJumpBar(
                    onJump = { section ->
                        val key = settingsItemKey(section)
                        // 先看目标是否正好在可见范围内:在的话直接用它已知的索引,
                        // 比按偏移推算更准(可见项的 index 由 Compose 实测得出)
                        val visibleIndex = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == key }
                            ?.index
                        val target = visibleIndex ?: sectionStarts[section]
                        if (target != null) {
                            scope.launch { listState.animateScrollToItem(target) }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ---------- 外观 ----------
            stickyHeader(key = settingsItemKey(SettingsSection.APPEARANCE)) {
                GroupHeader("外观", "主题模式、色板、日志字号")
            }
            item(key = "appearance") {
                SettingsCard {
                    ChoiceBlock("主题模式") {
                        ThemeMode.entries.forEach { mode ->
                            ChoiceChip(
                                text = mode.label,
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                            )
                        }
                    }

                    RowDivider()

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

                    RowDivider()

                    val monetActive = settings.monetEnabled && android.os.Build.VERSION.SDK_INT >= 31
                    ChoiceBlock(
                        label = "预设色板",
                        note = if (monetActive) "已被莫奈取色覆盖" else null,
                    ) {
                        AppPalette.entries.forEach { palette ->
                            PaletteDot(
                                palette = palette,
                                selected = settings.paletteId == palette.id,
                                enabled = !monetActive,
                                onClick = { viewModel.setPalette(palette.id) },
                            )
                        }
                    }

                    RowDivider()

                    SliderBlock(
                        label = "日志字号",
                        valueText = "${settings.logFontSize}sp",
                        value = settings.logFontSize.toFloat(),
                        range = SettingsRepository.MIN_FONT.toFloat()..SettingsRepository.MAX_FONT.toFloat(),
                        steps = SettingsRepository.MAX_FONT - SettingsRepository.MIN_FONT - 1,
                        onValueChange = { viewModel.setLogFontSize(it.roundToInt()) },
                    )
                }
            }

            // ---------- 采集 ----------
            stickyHeader(key = settingsItemKey(SettingsSection.CAPTURE)) {
                GroupHeader("采集", "权限模式、缓冲区、缓冲行数")
            }
            item(key = "capture") {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
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
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { viewModel.requestShizukuPermission() }) { Text("申请授权") }
                        TextButton(onClick = { viewModel.refreshModes() }) { Text("刷新状态") }
                    }

                    RowDivider()

                    ChoiceBlock("缓冲区") {
                        LogBuffer.entries.forEach { buffer ->
                            ChoiceChip(
                                text = buffer.label,
                                selected = settings.buffer == buffer,
                                onClick = { viewModel.setBuffer(buffer) },
                            )
                        }
                    }

                    RowDivider()

                    SliderBlock(
                        label = "缓冲行数上限",
                        valueText = "${settings.maxLines}",
                        value = settings.maxLines.toFloat(),
                        range = SettingsRepository.MIN_LINES.toFloat()..SettingsRepository.MAX_LINES.toFloat(),
                        steps = 0,
                        onValueChange = { viewModel.setMaxLines(it.roundToInt()) },
                        note = "超出上限后丢弃最早的日志,行数越大内存占用越高",
                    )
                }
            }

            // ---------- 显示 ----------
            stickyHeader(key = settingsItemKey(SettingsSection.DISPLAY)) {
                GroupHeader("显示", "级别、行密度、时间戳、滚动")
            }
            item(key = "display") {
                SettingsCard {
                    ChoiceBlock("最小级别") {
                        LogLevel.entries
                            .filterNot { it == LogLevel.ASSERT }
                            .forEach { level ->
                                ChoiceChip(
                                    text = level.label,
                                    selected = settings.level == level,
                                    onClick = { viewModel.setLevel(level) },
                                )
                            }
                    }

                    RowDivider()

                    ChoiceBlock("行密度") {
                        ChoiceChip(
                            text = "紧凑",
                            selected = settings.compactRows,
                            onClick = { viewModel.setCompactRows(true) },
                        )
                        ChoiceChip(
                            text = "舒适",
                            selected = !settings.compactRows,
                            onClick = { viewModel.setCompactRows(false) },
                        )
                    }

                    RowDivider()

                    SwitchRow(
                        title = "自动换行",
                        supporting = "关闭后每条日志只显示一行,横向滚动查看",
                        checked = settings.wrapLines,
                        onCheckedChange = viewModel::setWrapLines,
                    )
                    RowDivider()
                    SwitchRow(
                        title = "显示时间戳",
                        checked = settings.showTimestamp,
                        onCheckedChange = viewModel::setShowTimestamp,
                    )
                    RowDivider()
                    SwitchRow(
                        title = "显示 PID",
                        supporting = "在 tag 左侧显示来源进程号",
                        checked = settings.showPidTid,
                        onCheckedChange = viewModel::setShowPidTid,
                    )
                    RowDivider()
                    SwitchRow(
                        title = "自动滚动到最新",
                        supporting = "关闭后新日志不会打断当前阅读位置",
                        checked = settings.autoScroll,
                        onCheckedChange = viewModel::setAutoScroll,
                    )

                    RowDivider()

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
            stickyHeader(key = settingsItemKey(SettingsSection.CRASH)) {
                GroupHeader("崩溃", "自动记录 FATAL 与未捕获异常")
            }
            item(key = "crash") {
                SettingsCard {
                    SwitchRow(
                        title = "记录系统 FATAL 日志",
                        supporting = "从 logcat 提取 FATAL EXCEPTION 段落,需 Root 或 Shizuku 覆盖全部进程",
                        checked = settings.captureCrashes,
                        onCheckedChange = viewModel::setCaptureCrashes,
                    )
                    RowDivider()
                    SettingRow(
                        title = "清空全部崩溃记录",
                        supporting = "删除本地保存的崩溃与堆栈",
                        onClick = { confirmClearCrashes = true },
                        trailing = {
                            Icon(
                                AppIcons.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }

            // ---------- MCP ----------
            stickyHeader(key = settingsItemKey(SettingsSection.MCP)) {
                GroupHeader("MCP 服务", if (mcpStatus.running) mcpStatus.endpoint else "未启动")
            }
            item(key = "mcp") {
                SettingsCard {
                    SwitchRow(
                        title = "启用 MCP 服务",
                        supporting = "在本机 127.0.0.1 开放 JSON-RPC 接口,供 AI 客户端读取日志与设置",
                        checked = settings.mcpEnabled,
                        onCheckedChange = viewModel::setMcpEnabled,
                    )
                    RowDivider()
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

                    RowDivider()

                    // 端口输入用本地草稿态,不再直接用 settings.mcpPort 单向驱动输入框
                    PortField(viewModel = viewModel, currentPort = settings.mcpPort)

                    RowDivider()

                    TokenField(
                        token = settings.mcpToken,
                        onTokenChange = viewModel::setMcpToken,
                        onGenerate = viewModel::regenerateMcpToken,
                    )

                    mcpStatus.lastError?.let { error ->
                        Text(
                            text = "启动失败:$error",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = { viewModel.toggleMcp() }) {
                            Text(if (mcpStatus.running) "停止服务" else "启动服务")
                        }
                        TextButton(onClick = { showMcpHelp(viewModel) }) { Text("用法示例") }
                    }

                    Text(
                        text = "接口:POST / JSON-RPC 2.0,支持 initialize、tools/list、tools/call;" +
                            "工具覆盖日志查询、统计、tag 排行、清空、采集控制、缓冲区导出、" +
                            "进程列表、崩溃读写、设置读写、设备信息。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    )
                }
            }

            // ---------- 工具日志 ----------
            stickyHeader(key = settingsItemKey(SettingsSection.APP_LOG)) {
                GroupHeader("工具日志", "LogCat 自身动作记录,排查采集失败时用")
            }
            item(key = "applog-actions") {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
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
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        )
                    }
                }
            }
            if (appLogs.isNotEmpty()) {
                // 用 items 而非在 item 里 forEach,避免长列表一次性全部组合
                items(
                    count = minOf(appLogs.size, 40),
                    key = { index -> "applog-$index" },
                ) { index ->
                    val line = appLogs[appLogs.lastIndex - index]
                    Text(
                        text = "${line.time}  ${line.level}  ${line.text}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 1.dp),
                    )
                }
            }

            // ---------- 关于 ----------
            stickyHeader(key = settingsItemKey(SettingsSection.ABOUT)) {
                GroupHeader("关于", "版本、ABI、设备信息")
            }
            item(key = "about") {
                SettingsCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
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
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * MCP 端口输入。
 *
 * 原实现直接把 settings.mcpPort 单向绑定到输入框,onValueChange 里做
 * toIntOrNull 后立即 clamp,导致:删空无法输入、输入 1 立刻被改成 1024。
 * 这里用本地草稿字符串,只有能解析且落在合法区间才提交;
 * 失焦时把草稿回正为当前生效端口。
 */
@Composable
private fun PortField(viewModel: MainViewModel, currentPort: Int) {
    var draft by remember(currentPort) { mutableStateOf(currentPort.toString()) }
    val parsed = draft.toIntOrNull()
    val invalid = parsed == null ||
        parsed < SettingsRepository.MIN_PORT ||
        parsed > SettingsRepository.MAX_PORT

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { text ->
                // 只允许数字,且长度上限 5 位,防止输入出非法值
                val digits = text.filter(Char::isDigit).take(5)
                draft = digits
                digits.toIntOrNull()?.let { value ->
                    if (value in SettingsRepository.MIN_PORT..SettingsRepository.MAX_PORT) {
                        viewModel.setMcpPort(value)
                    }
                }
            },
            modifier = Modifier.width(132.dp),
            singleLine = true,
            isError = invalid,
            label = { Text("端口") },
            supportingText = if (invalid) {
                { Text("范围 ${SettingsRepository.MIN_PORT}-${SettingsRepository.MAX_PORT}") }
            } else null,
        )
        TextButton(onClick = { viewModel.setMcpPort(SettingsRepository.DEFAULT_MCP_PORT) }) {
            Text("默认 ${SettingsRepository.DEFAULT_MCP_PORT}")
        }
    }
}

@Composable
private fun TokenField(token: String, onTokenChange: (String) -> Unit, onGenerate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("留空表示不校验") },
            label = { Text("Bearer Token") },
        )
        TextButton(onClick = onGenerate) { Text("生成") }
    }
}

/**
 * 设置页分组。声明顺序必须与 LazyColumn 中 stickyHeader 的出现顺序一致,
 * 顶部快速跳转依赖这个顺序推算每个分组的起始索引。
 */
private enum class SettingsSection(val label: String) {
    APPEARANCE("外观"),
    CAPTURE("采集"),
    DISPLAY("显示"),
    CRASH("崩溃"),
    MCP("MCP"),
    APP_LOG("工具日志"),
    ABOUT("关于"),
}

/**
 * 分组 header 在 LazyColumn 里的稳定 key。
 * 跳转时用它从 layoutInfo 反查索引,保证 key 与 stickyHeader 一一对应。
 */
private fun settingsItemKey(section: SettingsSection): String = "hdr-${section.name.lowercase()}"

/** 顶栏下方的一条分组跳转 chip,点击平滑滚动到对应分组。 */
@Composable
private fun SectionJumpBar(onJump: (SettingsSection) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SettingsSection.entries.forEach { section ->
            val tint = MaterialTheme.colorScheme.primary
            PressableBox(
                onClick = { onJump(section) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.height(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 11.dp),
                )
            }
        }
    }
}

/** 卡片内的条目分隔线:左右缩进与内容起始对齐,不顶到卡片圆角。 */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

/**
 * 吸顶分组标题。
 *
 * 用 LazyColumn 的 stickyHeader 承载:滚动时当前分组名始终可见,
 * 长列表(设置页共 7 组、800 行)里能随时知道自己在哪一块,
 * 这是之前平铺标题做不到的。
 *
 * 背景必须是不透明的 surface,否则吸顶时会透出下方内容。
 */
@Composable
private fun GroupHeader(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 分组卡片容器:把一组设置项包在圆角卡片里。
 *
 * 之前所有设置项平铺在同一底色上,靠留白分组,层级很弱。
 * 改成卡片后,每组是一个明确的视觉单元,边界来自色差而非仅靠间距。
 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        content = content,
    )
}

@Composable
private fun ChoiceBlock(
    label: String,
    note: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/**
 * 选项 chip。视觉 30dp 高,外层 48dp 触控区(无障碍最小触控目标)。
 */
@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    // 选中态用颜色过渡而非硬切:140ms,关闭动画时瞬时完成
    val background = animatedSelectionColor(
        selected = selected,
        selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        normalColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.fast(reduceMotion()),
        label = "chip-content",
    )

    // 视觉 30dp,外层 48dp 触控区(无障碍最小触控目标)
    Box(
        modifier = Modifier
            .height48()
            .clip(RoundedCornerShape(9.dp))
            .pressableClick(onClick, role = Role.RadioButton)
            .semantics {
                this.selected = selected
                contentDescription = text
            }
            .padding(vertical = 9.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
        )
    }
}

/**
 * 色板圆形色块。
 *
 * 之前只有颜色 + 下方小字,点击目标是 32dp 圆形,不达 48dp;
 * 且选中态用 ✓ 字符。这里改为 40dp 圆 + 48dp 触控区,
 * 选中态用同心环(不依赖字形),并补充无障碍语义。
 */
@Composable
private fun PaletteDot(
    palette: AppPalette,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .height48()
                .width(48.dp)
                .then(if (enabled) Modifier.clickableNoRipple(onClick) else Modifier)
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                    contentDescription = "色板 ${palette.label}"
                },
            contentAlignment = Alignment.Center,
        ) {
            // 选中时圆变大、内环淡入,两处都走过渡,避免色板在点击瞬间"跳一下"
            val dotSize by animateDpAsState(
                targetValue = if (selected) 34.dp else 28.dp,
                animationSpec = Motion.fast(reduceMotion()),
                label = "palette-size",
            )
            val ringAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = Motion.fast(reduceMotion()),
                label = "palette-ring",
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (enabled) palette.seed else palette.seed.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                if (ringAlpha > 0f) {
                    // 同心内环表示选中,不依赖 ✓ 字形
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f * ringAlpha)),
                    )
                }
            }
        }
        Text(
            text = palette.label,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SliderBlock(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    note: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            .clip(RoundedCornerShape(12.dp))
            .clickableNoRipple(onSelect)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
            )
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = "${mode.label},$statusText"
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 单选圆点,选中态用内环,不用字符
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
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

private fun showMcpHelp(viewModel: MainViewModel) {
    viewModel.postMessage(
        "示例:先 tools/list,再 logcat_query {\"level\":\"E\",\"limit\":50};" +
            "crash_list;settings_set {\"mode\":\"root\"}"
    )
}

/** 固定 48dp 高度,作为 chip 的无障碍最小触控区。 */
private fun Modifier.height48(): Modifier = this.then(Modifier.height(48.dp))

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}
