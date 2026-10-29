package com.solosu.logcat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.solosu.logcat.ui.LogCatApp
import com.solosu.logcat.ui.theme.LogCatTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LogCatTheme {
                LogCatApp()
            }
        }
    }
}
