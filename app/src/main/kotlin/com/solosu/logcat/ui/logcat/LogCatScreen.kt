@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.solosu.logcat.ui.logcat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.solosu.logcat.log.rawLine
import com.solosu.logcat.log.LogLevel
import com.solosu.logcat.ui.MainViewModel
import com.solosu.logcat.ui.components.EmptyState
import com.solosu.logcat.ui.components.LevelBadge
import com.solosu.logcat.ui.components.Tag
import com.solosu.logcat.ui.components.defaultExportName
import com.solosu.logcat.ui.components.writeTextToUri
import com.solosu.logcat.ui.components.rememberTextClipboard
import com.solosu.logcat.ui.icons.AppIcons
import com.solosu.logcat.ui.theme.LogColors
import kotlinx.coroutines.launch

@Composable
fun LogCatScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val repository = viewModel.logRepository
    val allEntries = repository.entries
    val running = repository.running
    val error = repository.lastError
    val copyText = rememberTextClipboard()

    var showSearch by remember { mutableStateOf(false) }
    var expandedSeq by remember { mutableStateOf<Long?>(null) }

    // 文件导出:用 SAF 让用户选保存位置,不申请任何存储权限
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

    // 关键性能点:过滤结果用 derivedStateOf 缓存。
    // allEntries 是 SnapshotStateList,读取其 size 即建立快照依赖,
    // 因此新增日志会自动失效;而滚动、展开详情不会触发重算。
    val visibleEntries by remember(settings.level, settings.keyword) {
        derivedStateOf {
            allEntries.size // 建立依赖
            val minOrder = settings.level.order
            val keyword = settings.keyword.trim().lowercase()
            if (minOrder == LogLevel.VERBOSE.order && keyword.isEmpty()) {
                allEntries.toList()
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
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }

    // 自动滚动:只在「已贴底」或用户开启自动滚动时跟随,避免打断向上翻看
    LaunchedEffect(visibleEntries.size) {
        if (visibleEntries.isEmpty()) return@LaunchedEffect
        if (settings.autoScroll || atBottom) {
            listState.scrollToItem(visibleEntries.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("日志", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = buildString {
                                append(settings.mode.label)
                                append(" · ")
                                append(settings.buffer.label)
                                if (running) append(" · 采集中") else append(" · 已暂停")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(AppIcons.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { viewModel.toggleLogging() }) {
                        Icon(
                            if (running) AppIcons.Pause else AppIcons.Play,
                            contentDescription = if (running) "暂停" else "开始",
                        )
                    }
                    IconButton(onClick = { viewModel.clearLogBuffer(clearSystem = false) }) {
                        Icon(AppIcons.Clear, contentDescription = "清空")
                    }
                    IconButton(onClick = {
                        copyText(repository.exportVisible(visibleEntries))
                        viewModel.postMessage("已复制 ${visibleEntries.size} 行")
                    }) {
                        Icon(AppIcons.Copy, contentDescription = "复制")
                    }
                    IconButton(onClick = {
                        if (visibleEntries.isEmpty()) {
                            viewModel.postMessage("当前没有可导出的日志")
                        } else {
                            pendingExport = repository.exportVisible(visibleEntries)
                            exportLauncher.launch(defaultExportName())
                        }
                    }) {
                        Icon(AppIcons.Export, contentDescription = "导出到文件")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
            ) {
                SearchBar(
                    keyword = settings.keyword,
                    onKeywordChange = viewModel::setKeyword,
                    onClose = {
                        showSearch = false
                        viewModel.setKeyword("")
                    },
                )
            }

            FilterBar(
                level = settings.level,
                buffer = settings.buffer,
                onLevel = viewModel::setLevel,
                onBuffer = viewModel::setBuffer,
                visible = visibleEntries.size,
                total = allEntries.size,
            )
            HorizontalDivider()

            if (error != null) {
                ErrorBanner(error = error, onRetry = { viewModel.toggleLogging() })
            }

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
                            com.solosu.logcat.shell.ShellMode.DEFAULT -> "默认模式仅能看到本应用日志"
                            else -> null
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(items = visibleEntries, key = { it.seq }) { entry ->
                            LogRow(
                                entry = entry,
                                settings = settings,
                                expanded = expandedSeq == entry.seq,
                                onToggle = { expandedSeq = if (expandedSeq == entry.seq) null else entry.seq },
                                onCopy = {
                                    copyText(entry.rawLine())
                                    viewModel.postMessage("已复制该条日志")
                                },
                            )
                        }
                    }

                    // 未贴底时提供回到底部的浮动按钮
                    AnimatedVisibility(
                        visible = !atBottom,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        FilledTonalButton(
                            onClick = { scrollScope.launch { listState.animateScrollToItem(visibleEntries.lastIndex) } },
                            modifier = Modifier.padding(16.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(AppIcons.ArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("最新", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(keyword: String, onKeywordChange: (String) -> Unit, onClose: () -> Unit) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        placeholder = { Text("按 tag 或消息内容过滤") },
        singleLine = true,
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(AppIcons.Clear, contentDescription = "关闭搜索", modifier = Modifier.size(18.dp))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
    )
}

/** 单行筛选条:级别 + 缓冲区,节省纵向空间。 */
@Composable
private fun FilterBar(
    level: LogLevel,
    buffer: LogBuffer,
    onLevel: (LogLevel) -> Unit,
    onBuffer: (LogBuffer) -> Unit,
    visible: Int,
    total: Int,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogLevel.entries.filterNot { it == LogLevel.ASSERT }.forEach { item ->
                val selected = level == item
                val color = LogColors.of(item)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) color.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onLevel(item) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = item.letter.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (visible == total) "$total" else "$visible/$total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(4.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LogBuffer.entries.forEach { item ->
                val selected = buffer == item
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable { onBuffer(item) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(AppIcons.Info, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

/**
 * 单条日志。
 * 折叠态:级别 + 时间 + tag + 消息首行(单行省略)
 * 展开态:完整消息,若日志很长也便于逐条审阅
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isError) levelColor.copy(alpha = 0.05f) else Color.Transparent)
            .clickable(onClick = onToggle)
            .animateContentSize()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LevelBadge(entry.level, size = 15)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            Text(
                text = entry.tag,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
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
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onCopy)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = highlight(entry.message, keyword, levelColor),
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.35f).sp,
            softWrap = settings.wrapLines || expanded,
            maxLines = if (settings.wrapLines || expanded) Int.MAX_VALUE else 1,
            overflow = if (settings.wrapLines || expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 21.dp),
        )
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
