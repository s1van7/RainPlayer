package com.rainplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
    /** 浅色模式标记：少数地方要按明暗翻转（描边、遮罩、按钮底色）。 */
    val isLight: Boolean = false,
)

/** 明暗模式。叠在 7 套主题之上，主题决定色相，模式决定明暗。 */
enum class ThemeMode(val key: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    ;

    companion object {
        fun of(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * 可选主题。key 用于持久化，改动时不要改 key。
 *
 * 配色基调：整体不用纯黑。底色抬到 #12~#1C 这一档，配一点冷蓝或主题色倾向，
 * 长时间看不闷，也能让毛玻璃的高光和描边透出来。
 *
 * [lightAccent] 是浅色模式下用的强调色：深色主题的强调色普遍偏亮，
 * 直接放到白底上会糊，所以每套单独压一档饱和度与亮度。
 */
enum class AppTheme(
    val key: String,
    val displayName: String,
    val palette: RainPalette,
    val lightAccent: Color,
) {

    /** 极夜蓝：默认，冷色调，接近夜空蓝而不是纯黑。 */
    MIDNIGHT(
        "midnight", "极夜蓝",
        RainPalette(
            bg = Color(0xFF121A24),
            bgTop = Color(0xFF1E2B3B),
            surface = Color(0xFF1A2432),
            surfaceHigh = Color(0xFF243244),
            accent = Color(0xFF6C9BFF),
            accentDim = Color(0xFF2E4260),
            text = Color(0xFFF3F6FB),
            textDim = Color(0xFFA4B0C2),
        ),
        Color(0xFF2F6BE0),
    ),

    /** 石墨灰：中性灰阶，比纯黑柔和，夜间不刺眼。 */
    GRAPHITE(
        "graphite", "石墨灰",
        RainPalette(
            bg = Color(0xFF16181C),
            bgTop = Color(0xFF23262C),
            surface = Color(0xFF1E2126),
            surfaceHigh = Color(0xFF2A2E35),
            accent = Color(0xFFD5DAE3),
            accentDim = Color(0xFF383D45),
            text = Color(0xFFF4F5F7),
            textDim = Color(0xFFA3A9B4),
        ),
        Color(0xFF4A5260),
    ),

    /** 深海青：青绿色点缀，适合看纪录片 / 剧集。 */
    OCEAN(
        "ocean", "深海青",
        RainPalette(
            bg = Color(0xFF0F2024),
            bgTop = Color(0xFF193A40),
            surface = Color(0xFF162C31),
            surfaceHigh = Color(0xFF1F3C42),
            accent = Color(0xFF3FD6C4),
            accentDim = Color(0xFF14453F),
            text = Color(0xFFEFFAF8),
            textDim = Color(0xFF9CBDB7),
        ),
        Color(0xFF0E9C8A),
    ),

    /** 暮光紫：偏影音厅氛围。 */
    VIOLET(
        "violet", "暮光紫",
        RainPalette(
            bg = Color(0xFF1A1326),
            bgTop = Color(0xFF2C1D42),
            surface = Color(0xFF231831),
            surfaceHigh = Color(0xFF302343),
            accent = Color(0xFFBE8CFF),
            accentDim = Color(0xFF3F2A5C),
            text = Color(0xFFF7F2FF),
            textDim = Color(0xFFB2A4C6),
        ),
        Color(0xFF7C4DD6),
    ),

    /** 暖琥珀：暖色调，白天看更舒服。 */
    AMBER(
        "amber", "暖琥珀",
        RainPalette(
            bg = Color(0xFF1D1610),
            bgTop = Color(0xFF322417),
            surface = Color(0xFF261C13),
            surfaceHigh = Color(0xFF35281A),
            accent = Color(0xFFF5B34A),
            accentDim = Color(0xFF4C3717),
            text = Color(0xFFFDF8EE),
            textDim = Color(0xFFC0B199),
        ),
        Color(0xFFB0730F),
    ),

    /** 玫瑰红：偏柔和的粉色强调，不做成刺眼的大红。 */
    ROSE(
        "rose", "玫瑰红",
        RainPalette(
            bg = Color(0xFF21141B),
            bgTop = Color(0xFF361F29),
            surface = Color(0xFF2A1820),
            surfaceHigh = Color(0xFF38222D),
            accent = Color(0xFFFF7E9B),
            accentDim = Color(0xFF52222F),
            text = Color(0xFFFFF2F5),
            textDim = Color(0xFFC8A4AE),
        ),
        Color(0xFFD63F63),
    ),

    /** 松林绿：低饱和绿，长时间看眼睛负担小。 */
    FOREST(
        "forest", "松林绿",
        RainPalette(
            bg = Color(0xFF101F17),
            bgTop = Color(0xFF193322),
            surface = Color(0xFF15271D),
            surfaceHigh = Color(0xFF1E3628),
            accent = Color(0xFF6CDB96),
            accentDim = Color(0xFF17452A),
            text = Color(0xFFF1FBF5),
            textDim = Color(0xFFA0BFAF),
        ),
        Color(0xFF1F9257),
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

    /** 明暗模式：跟随系统 / 强制浅色 / 强制深色。 */
    var mode by mutableStateOf(ThemeMode.SYSTEM)

    /** 毛玻璃总开关：低端设备（Android 6/7 的老盒子）关掉可以明显减少合成开销。 */
    var glass by mutableStateOf(true)

    fun apply(theme: AppTheme, glass: Boolean, mode: ThemeMode = ThemeMode.SYSTEM) {
        this.theme = theme
        this.glass = glass
        this.mode = mode
    }

    /** 当前是否深色（跟随系统时看系统设置）。 */
    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
}

/**
 * 浅色变体：底色走中性灰白，强调色用该主题压暗过的一档，
 * 强调容器色是强调色兑白，保证在白底上仍能看出层次。
 */
fun AppTheme.lightPalette(): RainPalette = RainPalette(
    bg = Color(0xFFF4F5F9),
    bgTop = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE8EBF1),
    accent = lightAccent,
    accentDim = lerp(Color.White, lightAccent, 0.16f),
    text = Color(0xFF171B22),
    textDim = Color(0xFF6A7280),
    glassTint = Color(0xE0FFFFFF),
    glassBorder = Color(0x1F000000),
    isLight = true,
)

/** 按当前模式解析出实际要用的配色。 */
fun AppTheme.paletteFor(systemDark: Boolean): RainPalette =
    if (ThemeState.isDark(systemDark)) palette else lightPalette()

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

/** 半透明填充：浅色下用淡黑，深色下用淡白。 */
val RainPlayerFill: Color
    @Composable @ReadOnlyComposable get() =
        if (LocalRainPalette.current.isLight) Color(0x14000000) else Color(0x1FFFFFFF)

/** 细分隔线 / 描边。 */
val RainPlayerStroke: Color
    @Composable @ReadOnlyComposable get() =
        if (LocalRainPalette.current.isLight) Color(0x1A000000) else Color(0x14FFFFFF)

/** 语义色，不随主题变化。 */
val RainPlayerRed = Color(0xFFE5484D)
val RainPlayerGreen = Color(0xFF46C27A)
/** 评分用的金色。 */
val RainPlayerGold = Color(0xFFF5C451)

private fun schemeOf(p: RainPalette) = if (p.isLight) {
    lightColorScheme(
        primary = p.accent,
        onPrimary = Color.White,
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
} else {
    darkColorScheme(
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
}

@Composable
fun RainPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = ThemeState.theme.paletteFor(darkTheme)
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
