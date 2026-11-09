package com.rainplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 一套配色。所有界面只读这 8 个语义色，换主题时整站一起变。
 */
data class RainPalette(
    /** 页面底色（最底层）。 */
    val bg: Color,
    /** 底色上方的渐变高光，用于做出「有光从上面打下来」的层次。 */
    val bgTop: Color,
    /** 卡片 / 面板底色。 */
    val surface: Color,
    /** 卡片上的次级色块（选中态、缩略图占位）。 */
    val surfaceHigh: Color,
    /** 强调色：按钮、进度条、选中标识。 */
    val accent: Color,
    /** 强调色的低饱和版本，用于容器底色。 */
    val accentDim: Color,
    val text: Color,
    val textDim: Color,
    /** 毛玻璃层的叠加色（半透明白）。 */
    val glassTint: Color = Color(0x1FFFFFFF),
    /** 毛玻璃层的描边色。 */
    val glassBorder: Color = Color(0x26FFFFFF),
)

/**
 * 可选主题。key 用于持久化，改动时不要改 key。
 */
enum class AppTheme(val key: String, val displayName: String, val palette: RainPalette) {

    /** 极夜蓝：默认，冷色调，最中性。 */
    MIDNIGHT(
        "midnight", "极夜蓝",
        RainPalette(
            bg = Color(0xFF0B0D12),
            bgTop = Color(0xFF161B28),
            surface = Color(0xFF151922),
            surfaceHigh = Color(0xFF1E2430),
            accent = Color(0xFF6C8CFF),
            accentDim = Color(0xFF2A3550),
            text = Color(0xFFF2F4F8),
            textDim = Color(0xFF9BA3B4),
        ),
    ),

    /** 石墨灰：纯灰阶，夜间不刺眼，OLED 省电。 */
    GRAPHITE(
        "graphite", "石墨灰",
        RainPalette(
            bg = Color(0xFF08090A),
            bgTop = Color(0xFF15171A),
            surface = Color(0xFF121417),
            surfaceHigh = Color(0xFF1C1F24),
            accent = Color(0xFFD5DAE3),
            accentDim = Color(0xFF2C3038),
            text = Color(0xFFF4F5F7),
            textDim = Color(0xFF9AA0AA),
        ),
    ),

    /** 深海青：青绿色点缀，适合看纪录片 / 剧集。 */
    OCEAN(
        "ocean", "深海青",
        RainPalette(
            bg = Color(0xFF04100F),
            bgTop = Color(0xFF0B2320),
            surface = Color(0xFF0A1A18),
            surfaceHigh = Color(0xFF102623),
            accent = Color(0xFF35D0C0),
            accentDim = Color(0xFF0F3B36),
            text = Color(0xFFEFFAF8),
            textDim = Color(0xFF8FAFAA),
        ),
    ),

    /** 暮光紫：偏影音厅氛围。 */
    VIOLET(
        "violet", "暮光紫",
        RainPalette(
            bg = Color(0xFF0B0714),
            bgTop = Color(0xFF1B1030),
            surface = Color(0xFF150E24),
            surfaceHigh = Color(0xFF201634),
            accent = Color(0xFFB57BFF),
            accentDim = Color(0xFF35224F),
            text = Color(0xFFF6F1FF),
            textDim = Color(0xFFA99BBB),
        ),
    ),

    /** 暖琥珀：暖色调，白天看更舒服。 */
    AMBER(
        "amber", "暖琥珀",
        RainPalette(
            bg = Color(0xFF100C06),
            bgTop = Color(0xFF261A0C),
            surface = Color(0xFF1A1309),
            surfaceHigh = Color(0xFF261B0E),
            accent = Color(0xFFF0A93B),
            accentDim = Color(0xFF42300F),
            text = Color(0xFFFDF7EC),
            textDim = Color(0xFFB5A78F),
        ),
    ),

    /** 玫瑰红：偏柔和的粉色强调，不做成刺眼的大红。 */
    ROSE(
        "rose", "玫瑰红",
        RainPalette(
            bg = Color(0xFF12070B),
            bgTop = Color(0xFF2A1018),
            surface = Color(0xFF1B0C12),
            surfaceHigh = Color(0xFF28131B),
            accent = Color(0xFFFF6B8A),
            accentDim = Color(0xFF4A1B27),
            text = Color(0xFFFFF1F4),
            textDim = Color(0xFFBF9AA4),
        ),
    ),

    /** 松林绿：低饱和绿，长时间看眼睛负担小。 */
    FOREST(
        "forest", "松林绿",
        RainPalette(
            bg = Color(0xFF050F0A),
            bgTop = Color(0xFF0F2418),
            surface = Color(0xFF0A1811),
            surfaceHigh = Color(0xFF12251A),
            accent = Color(0xFF5FD08A),
            accentDim = Color(0xFF123A24),
            text = Color(0xFFF0FBF4),
            textDim = Color(0xFF93B2A2),
        ),
    );

    companion object {
        fun of(key: String?): AppTheme = entries.firstOrNull { it.key == key } ?: MIDNIGHT
    }
}

/**
 * 主题与毛玻璃开关的运行时状态。
 *
 * 用全局单例而不是 CompositionLocal 保存「选了哪个主题」，是因为主题需要在
 * Activity 重建、播放页独立 Compose 树、TV/手机两套入口之间共享。
 */
object ThemeState {

    var theme by mutableStateOf(AppTheme.MIDNIGHT)

    /** 毛玻璃总开关：低端设备（Android 6/7 的老盒子）关掉可以明显减少合成开销。 */
    var glass by mutableStateOf(true)

    fun apply(theme: AppTheme, glass: Boolean) {
        this.theme = theme
        this.glass = glass
    }
}

val LocalRainPalette = compositionLocalOf { AppTheme.MIDNIGHT.palette }
val LocalGlassEnabled = compositionLocalOf { true }

/** 当前主题的配色（非 Composable 环境请用 ThemeState.theme.palette）。 */
val rainPalette: RainPalette
    @Composable @ReadOnlyComposable get() = LocalRainPalette.current

/*
 * 下面这些名字是历史包袱：早期所有界面直接引用顶层常量。
 * 改成 @Composable 属性 getter 之后，180+ 处调用点一行都不用动，
 * 但颜色会跟着当前主题走。
 */
val RainPlayerBg: Color
    @Composable @ReadOnlyComposable get() = LocalRainPalette.current.bg

val RainPlayerBgTop: Color
    @Composable @ReadOnlyComposable get() = LocalRainPalette.current.bgTop

val RainPlayerSurface: Color
    @Composable @ReadOnlyComposable get() = LocalRainPalette.current.surface

val RainPlayerSurfaceHigh: Color
    @Composable @ReadOnlyComposable get() = LocalRainPalette.current.surfaceHigh

val RainPlayerAccent: Color
    @Composable @ReadOnlyComposable get() = LocalRainPalette.current.accent

val RainPlayerAccentDim: Color
    @Composable @ReadOnlyComposable get() = LocalRainPalette.current.accentDim

val RainPlayerText: Color
    @Composable @ReadOnlyComposable get() = LocalRainPalette.current.text

val RainPlayerTextDim: Color
    @Composable @ReadOnlyComposable get() = LocalRainPalette.current.textDim

val RainPlayerGlassTint: Color
    @Composable @ReadOnlyComposable get() = LocalRainPalette.current.glassTint

val RainPlayerGlassBorder: Color
    @Composable @ReadOnlyComposable get() = LocalRainPalette.current.glassBorder

/** 语义色，不随主题变化。 */
val RainPlayerRed = Color(0xFFE5484D)
val RainPlayerGreen = Color(0xFF46C27A)
/** 评分用的金色。 */
val RainPlayerGold = Color(0xFFF5C451)

private fun schemeOf(p: RainPalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = Color.Black,
    primaryContainer = p.accentDim,
    onPrimaryContainer = p.text,
    secondary = p.accent,
    background = p.bg,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = p.surfaceHigh,
    onSurfaceVariant = p.textDim,
    error = RainPlayerRed,
)

@Composable
fun RainPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = ThemeState.theme.palette
    CompositionLocalProvider(
        LocalRainPalette provides palette,
        LocalGlassEnabled provides ThemeState.glass,
    ) {
        MaterialTheme(
            colorScheme = schemeOf(palette),
            typography = MaterialTheme.typography.copy(
                titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                bodyMedium = TextStyle(fontSize = 14.sp),
                labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            ),
            content = content,
        )
    }
}
