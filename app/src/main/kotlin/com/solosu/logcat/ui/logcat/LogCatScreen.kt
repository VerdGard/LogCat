@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.solosu.logcat.ui.logcat

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solosu.logcat.data.AppSettings
import com.solosu.logcat.log.LogBuffer
import com.solosu.logcat.log.LogEntry
import com.solosu.logcat.log.LogLevel
import com.solosu.logcat.log.rawLine
import com.solosu.logcat.shell.ShellMode
import com.solosu.logcat.ui.MainViewModel
import com.solosu.logcat.ui.components.EmptyState
import com.solosu.logcat.ui.components.Tag
import com.solosu.logcat.ui.components.defaultExportName
import com.solosu.logcat.ui.components.rememberTextClipboard
import com.solosu.logcat.ui.components.writeTextToUri
import com.solosu.logcat.ui.icons.AppIcons
import com.solosu.logcat.ui.theme.LogColors
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import com.solosu.logcat.ui.components.animatedSelectionColor
import com.solosu.logcat.ui.components.pressableClick
import com.solosu.logcat.ui.theme.Motion
import com.solosu.logcat.ui.theme.reduceMotion

@Composable
fun LogCatScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val repository = viewModel.logRepository
    val allEntries = repository.entries
    val running = repository.running
    val error = repository.lastError
    val copyText = rememberTextClipboard()

    var showSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var expandedSeq by remember { mutableStateOf<Long?>(null) }

    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri == null || text == null) return@rememberLauncherForActivityResult
        exportScope.launch {
            writeTextToUri(context, uri, text)
                .onSuccess { viewModel.postMessage("已导出到所选文件") }
                .onFailure { viewModel.postMessage("导出失败:${it.message ?: "未知错误"}") }
        }
    }

    /*
     * 过滤结果缓存:只在缓冲内容或筛选条件变化时重算。
     *
     * 无筛选时直接返回 SnapshotStateList 本身(它实现了 List),不再 toList():
     * 日志流约每 120ms 提交一批,每批都全量复制 3000 行是纯浪费。
     * 有筛选时才产生新列表,此时复制不可避免。
     */
    val visibleEntries: List<LogEntry> by remember(settings.level, settings.keyword) {
        derivedStateOf {
            val minOrder = settings.level.order
            val keyword = settings.keyword.trim().lowercase()
            if (minOrder == LogLevel.VERBOSE.order && keyword.isEmpty()) {
                allEntries
            } else {
                allEntries.filter { entry ->
                    entry.level.order >= minOrder &&
                        (keyword.isEmpty() || entry.searchText.contains(keyword))
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()

    // 是否贴近底部。列表为空时视为贴底,避免「空列表显示回到底部按钮」
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf true
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 2
        }
    }

    /*
     * 自动滚动。
     *
     * 之前用 visibleEntries.size 做 key:环形缓冲被写满(maxLines 已到上限)后
     * size 恒定不再变化,新日志进来时 effect 不会重启,自动滚动失效。
     * 这里改用「最后一条的 seq」:每来一条新日志 seq 必变,以此驱动跟随;
     * 而滚动、展开详情都不会改动它。
     */
    val lastSeq = visibleEntries.lastOrNull()?.seq ?: -1L
    LaunchedEffect(lastSeq) {
        if (lastSeq < 0 || visibleEntries.isEmpty()) return@LaunchedEffect
        if (settings.autoScroll || atBottom) {
            listState.scrollToItem(visibleEntries.lastIndex)
        }
    }

    // 搜索栏打开时,返回键先收起它而不是退出页面
    BackHandler(enabled = showSearch || showFilters) {
        if (showSearch) {
            showSearch = false
            viewModel.setKeyword("")
        } else {
            showFilters = false
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusDot(running = running, error = error != null)
                        Column {
                            Text(
                                text = "日志",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = buildString {
                                    append(settings.mode.label)
                                    append(" · ")
                                    append(settings.buffer.label)
                                    append(" · ")
                                    append("${visibleEntries.size}/${allEntries.size}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (showSearch) {
                            showSearch = false
                            viewModel.setKeyword("")
                        } else {
                            showSearch = true
                        }
                    }) { Icon(AppIcons.Search, contentDescription = "搜索") }

                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            AppIcons.Filter,
                            contentDescription = "筛选",
                            tint = if (showFilters) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(onClick = { viewModel.toggleLogging() }) {
                        Icon(
                            if (running) AppIcons.Pause else AppIcons.Play,
                            contentDescription = if (running) "暂停采集" else "开始采集",
                        )
                    }

                    OverflowMenu(
                        onClear = { viewModel.clearLogBuffer(clearSystem = false) },
                        onClearDevice = { viewModel.clearLogBuffer(clearSystem = true) },
                        onCopy = {
                            copyText(repository.exportVisible(visibleEntries))
                            viewModel.postMessage("已复制 ${visibleEntries.size} 行")
                        },
                        onExport = {
                            if (visibleEntries.isEmpty()) {
                                viewModel.postMessage("当前没有可导出的日志")
                            } else {
                                pendingExport = repository.exportVisible(visibleEntries)
                                exportLauncher.launch(defaultExportName())
                            }
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn(Motion.medium<Float>(reduceMotion())) +
                    slideInVertically(Motion.medium<androidx.compose.ui.unit.IntOffset>(reduceMotion())) { -it / 2 },
                exit = fadeOut(Motion.exit<Float>(reduceMotion())) +
                    slideOutVertically(Motion.exit<androidx.compose.ui.unit.IntOffset>(reduceMotion())) { -it / 2 },
            ) {
                CompactSearchBar(
                    keyword = settings.keyword,
                    onKeywordChange = viewModel::setKeyword,
                    onClose = {
                        showSearch = false
                        viewModel.setKeyword("")
                    },
                )
            }

            AnimatedVisibility(
                visible = showFilters,
                enter = fadeIn(Motion.medium<Float>(reduceMotion())) +
                    slideInVertically(Motion.medium<androidx.compose.ui.unit.IntOffset>(reduceMotion())) { -it / 2 },
                exit = fadeOut(Motion.exit<Float>(reduceMotion())) +
                    slideOutVertically(Motion.exit<androidx.compose.ui.unit.IntOffset>(reduceMotion())) { -it / 2 },
            ) {
                FilterPanel(
                    settings = settings,
                    onLevel = viewModel::setLevel,
                    onBuffer = viewModel::setBuffer,
                    onCompactRows = viewModel::setCompactRows,
                    onWrapLines = viewModel::setWrapLines,
                    onAutoScroll = viewModel::setAutoScroll,
                )
            }

            if (error != null) {
                ErrorBanner(error = error, onRetry = { viewModel.toggleLogging() })
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (visibleEntries.isEmpty()) {
                    EmptyState(
                        icon = AppIcons.Logs,
                        title = when {
                            settings.keyword.isNotBlank() -> "没有匹配「${settings.keyword}」的日志"
                            settings.level != LogLevel.VERBOSE -> "当前级别(${settings.level.label})下暂无日志"
                            running -> "等待日志输出..."
                            else -> "日志流已暂停"
                        },
                        description = when (settings.mode) {
                            ShellMode.DEFAULT -> "默认模式仅能看到本应用日志"
                            else -> null
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // 紧凑模式几乎不留纵向 padding,一屏尽可能多行
                        contentPadding = PaddingValues(
                            vertical = if (settings.compactRows) 2.dp else 6.dp,
                        ),
                    ) {
                        // 用不重复的 seq 作为 key;itemContent 内再判断是否展开,
                        // 这样展开态切换不会导致整表重建
                        items(items = visibleEntries, key = { it.seq }) { entry ->
                            LogRow(
                                entry = entry,
                                settings = settings,
                                expanded = expandedSeq == entry.seq,
                                onToggle = {
                                    expandedSeq = if (expandedSeq == entry.seq) null else entry.seq
                                },
                                onCopy = {
                                    copyText(entry.rawLine())
                                    viewModel.postMessage("已复制该条日志")
                                },
                            )
                        }
                    }

                    BackToBottomButton(
                        visible = !atBottom,
                        onClick = {
                            // B2 修复:空列表时 lastIndex 为 -1,animateScrollToItem(-1) 会抛异常
                            val target = visibleEntries.lastIndex
                            if (target >= 0) {
                                scrollScope.launch { listState.animateScrollToItem(target) }
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )

                    if (running) {
                        StreamingIndicator(modifier = Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
    }
}

/**
 * 溢出菜单:把「清空 / 复制 / 导出 / 清设备缓冲」收进一个入口。
 * TopBar 只保留搜索、筛选、采集开关三个高频动作,避免图标堆叠。
 */
@Composable
private fun OverflowMenu(
    onClear: () -> Unit,
    onClearDevice: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(AppIcons.More, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("复制当前列表") },
                leadingIcon = { Icon(AppIcons.Copy, contentDescription = null, modifier = Modifier.size(17.dp)) },
                onClick = { expanded = false; onCopy() },
            )
            DropdownMenuItem(
                text = { Text("导出到文件") },
                leadingIcon = { Icon(AppIcons.Export, contentDescription = null, modifier = Modifier.size(17.dp)) },
                onClick = { expanded = false; onExport() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("清空本地缓冲") },
                leadingIcon = { Icon(AppIcons.Clear, contentDescription = null, modifier = Modifier.size(17.dp)) },
                onClick = { expanded = false; onClear() },
            )
            DropdownMenuItem(
                text = { Text("清空设备缓冲") },
                leadingIcon = { Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(17.dp)) },
                onClick = { expanded = false; onClearDevice() },
            )
        }
    }
}

/** 采集状态点:运行中绿色脉冲感(静态描边),暂停灰色,出错红色。 */
@Composable
private fun StatusDot(running: Boolean, error: Boolean) {
    val color = when {
        error -> MaterialTheme.colorScheme.error
        running -> Color(0xFF2E9E5B)
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .semantics {
                contentDescription = when {
                    error -> "采集异常"
                    running -> "正在采集"
                    else -> "已暂停"
                }
            },
    )
}

@Composable
private fun StreamingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .size(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
    )
}

@Composable
private fun CompactSearchBar(keyword: String, onKeywordChange: (String) -> Unit, onClose: () -> Unit) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        placeholder = { Text("按 tag 或消息内容过滤", style = MaterialTheme.typography.bodySmall) },
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        leadingIcon = {
            Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(AppIcons.Clear, contentDescription = "关闭搜索", modifier = Modifier.size(16.dp))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
    )
}

/**
 * 筛选面板:级别 / 缓冲区 / 行密度 / 换行 / 跟随。
 * 级别与缓冲区用可滚动单行 chip,避免像之前那样挤成两行占掉日志空间。
 */
@Composable
private fun FilterPanel(
    settings: AppSettings,
    onLevel: (LogLevel) -> Unit,
    onBuffer: (LogBuffer) -> Unit,
    onCompactRows: (Boolean) -> Unit,
    onWrapLines: (Boolean) -> Unit,
    onAutoScroll: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChipRow(label = "级别") {
            LogLevel.entries.filterNot { it == LogLevel.ASSERT }.forEach { item ->
                val selected = settings.level == item
                val color = LogColors.of(item)
                FilterChip(
                    text = item.letter.toString(),
                    selected = selected,
                    tint = color,
                    contentDescription = "最低级别 ${item.label}",
                    onClick = { onLevel(item) },
                )
            }
        }

        FilterChipRow(label = "缓冲") {
            LogBuffer.entries.forEach { item ->
                FilterChip(
                    text = item.label,
                    selected = settings.buffer == item,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = "缓冲区 ${item.label}",
                    onClick = { onBuffer(item) },
                )
            }
        }

        FilterChipRow(label = "显示") {
            FilterChip(
                text = "紧凑",
                selected = settings.compactRows,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = "紧凑行模式",
                onClick = { onCompactRows(true) },
            )
            FilterChip(
                text = "舒适",
                selected = !settings.compactRows,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = "舒适行模式",
                onClick = { onCompactRows(false) },
            )
            FilterChip(
                text = "换行",
                selected = settings.wrapLines,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = "自动换行",
                onClick = { onWrapLines(!settings.wrapLines) },
            )
            FilterChip(
                text = "跟随",
                selected = settings.autoScroll,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = "自动滚动到最新",
                onClick = { onAutoScroll(!settings.autoScroll) },
            )
        }
    }
}

@Composable
private fun FilterChipRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

/**
 * 单个筛选 chip。
 *
 * 视觉尺寸保持紧凑(26dp 高),但外层套 48dp 高的触控区,
 * 满足无障碍最小触控目标要求,不会因为 UI 变紧凑而变得难点。
 */
@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    // 选中态颜色过渡,避免切换级别时整排 chip 同时硬跳
    val background = animatedSelectionColor(
        selected = selected,
        selectedColor = tint.copy(alpha = 0.15f),
        normalColor = Color.Transparent,
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.fast(reduceMotion()),
        label = "filter-chip-content",
    )

    // 视觉 26dp,外层 48dp 触控区
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .pressableClick(onClick, role = Role.Button)
            .semantics { this.contentDescription = contentDescription }
            .padding(vertical = 11.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
        )
    }
}

@Composable
private fun ErrorBanner(error: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(AppIcons.Info, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Text("重试", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * 单条日志。
 *
 * 左侧贯穿整行的级别色条替代小块徽章——扫读时纵向色带比离散小方块更快定位。
 * 紧凑模式下折叠态只有一行;点击展开显示完整消息与复制入口。
 */
@Composable
private fun LogRow(
    entry: LogEntry,
    settings: AppSettings,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    val levelColor = LogColors.of(entry.level)
    val isError = entry.level.order >= LogLevel.ERROR.order
    val keyword = settings.keyword.trim()
    val fontSize = settings.logFontSize.sp
    val compact = settings.compactRows

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // IntrinsicSize.Min 让子项按最小内容高度测量,级别色条才能用
            // fillMaxHeight 撑满整行,而不会把 Row 撑到无限高
            .height(IntrinsicSize.Min)
            .background(
                when {
                    expanded -> MaterialTheme.colorScheme.surfaceContainer
                    isError -> levelColor.copy(alpha = 0.055f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onToggle)
            .animateContentSize(animationSpec = Motion.medium<androidx.compose.ui.unit.IntSize>(reduceMotion())),
    ) {
        // 级别色条:2dp 宽,贯穿整行高度
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(levelColor.copy(alpha = if (isError) 0.9f else 0.5f)),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = 8.dp,
                    end = 8.dp,
                    top = if (compact) 2.dp else 5.dp,
                    bottom = if (compact) 2.dp else 5.dp,
                ),
        ) {
            // 元信息行:级别字母 + 时间 + pid + tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = entry.level.letter.toString(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor,
                )
                if (settings.showTimestamp) {
                    Text(
                        text = entry.time,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (settings.showPidTid) {
                    Text(
                        text = "${entry.pid}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
                Text(
                    text = entry.tag,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.overflow) Tag("截断", MaterialTheme.colorScheme.error)
                if (expanded) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "复制",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .clickable(onClick = onCopy)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }

            Text(
                text = highlight(entry.message, keyword, levelColor),
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize,
                lineHeight = (fontSize.value * 1.32f).sp,
                // 紧凑模式折叠态只留一行;展开后一定换行
                softWrap = settings.wrapLines || expanded,
                maxLines = when {
                    expanded -> Int.MAX_VALUE
                    settings.wrapLines -> Int.MAX_VALUE
                    else -> 1
                },
                overflow = if (settings.wrapLines || expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                // 紧凑模式下不再为消息额外缩进,纵向对齐更整齐
                modifier = Modifier.padding(top = if (compact) 0.dp else 1.dp),
            )
        }
    }
}

/** 高亮关键字;无关键字时直接返回原文本,避免多余 AnnotatedString 分配。 */
private fun highlight(text: String, keyword: String, color: Color): AnnotatedString {
    if (keyword.isBlank()) return AnnotatedString(text)
    val lower = text.lowercase()
    val target = keyword.lowercase()
    if (!lower.contains(target)) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        while (true) {
            val index = lower.indexOf(target, cursor)
            if (index < 0) {
                append(text.substring(cursor))
                break
            }
            append(text.substring(cursor, index))
            withStyle(SpanStyle(background = color.copy(alpha = 0.28f), fontWeight = FontWeight.SemiBold)) {
                append(text.substring(index, index + target.length))
            }
            cursor = index + target.length
        }
    }
}

@Composable
private fun BackToBottomButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier.padding(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(AppIcons.ArrowDown, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text("最新", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 无涟漪点击,用于密集列表区域,避免大片水波纹影响阅读。 */
internal fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}
