package com.solosu.logcat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.solosu.logcat.ui.theme.Motion
import com.solosu.logcat.ui.theme.reduceMotion

/**
 * 带按压反馈的点击修饰符。
 *
 * 单纯的 clickable 只有涟漪,在大面积卡片上反馈偏弱(涟漪扩散慢、易被内容遮住)。
 * 这里叠加一个极小的缩放(1.0 -> 0.97)按压感,松手用弹簧回弹,
 * 让"按下去了"这件事在触觉之外也有视觉确认。
 *
 * 缩放幅度刻意压到 3%:幅度再大就会让列表在快速点击时产生抖动感。
 * 系统要求移除动画时不做缩放,只保留 clickable 本身。
 */
fun Modifier.pressableClick(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = null,
    pressScale: Float = 0.97f,
): Modifier = composed {
    if (!enabled) return@composed this
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = reduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressScale else 1f,
        animationSpec = Motion.bouncy(),
        label = "press-scale",
    )
    this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .semantics { if (role != null) this.role = role }
}

/**
 * 选中态背景色过渡。
 *
 * chip/选项从"选中 -> 未选中"直接切色会让界面显得跳。这里用 animateColorAsState
 * 做 140ms 过渡;关闭动画时 spec 时长为 0,颜色瞬时切换。
 */
@Composable
fun animatedSelectionColor(selected: Boolean, selectedColor: Color, normalColor: Color): Color {
    val reduced = reduceMotion()
    val color by animateColorAsState(
        targetValue = if (selected) selectedColor else normalColor,
        animationSpec = Motion.fast(reduced),
        label = "selection-color",
    )
    return color
}

/**
 * 可点击的容器:把「shape + 背景 + 按压反馈 + 语义」打包,
 * 避免每个可点击 Box 都重复写一遍 clip/background/semantics。
 */
@Composable
fun PressableBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = Color.Transparent,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    // 顺序很重要:先 clip 再 background,最后才应用 pressableClick 的缩放。
    // 若把 scale 放在 clip 之前,缩小后圆角边缘会露出未裁剪的背景。
    Box(
        modifier = modifier
            .pressableClick(onClick = onClick, enabled = enabled, role = role)
            .clip(shape)
            .background(color),
        contentAlignment = contentAlignment,
        content = content,
    )
}
