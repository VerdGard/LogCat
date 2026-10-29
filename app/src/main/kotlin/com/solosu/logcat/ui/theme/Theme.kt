package com.solosu.logcat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.solosu.logcat.log.LogLevel

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6C4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2C6),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6355),
    tertiary = Color(0xFF3B6470),
    background = Color(0xFFFBFDF8),
    surface = Color(0xFFFBFDF8),
    surfaceVariant = Color(0xFFDCE5DB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD6AA),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF00522D),
    onPrimaryContainer = Color(0xFFA8F2C6),
    secondary = Color(0xFFB5CCBB),
    tertiary = Color(0xFFA3CDDA),
    background = Color(0xFF191C1A),
    surface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFF404942),
)

@Composable
fun LogCatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** 日志级别配色。默认取一组在深浅色下都能辨识的颜色。 */
object LogColors {
    private val verbose = Color(0xFF8A8F98)
    private val debug = Color(0xFF3B82F6)
    private val info = Color(0xFF16A34A)
    private val warn = Color(0xFFCA8A04)
    private val error = Color(0xFFDC2626)
    private val fatal = Color(0xFFB91C1C)

    fun of(level: LogLevel): Color = when (level) {
        LogLevel.VERBOSE -> verbose
        LogLevel.DEBUG -> debug
        LogLevel.INFO -> info
        LogLevel.WARN -> warn
        LogLevel.ERROR -> error
        LogLevel.FATAL, LogLevel.ASSERT -> fatal
    }
}
