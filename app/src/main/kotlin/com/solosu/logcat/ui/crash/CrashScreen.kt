@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.solosu.logcat.ui.crash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solosu.logcat.crash.CrashRecord
import com.solosu.logcat.ui.MainViewModel
import com.solosu.logcat.ui.components.EmptyState
import com.solosu.logcat.ui.components.Tag
import com.solosu.logcat.ui.components.rememberTextClipboard
import com.solosu.logcat.ui.icons.AppIcons

@Composable
fun CrashScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val crashes by viewModel.crashes.collectAsStateWithLifecycle()
    val copyText = rememberTextClipboard()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val selected = crashes.firstOrNull { it.id == selectedId }

    // 返回键:仅在详情页拦截,先退回列表;列表页不再拦截,交给系统默认行为
    BackHandler(enabled = selected != null) {
        selectedId = null
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空崩溃记录?") },
            text = { Text("将删除 ${crashes.size} 条记录,该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCrashes()
                    confirmClear = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            selected?.let { record ->
                CrashDetail(
                    record = record,
                    onBack = { selectedId = null },
                    onCopy = {
                        viewModel.exportCrashes({ text ->
                            copyText(text)
                            viewModel.postMessage("崩溃信息已复制")
                        }, record)
                    },
                    onDelete = {
                        viewModel.removeCrash(record.id)
                        selectedId = null
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = selected == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        title = {
                            Column {
                                Text("崩溃", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${crashes.size} 条记录",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                viewModel.exportCrashes({ text ->
                                    if (text.isBlank()) {
                                        viewModel.postMessage("暂无崩溃记录")
                                    } else {
                                        copyText(text)
                                        viewModel.postMessage("全部崩溃记录已复制")
                                    }
                                }, null)
                            }) { Icon(AppIcons.Copy, contentDescription = "复制全部") }

                            IconButton(
                                onClick = { if (crashes.isNotEmpty()) confirmClear = true },
                                enabled = crashes.isNotEmpty(),
                            ) { Icon(AppIcons.Delete, contentDescription = "清空全部") }
                        },
                    )
                },
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    if (crashes.isEmpty()) {
                        EmptyState(
                            icon = AppIcons.Crash,
                            title = "暂无崩溃记录",
                            description = "应用崩溃会自动记录;系统 FATAL 日志需要 Root 或 Shizuku 模式",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(items = crashes, key = { it.id }) { record ->
                                CrashCard(
                                    record = record,
                                    onClick = { selectedId = record.id },
                                    onCopy = {
                                        viewModel.exportCrashes({ text ->
                                            copyText(text)
                                            viewModel.postMessage("已复制该条崩溃")
                                        }, record)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 崩溃条目。
 *
 * 之前用固定 height(64.dp) 的色条,内容超过 64dp 时色条与卡片不等高。
 * 这里改用 IntrinsicSize.Min + fillMaxHeight,色条始终与内容同高。
 */
@Composable
private fun CrashCard(record: CrashRecord, onClick: () -> Unit, onCopy: () -> Unit) {
    val accent = if (record.fromSystem) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent.copy(alpha = 0.7f)),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(if (record.fromSystem) "系统" else "应用", accent)
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    AppIcons.Copy,
                    contentDescription = "复制该条崩溃",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onCopy)
                        .padding(6.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            record.topFrameClass?.let { frame ->
                Text(
                    text = frame,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = record.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = record.thread,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CrashDetail(
    record: CrashRecord,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var stackExpanded by remember { mutableStateOf(true) }
    val rotation by animateFloatAsState(if (stackExpanded) 90f else 0f, label = "stack-arrow")

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = "返回列表")
                    }
                },
                title = {
                    Column {
                        Text("崩溃详情", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = record.time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCopy) { Icon(AppIcons.Copy, contentDescription = "复制") }
                    IconButton(onClick = onDelete) { Icon(AppIcons.Delete, contentDescription = "删除") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(if (record.fromSystem) "系统日志" else "应用崩溃")
                    Tag(record.module, MaterialTheme.colorScheme.secondary)
                }
                Text(
                    text = record.exception,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MetaRow("线程 / Tag", record.thread)
                record.topFrameClass?.let { MetaRow("首帧", it) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { stackExpanded = !stackExpanded }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    AppIcons.ChevronRight,
                    contentDescription = if (stackExpanded) "收起堆栈" else "展开堆栈",
                    modifier = Modifier.size(14.dp).rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "堆栈信息",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${record.stackTrace.lineSequence().count()} 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = stackExpanded, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = record.stackTrace,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy) { Text("复制完整信息") }
                TextButton(onClick = onDelete) {
                    Text("删除该记录", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
