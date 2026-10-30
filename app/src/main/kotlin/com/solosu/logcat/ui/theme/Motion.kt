package com.solosu.logcat.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 统一动效体系。
 *
 * 之前各处的动画都直接用默认 spec:tween 默认 300ms + FastOutSlowInEasing,
 * 弹簧用 Compose 内置默认参数。同一界面里出现多种时长与曲线,切换时节奏不一致,
 * 观感是"每块各动各的"。这里把时长、缓动、弹簧收敛成一套常量,
 * 所有动画从这里取值。
 *
 * 时长依据 Material 3 运动建议:
 * - 微反馈(按压、选中)90~140ms:短到不打断操作
 * - 结构化变化(展开、滑入)220~280ms:能看清因果
 * - 页面级转场 240ms,再长会显得拖沓
 */
object Motion {

    const val Instant = 90
    const val Fast = 140
    const val Medium = 220
    const val Slow = 280
    const val Screen = 240

    /** 标准缓动:两端都留缓冲,适合绝大多数状态变化。 */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 加速缓动:仅用于退出,让消失比出现更快。 */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 减速缓动:仅用于进入。 */
    val Decelerate: Easing = LinearOutSlowInEasing

    fun <T> fast(reduced: Boolean): FiniteAnimationSpec<T> =
        tween(durationMillis = if (reduced) 0 else Fast, easing = Standard)

    fun <T> medium(reduced: Boolean): FiniteAnimationSpec<T> =
        tween(durationMillis = if (reduced) 0 else Medium, easing = Decelerate)

    fun <T> exit(reduced: Boolean): FiniteAnimationSpec<T> =
        tween(durationMillis = if (reduced) 0 else Fast, easing = Accelerate)

    fun <T> screenIn(reduced: Boolean): FiniteAnimationSpec<T> =
        tween(durationMillis = if (reduced) 0 else Screen, easing = Decelerate)

    fun <T> screenOut(reduced: Boolean): FiniteAnimationSpec<T> =
        tween(durationMillis = if (reduced) 0 else Screen - 60, easing = Accelerate)

    /** 有回弹的弹簧,用于按压回弹这类需要手感的位置。 */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/**
 * 无障碍:系统「移除动画」开关的 Compose 侧投影。
 *
 * Compose 不会自动遵守 Settings.Global.ANIMATOR_DURATION_SCALE —— 该设置只影响
 * 平台的 View 动画与窗口转场。用户把动画时长缩放设为 0 时,应用内的 tween/spring
 * 仍会照常播放,对晕动敏感的用户是实打实的困扰,所以这里主动读取并降级。
 *
 * 读取点收敛到 LogCatTheme 一次,通过 CompositionLocal 下发给所有动画。
 */
@Immutable
data class MotionPreference(val reduced: Boolean)

val LocalReduceMotion = compositionLocalOf { MotionPreference(reduced = false) }

/** 读取系统动画缩放;scale 为 0 视为用户要求移除动画。 */
@Composable
fun rememberMotionPreference(): MotionPreference {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        }.getOrDefault(1f)
        MotionPreference(reduced = scale == 0f)
    }
}

/** 便捷读取:当前是否应移除动画。 */
@Composable
fun reduceMotion(): Boolean = LocalReduceMotion.current.reduced
