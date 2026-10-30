package com.solosu.logcat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.solosu.logcat.data.ThemeMode
import com.solosu.logcat.log.LogLevel

/**
 * 预设色板。每种色板提供一个主色种子,衍生出完整的浅色/深色配色,
 * 这样在 Android 12 以下、或用户关闭莫奈取色时也有协调的配色。
 */
@Immutable
enum class AppPalette(
    val id: String,
    val label: String,
    val seed: Color,
) {
    GREEN("green", "松绿", Color(0xFF1B6C4B)),
    BLUE("blue", "靛蓝", Color(0xFF2C5EA8)),
    PURPLE("purple", "紫罗兰", Color(0xFF6A4BA8)),
    ORANGE("orange", "琥珀", Color(0xFF9A5B1E)),
    RED("red", "绯红", Color(0xFF9B2C3A)),
    CYAN("cyan", "青碧", Color(0xFF136E7A)),
    GRAY("gray", "石墨", Color(0xFF4C5459));

    companion object {
        fun fromId(id: String?): AppPalette = entries.firstOrNull { it.id == id } ?: GREEN
    }
}

@Composable
fun LogCatTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    monetEnabled: Boolean = true,
    paletteId: String = "green",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = AppPalette.fromId(paletteId)

    val colorScheme = remember(darkTheme, monetEnabled, palette) {
        val monetAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        when {
            monetEnabled && monetAvailable ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> palette.darkScheme()
            else -> palette.lightScheme()
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

// ---------- 由种子色衍生配色 ----------

private fun AppPalette.lightScheme(): ColorScheme = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    primaryContainer = seed.blendWithLight(0.78f),
    onPrimaryContainer = seed.blendWithDark(0.62f),
    secondary = seed.shiftHue(-18f).desaturate(0.25f),
    tertiary = seed.shiftHue(28f),
    background = seed.blendWithLight(0.965f),
    onBackground = Color(0xFF1A1C1B),
    surface = seed.blendWithLight(0.975f),
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = seed.blendWithLight(0.885f),
    onSurfaceVariant = Color(0xFF424844),
    outline = seed.blendWithLight(0.62f),
    outlineVariant = seed.blendWithLight(0.82f),
    surfaceContainer = seed.blendWithLight(0.94f),
    surfaceContainerHigh = seed.blendWithLight(0.905f),
    surfaceContainerLow = seed.blendWithLight(0.955f),
)

private fun AppPalette.darkScheme(): ColorScheme = darkColorScheme(
    primary = seed.blendWithLight(0.52f),
    onPrimary = Color(0xFF00281A),
    primaryContainer = seed.blendWithDark(0.28f),
    onPrimaryContainer = seed.blendWithLight(0.82f),
    secondary = seed.shiftHue(-18f).desaturate(0.3f).blendWithLight(0.6f),
    tertiary = seed.shiftHue(28f).blendWithLight(0.62f),
    background = seed.blendWithDark(0.9f),
    onBackground = Color(0xFFE2E3E0),
    surface = seed.blendWithDark(0.9f),
    onSurface = Color(0xFFE2E3E0),
    surfaceVariant = seed.blendWithDark(0.74f),
    onSurfaceVariant = Color(0xFFC0C8C3),
    outline = seed.blendWithLight(0.42f),
    outlineVariant = seed.blendWithDark(0.66f),
    surfaceContainer = seed.blendWithDark(0.84f),
    surfaceContainerHigh = seed.blendWithDark(0.78f),
    surfaceContainerLow = seed.blendWithDark(0.87f),
)

// 极简 HSL 变换,避免为调色引入外部依赖
private fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, lightness)
    val delta = max - min
    val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val hue = when (max) {
        r -> ((g - b) / delta + if (g < b) 6f else 0f)
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } * 60f
    return floatArrayOf(hue, saturation, lightness)
}

private fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val light = l.coerceIn(0f, 1f)
    if (sat == 0f) return Color(light, light, light, alpha)
    val q = if (light < 0.5f) light * (1f + sat) else light + sat - light * sat
    val p = 2f * light - q
    fun channel(t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 0.5f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    val hNorm = hue / 360f
    return Color(channel(hNorm + 1f / 3f), channel(hNorm), channel(hNorm - 1f / 3f), alpha)
}

/** 与白色混合,amount 为白色的比例。 */
private fun Color.blendWithLight(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(red + (1f - red) * a, green + (1f - green) * a, blue + (1f - blue) * a, 1f)
}

/** 与黑色混合,amount 为黑色的比例。 */
private fun Color.blendWithDark(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(red * (1f - a), green * (1f - a), blue * (1f - a), 1f)
}

private fun Color.shiftHue(degrees: Float): Color {
    val (h, s, l) = toHsl()
    return hslToColor(h + degrees, s, l)
}

private fun Color.desaturate(factor: Float): Color {
    val (h, s, l) = toHsl()
    return hslToColor(h, s * (1f - factor.coerceIn(0f, 1f)), l)
}

/** 日志级别配色,跟随深浅色切换以保证对比度。 */
object LogColors {

    private val verboseLight = Color(0xFF6B7078)
    private val verboseDark = Color(0xFF9AA0A8)
    private val debugLight = Color(0xFF2563C7)
    private val debugDark = Color(0xFF7EAAFF)
    private val infoLight = Color(0xFF15813F)
    private val infoDark = Color(0xFF5CD08A)
    private val warnLight = Color(0xFF9A6A00)
    private val warnDark = Color(0xFFE9B949)
    private val errorLight = Color(0xFFC02A2A)
    private val errorDark = Color(0xFFFF8A80)

    @Composable
    fun of(level: LogLevel): Color {
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        return when (level) {
            LogLevel.VERBOSE -> if (dark) verboseDark else verboseLight
            LogLevel.DEBUG -> if (dark) debugDark else debugLight
            LogLevel.INFO -> if (dark) infoDark else infoLight
            LogLevel.WARN -> if (dark) warnDark else warnLight
            LogLevel.ERROR, LogLevel.FATAL, LogLevel.ASSERT -> if (dark) errorDark else errorLight
        }
    }

    private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
}
