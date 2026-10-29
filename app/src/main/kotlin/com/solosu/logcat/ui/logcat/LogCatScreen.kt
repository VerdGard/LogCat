@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.solosu.logcat.ui.logcat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solosu.logcat.data.AppSettings
import com.solosu.logcat.log.LogBuffer
import com.solosu.logcat.log.LogEntry
import com.solosu.logcat.log.LogLevel
import com.solosu.logcat.ui.MainViewModel
import com.solosu.logcat.ui.icons.AppIcons
import com.solosu.logcat.ui.theme.LogColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogCatScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val repository = viewModel.logRepository
    val allEntries = repository.entries
    val running = repository.running
    val error = repository.lastError

    var showSearch by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // 只依赖筛选条件重算;allEntries 是 SnapshotStateList,其变更会自动使 derivedStateOf 失效
    val visibleEntries by remember(settings.level, settings.keyword) {
        derivedStateOf {
            val minOrder = settings.level.order
            val keyword = settings.keyword.trim().lowercase()
            if (minOrder == LogLevel.VERBOSE.order && keyword.isEmpty()) {
                allEntries.toList()
            } else {
                allEntries.filter { entry ->
                    entry.level.order >= minOrder && (keyword.isEmpty() || entry.searchText.contains(keyword))
                }
            }
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(visibleEntries.size, settings.autoScroll) {
        if (settings.autoScroll && visibleEntries.isNotEmpty()) {
            listState.scrollToItem(visibleEntries.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("LogCat", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = buildString {
                            append(settings.mode.label)
                            append(" · ")
                            append(settings.buffer.label)
                            append(" · ")
                            append(if (running) "采集中" else "已暂停")
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
                    Icon(if (running) AppIcons.Pause else AppIcons.Play, contentDescription = if (running) "暂停" else "开始")
                }
                IconButton(onClick = { viewModel.clearLogBuffer(clearSystem = false) }) {
                    Icon(AppIcons.Clear, contentDescription = "清空")
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(repository.exportVisible(visibleEntries)))
                    viewModel.postMessage("已复制 ${visibleEntries.size} 行到剪贴板")
                }) {
                    Icon(AppIcons.Copy, contentDescription = "复制")
                }
            },
        )

        if (showSearch) {
            SearchField(
                keyword = settings.keyword,
                onKeywordChange = viewModel::setKeyword,
                onClose = {
                    showSearch = false
                    viewModel.setKeyword("")
                },
            )
        }

        LevelFilterRow(current = settings.level, onSelect = viewModel::setLevel)
        BufferTabRow(current = settings.buffer, onSelect = viewModel::setBuffer)

        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(AppIcons.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.toggleLogging() }) { Text("重试") }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (visibleEntries.isEmpty()) {
                EmptyHint(running = running, keyword = settings.keyword, level = settings.level)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    items(items = visibleEntries, key = { it.seq }) { entry ->
                        LogRow(entry = entry, settings = settings)
                    }
                }
            }
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        BottomStatusBar(
            total = allEntries.size,
            visible = visibleEntries.size,
            dropped = repository.droppedLines,
            autoScroll = settings.autoScroll,
            onToggleAutoScroll = { viewModel.setAutoScroll(!settings.autoScroll) },
        )
    }
}

@Composable
private fun SearchField(keyword: String, onKeywordChange: (String) -> Unit, onClose: () -> Unit) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        placeholder = { Text("按 tag 或内容过滤") },
        singleLine = true,
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) { Icon(AppIcons.Clear, contentDescription = "关闭搜索") }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
    )
}

@Composable
private fun LevelFilterRow(current: LogLevel, onSelect: (LogLevel) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogLevel.entries.filterNot { it == LogLevel.ASSERT }.forEach { level ->
            val selected = current == level
            Surface(
                color = if (selected) LogColors.of(level).copy(alpha = 0.18f) else Color.Transparent,
                shape = MaterialTheme.shapes.small,
                onClick = { onSelect(level) },
            ) {
                Text(
                    text = level.letter.toString(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) LogColors.of(level) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BufferTabRow(current: LogBuffer, onSelect: (LogBuffer) -> Unit) {
    val buffers = LogBuffer.entries
    ScrollableTabRow(
        selectedTabIndex = buffers.indexOf(current).coerceAtLeast(0),
        edgePadding = 8.dp,
        divider = { HorizontalDivider() },
    ) {
        buffers.forEach { buffer ->
            Tab(
                selected = buffer == current,
                onClick = { onSelect(buffer) },
                text = { Text(buffer.label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, settings: AppSettings) {
    val levelColor = LogColors.of(entry.level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (entry.level.order >= LogLevel.ERROR.order) levelColor.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = entry.level.letter.toString(),
                    color = levelColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
                if (settings.showTimestamp) {
                    Text(
                        text = entry.clock,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (settings.showPidTid) {
                    Text(
                        text = "${entry.pid}/${entry.tid}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = entry.tag,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text(
                text = entry.message + if (entry.overflow) "\n...(内容过长已截断)" else "",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                softWrap = settings.wrapLines,
                maxLines = if (settings.wrapLines) Int.MAX_VALUE else 1,
                overflow = if (settings.wrapLines) TextOverflow.Clip else TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EmptyHint(running: Boolean, keyword: String, level: LogLevel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                AppIcons.Logs,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when {
                    keyword.isNotBlank() -> "没有匹配「$keyword」的日志"
                    level != LogLevel.VERBOSE -> "当前级别(${level.label})下暂无日志"
                    running -> "等待日志输出..."
                    else -> "日志流已暂停"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BottomStatusBar(
    total: Int,
    visible: Int,
    dropped: Int,
    autoScroll: Boolean,
    onToggleAutoScroll: () -> Unit,
) {
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = buildString {
                append("$visible/$total 行")
                if (dropped > 0) append(" · 已丢弃 $dropped")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = onToggleAutoScroll, modifier = Modifier.size(28.dp)) {
            Icon(
                AppIcons.Play,
                contentDescription = if (autoScroll) "关闭自动滚动" else "开启自动滚动",
                modifier = Modifier.size(14.dp),
                tint = if (autoScroll) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
