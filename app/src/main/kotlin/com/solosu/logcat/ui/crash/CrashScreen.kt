@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.solosu.logcat.ui.crash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solosu.logcat.crash.CrashRecord
import com.solosu.logcat.ui.MainViewModel
import com.solosu.logcat.ui.icons.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val crashes by viewModel.crashes.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = crashes.firstOrNull { it.id == selectedId }

    if (selected != null) {
        CrashDetail(
            record = selected,
            onBack = { selectedId = null },
            onCopy = {
                viewModel.exportCrashes({ text ->
                    clipboard.setText(AnnotatedString(text))
                    viewModel.postMessage("崩溃信息已复制")
                }, selected)
            },
            onDelete = {
                viewModel.removeCrash(selected.id)
                selectedId = null
            },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("崩溃记录", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${crashes.size} 条记录,来源:本应用崩溃 + FATAL 系统日志",
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
                                clipboard.setText(AnnotatedString(text))
                                viewModel.postMessage("全部崩溃记录已复制")
                            }
                        }, null)
                    }) { Icon(AppIcons.Copy, contentDescription = "复制全部") }
                    IconButton(onClick = { viewModel.clearCrashes() }) {
                        Icon(AppIcons.Delete, contentDescription = "清空")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (crashes.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        AppIcons.Crash,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "暂无崩溃记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = "应用崩溃会自动记录;系统 FATAL 日志需要 Root 或 Shizuku 模式",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = crashes, key = { it.id }) { record ->
                        CrashRow(record = record, onClick = { selectedId = record.id })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashRow(record: CrashRecord, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                color = if (record.fromSystem) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = if (record.fromSystem) "系统" else "应用",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                text = record.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                AppIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
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
        Text(
            text = "${record.time} · ${record.thread}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashDetail(
    record: CrashRecord,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = "返回") }
                },
                title = { Text("崩溃详情", fontWeight = FontWeight.SemiBold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DetailField("时间", record.time)
            DetailField("来源", if (record.fromSystem) "系统日志(FATAL)" else "应用崩溃处理器")
            DetailField("模块 / Tag", record.module)
            DetailField("线程", record.thread)
            DetailField("异常", record.exception)
            HorizontalDivider()
            Text(
                text = "堆栈信息",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.medium,
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
            TextButton(onClick = onCopy) { Text("复制完整信息") }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
