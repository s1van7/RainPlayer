package com.rainplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RainPlayerBg = Color(0xFF0B0D12)
val RainPlayerSurface = Color(0xFF151922)
val RainPlayerSurfaceHigh = Color(0xFF1E2430)
val RainPlayerAccent = Color(0xFF6C8CFF)
val RainPlayerAccentDim = Color(0xFF2A3550)
val RainPlayerText = Color(0xFFF2F4F8)
val RainPlayerTextDim = Color(0xFF9BA3B4)
val RainPlayerRed = Color(0xFFE5484D)
val RainPlayerGreen = Color(0xFF46C27A)

private val RainPlayerColors = darkColorScheme(
    primary = RainPlayerAccent,
    onPrimary = Color.White,
    primaryContainer = RainPlayerAccentDim,
    onPrimaryContainer = RainPlayerText,
    secondary = Color(0xFF8FA6FF),
    background = RainPlayerBg,
    onBackground = RainPlayerText,
    surface = RainPlayerSurface,
    onSurface = RainPlayerText,
    surfaceVariant = RainPlayerSurfaceHigh,
    onSurfaceVariant = RainPlayerTextDim,
    error = RainPlayerRed,
)

@Composable
fun RainPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = RainPlayerColors,
        typography = MaterialTheme.typography.copy(
            titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
            bodyMedium = TextStyle(fontSize = 14.sp),
            labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
