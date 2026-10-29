@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.solosu.logcat.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solosu.logcat.data.SettingsRepository
import com.solosu.logcat.shell.ShellMode
import com.solosu.logcat.ui.BuildInfo
import com.solosu.logcat.ui.MainViewModel
import com.solosu.logcat.ui.icons.AppIcons
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    val appLogs by viewModel.appLogLines.collectAsStateWithLifecycle()

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            TopAppBar(
                title = {
                    Column {
                        Text("设置", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "环境:${BuildInfo.abis}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        item { SectionTitle("日志权限模式") }

        items(ShellMode.entries.toList()) { mode ->
            val usable = availability.isAvailable(mode)
            ModeRow(
                mode = mode,
                selected = settings.mode == mode,
                usable = usable,
                statusText = when (mode) {
                    ShellMode.DEFAULT -> "无需授权"
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

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { viewModel.requestShizukuPermission() }) { Text("申请 Shizuku 授权") }
                TextButton(onClick = { viewModel.refreshModes() }) { Text("刷新权限状态") }
            }
        }

        item { HorizontalDivider() }
        item { SectionTitle("采集参数") }

        item {
            ValueRow(
                title = "最大缓冲行数",
                value = "${settings.maxLines}",
                description = "超出后丢弃最早的行(${SettingsRepository.MIN_LINES}~${SettingsRepository.MAX_LINES})",
            )
        }

        item {
            Slider(
                value = settings.maxLines.toFloat(),
                onValueChange = { viewModel.setMaxLines(it.roundToInt()) },
                valueRange = SettingsRepository.MIN_LINES.toFloat()..SettingsRepository.MAX_LINES.toFloat(),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item { HorizontalDivider() }
        item { SectionTitle("显示") }

        item {
            SwitchRow(
                title = "自动滚动到最新",
                description = "新日志到达时自动跟随底部",
                checked = settings.autoScroll,
                onCheckedChange = viewModel::setAutoScroll,
            )
        }
        item {
            SwitchRow(
                title = "自动换行",
                description = "关闭后每条日志单行显示,便于快速浏览",
                checked = settings.wrapLines,
                onCheckedChange = viewModel::setWrapLines,
            )
        }
        item {
            SwitchRow(
                title = "显示时间戳",
                checked = settings.showTimestamp,
                onCheckedChange = viewModel::setShowTimestamp,
            )
        }
        item {
            SwitchRow(
                title = "显示 PID / TID",
                checked = settings.showPidTid,
                onCheckedChange = viewModel::setShowPidTid,
            )
        }

        item { HorizontalDivider() }
        item { SectionTitle("应用内日志") }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "默认模式下可在此查看本工具自身动作",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { viewModel.clearAppLogs() }) { Text("清空") }
            }
        }

        if (appLogs.isEmpty()) {
            item {
                Text(
                    text = "(暂无记录)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(appLogs.reversed()) { line ->
                Text(
                    text = "${line.time} ${line.level} ${line.text}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { HorizontalDivider() }
        item { SectionTitle("关于") }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AboutLine("应用", "LogCat")
                AboutLine("包名", "com.solosu.logcat")
                AboutLine("版本", "1.0.0")
                AboutLine("ABI", BuildInfo.abis)
                AboutLine("64 位进程", if (BuildInfo.is64Bit) "是" else "否")
                AboutLine("系统版本", "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                AboutLine("设备", "${android.os.Build.BRAND} ${android.os.Build.MODEL}")
            }
        }

        item {
            Text(
                text = "默认模式仅能读取本应用自身日志;Root 与 Shizuku 模式可读取整机日志。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun ModeRow(
    mode: ShellMode,
    selected: Boolean,
    usable: Boolean,
    statusText: String,
    onSelect: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = when (mode) {
                    ShellMode.DEFAULT -> AppIcons.Logs
                    ShellMode.SHIZUKU -> AppIcons.Share
                    ShellMode.ROOT -> AppIcons.Info
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (!usable) {
                        Text(
                            text = "不可用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                FilterChip(selected = true, onClick = onSelect, label = { Text("当前") })
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ValueRow(title: String, value: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            AssistChip(onClick = {}, label = { Text(value) })
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}
