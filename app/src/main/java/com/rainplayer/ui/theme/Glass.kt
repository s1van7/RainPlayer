package com.rainplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 毛玻璃工具集。
 *
 * Compose 的 `Modifier.blur` 只在 Android 12(API 31) 以上真正生效，低于该版本会被忽略。
 * 为了在 Android 5.0~11 的老设备上也有「磨砂」观感，这里用
 * 「半透明白叠加 + 高光渐变 + 1dp 亮边」三层模拟，视觉接近真实高斯模糊，
 * 且不增加任何渲染成本（纯 draw，无离屏缓冲）。
 */

/** 页面级背景：底色 + 顶部径向高光，让毛玻璃层有东西可以「透」。 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = rainPalette
    Box(
        modifier
            .fillMaxSize()
            .background(p.bg)
            .background(
                Brush.verticalGradient(
                    0f to p.bgTop.copy(alpha = 0.95f),
                    0.45f to p.bg,
                    1f to p.bg,
                )
            )
            // 顶部再压一层主题色薄雾：毛玻璃面板浮在上面时才有颜色可「透」
            .background(
                Brush.verticalGradient(
                    0f to p.accent.copy(alpha = if (p.isLight) 0.10f else 0.16f),
                    0.32f to Color.Transparent,
                )
            ),
        content = content,
    )
}

/**
 * 毛玻璃面板。
 *
 * @param level 1=普通卡片 2=浮层（弹窗/菜单）3=高亮（选中/播放控件）
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    level: Int = 1,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = rainPalette
    val enabled = LocalGlassEnabled.current
    val light = p.isLight

    // 底色压得越低、白色高光给得越足，「磨砂玻璃」的通透感越强。
    // 深色主题靠半透明面板 + 白色高光，浅色主题靠白色半透明 + 淡黑描边。
    val baseAlpha = when (level) {
        3 -> 0.58f
        2 -> 0.46f
        else -> 0.34f
    }
    val tintTop = when (level) {
        3 -> 0.26f
        2 -> 0.20f
        else -> 0.13f
    }
    val borderAlpha = when (level) {
        3 -> 0.36f
        2 -> 0.28f
        else -> 0.18f
    }

    val fill = when {
        !enabled -> if (light) Color.White else p.surface
        light -> Color.White.copy(alpha = if (level >= 2) 0.82f else 0.66f)
        else -> p.surface.copy(alpha = baseAlpha)
    }
    val sheen = if (enabled) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = tintTop),
            0.55f to Color.White.copy(alpha = tintTop * 0.22f),
            1f to Color.White.copy(alpha = 0f),
        )
    } else {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val edge = when {
        !enabled -> Color.Transparent
        light -> Color.Black.copy(alpha = borderAlpha * 0.55f)
        else -> Color.White.copy(alpha = borderAlpha)
    }

    Box(
        modifier
            .clip(shape)
            .background(fill)
            .background(sheen)
            .border(
                width = if (enabled) borderWidth else 0.dp,
                color = edge,
                shape = shape,
            ),
        content = content,
    )
}

/** 给背景图加模糊（仅 API 31+ 生效，低版本自动忽略）。 */
fun Modifier.softBlur(radius: Dp = 24.dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this

/**
 * 海报 / 剧照后面的压暗遮罩。毛玻璃层需要「有东西透过来但又不抢眼」，
 * 直接盖一层从透明到 80% 黑的竖向渐变效果最好。
 */
@Composable
fun scrimBrush(strength: Float = 0.82f): Brush {
    val p = rainPalette
    return Brush.verticalGradient(
        0f to Color.Transparent,
        0.35f to p.bg.copy(alpha = 0.35f * strength),
        0.72f to p.bg.copy(alpha = 0.86f * strength),
        1f to p.bg,
    )
}

/** 横向压暗（左侧放文字时用）。 */
@Composable
fun sideScrimBrush(strength: Float = 0.9f): Brush {
    val p = rainPalette
    return Brush.horizontalGradient(
        0f to p.bg.copy(alpha = 0.95f * strength),
        0.55f to p.bg.copy(alpha = 0.45f * strength),
        1f to Color.Transparent,
    )
}
