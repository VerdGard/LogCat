package com.solosu.logcat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solosu.logcat.ui.crash.CrashScreen
import com.solosu.logcat.ui.icons.AppIcons
import com.solosu.logcat.ui.logcat.LogCatScreen
import com.solosu.logcat.ui.settings.SettingsScreen
import com.solosu.logcat.ui.theme.LogCatTheme
import com.solosu.logcat.ui.theme.Motion
import com.solosu.logcat.ui.theme.reduceMotion

/** 顶层三页,平级关系,由底部导航切换。 */
enum class Route(val title: String, val icon: ImageVector) {
    LOGS("日志", AppIcons.Logs),
    CRASH("崩溃", AppIcons.Crash),
    SETTINGS("设置", AppIcons.Settings),
}

@Composable
fun LogCatApp(viewModel: MainViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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
    var route by rememberSaveable { mutableStateOf(Route.LOGS.ordinal) }
    val current = Route.entries[route]
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()

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
                Route.entries.forEachIndexed { index, item ->
                    val selected = route == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { if (!selected) route = index },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                modifier = Modifier.size(21.dp),
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
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
        // 三页平级切换:淡入淡出 + 极小位移,不做方向性滑动,避免暗示层级关系。
        // 位移取 1/24 屏宽:足以让"换了一页"被感知,又不至于像翻页动画那样打断注意力。
        val reduced = reduceMotion()
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                // fadeIn/fadeOut 要 Float 的 spec,slideIn/OutHorizontally 要 IntOffset 的,
                // 两者不能共用同一个泛型实例
                val fadeInSpec = Motion.screenIn<Float>(reduced)
                val fadeOutSpec = Motion.screenOut<Float>(reduced)
                val slideInSpec = Motion.screenIn<androidx.compose.ui.unit.IntOffset>(reduced)
                val slideOutSpec = Motion.screenOut<androidx.compose.ui.unit.IntOffset>(reduced)
                (
                    fadeIn(fadeInSpec) +
                        slideInHorizontally(slideInSpec) { width -> width / 24 }
                    ) togetherWith (
                    fadeOut(fadeOutSpec) +
                        slideOutHorizontally(slideOutSpec) { width -> -width / 24 }
                    )
            },
            label = "route",
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) { target ->
            when (target) {
                Route.LOGS -> LogCatScreen(viewModel = viewModel)
                Route.CRASH -> CrashScreen(viewModel = viewModel)
                Route.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
