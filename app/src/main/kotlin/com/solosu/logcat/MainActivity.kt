package com.solosu.logcat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.solosu.logcat.ui.LogCatApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 主题由 LogCatApp 内部根据设置动态提供,这里不再包裹 LogCatTheme
        setContent {
            LogCatApp()
        }
    }
}
