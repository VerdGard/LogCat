package com.solosu.logcat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solosu.logcat.ui.crash.CrashScreen
import com.solosu.logcat.ui.icons.AppIcons
import com.solosu.logcat.ui.logcat.LogCatScreen
import com.solosu.logcat.ui.settings.SettingsScreen
import com.solosu.logcat.ui.theme.LogCatTheme

private enum class Tab(
    val label: String,
    val icon: ImageVector,
    val description: String,
) {
    LOGS("日志", AppIcons.Logs, "日志"),
    CRASH("崩溃", AppIcons.Crash, "崩溃"),
    SETTINGS("设置", AppIcons.Settings, "设置"),
}

@Composable
fun LogCatApp(viewModel: MainViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // 主题跟随设置实时切换(莫奈取色 / 预设色板 / 明暗模式)
    LogCatTheme(
        themeMode = settings.themeMode,
        monetEnabled = settings.monetEnabled,
        paletteId = settings.paletteId,
    ) {
        LogCatContent(viewModel)
    }
}

@Composable
private fun LogCatContent(viewModel: MainViewModel) {
    var currentTab by rememberSaveable { mutableStateOf(Tab.LOGS.ordinal) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()

    // 回到前台时若日志流已停止则自动恢复
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
        viewModel.consumeMessage()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Tab.entries.forEachIndexed { index, tab ->
                    val selected = currentTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { if (!selected) currentTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.description,
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // 页面切换动画:根据切换方向做水平滑动 + 淡入淡出
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val forward = targetState > initialState
                    val offset = if (forward) 1 else -1
                    (slideInHorizontally(tween(220)) { width -> width / 8 * offset } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(180)) { width -> -width / 12 * offset } + fadeOut(tween(180)))
                },
                label = "tab-content",
            ) { index ->
                when (Tab.entries[index]) {
                    Tab.LOGS -> LogCatScreen(viewModel)
                    Tab.CRASH -> CrashScreen(viewModel)
                    Tab.SETTINGS -> SettingsScreen(viewModel)
                }
            }
        }
    }
}
