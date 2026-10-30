package com.solosu.logcat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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

/** 顶层页面。日志是主页面,崩溃与设置作为可侧滑进入的覆盖页。 */
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
    // 日志为默认页;其余页面从右侧滑入并覆盖整个屏幕
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
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    // 回到日志页用反向动画,进入其他页从右滑入
                    val forward = targetState != Route.LOGS
                    val direction = if (forward) 1 else -1
                    (
                        slideInHorizontally(tween(240)) { width -> width * direction / 4 } +
                            fadeIn(tween(200))
                        ) togetherWith (
                        slideOutHorizontally(tween(200)) { width -> -width * direction / 6 } +
                            fadeOut(tween(200))
                        )
                },
                label = "route",
            ) { target ->
                when (target) {
                    Route.LOGS -> LogCatScreen(
                        viewModel = viewModel,
                        onOpenCrash = { route = Route.CRASH.ordinal },
                        onOpenSettings = { route = Route.SETTINGS.ordinal },
                    )

                    Route.CRASH -> CrashScreen(viewModel = viewModel, onBack = { route = Route.LOGS.ordinal })

                    Route.SETTINGS -> SettingsScreen(viewModel = viewModel, onBack = { route = Route.LOGS.ordinal })
                }
            }
        }
    }
}

/**
 * 首页左上角的入口按钮组:用于从日志页跳到崩溃或设置。
 * 独立成 composable 以便 LogCatScreen 的 TopBar 复用。
 */
@Composable
fun RouteEntryButtons(
    onOpenCrash: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        RouteChip(AppIcons.Crash, "崩溃", onOpenCrash)
        RouteChip(AppIcons.Settings, "设置", onOpenSettings)
    }
}

@Composable
private fun RouteChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
