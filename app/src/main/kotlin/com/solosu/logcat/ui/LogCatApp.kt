package com.solosu.logcat.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import com.solosu.logcat.ui.crash.CrashScreen
import com.solosu.logcat.ui.icons.AppIcons
import com.solosu.logcat.ui.logcat.LogCatScreen
import com.solosu.logcat.ui.settings.SettingsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    LOGS("日志", AppIcons.Logs),
    CRASH("崩溃", AppIcons.Crash),
    SETTINGS("设置", AppIcons.Settings),
}

@Composable
fun LogCatApp(viewModel: MainViewModel = viewModel()) {
    var currentTab by rememberSaveable { mutableStateOf(Tab.LOGS.ordinal) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()

    // 回到前台时若日志流已停止则自动恢复
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontWeight = if (currentTab == index) FontWeight.SemiBold else FontWeight.Normal) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (Tab.entries[currentTab]) {
            Tab.LOGS -> LogCatScreen(viewModel, modifier)
            Tab.CRASH -> CrashScreen(viewModel, modifier)
            Tab.SETTINGS -> SettingsScreen(viewModel, modifier)
        }
    }
}
