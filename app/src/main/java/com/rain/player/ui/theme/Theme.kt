package com.rain.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RainBg = Color(0xFF0B0D12)
val RainSurface = Color(0xFF151922)
val RainSurfaceHigh = Color(0xFF1E2430)
val RainAccent = Color(0xFF6C8CFF)
val RainAccentDim = Color(0xFF2A3550)
val RainText = Color(0xFFF2F4F8)
val RainTextDim = Color(0xFF9BA3B4)
val RainRed = Color(0xFFE5484D)
val RainGreen = Color(0xFF46C27A)

private val RainColors = darkColorScheme(
    primary = RainAccent,
    onPrimary = Color.White,
    primaryContainer = RainAccentDim,
    onPrimaryContainer = RainText,
    secondary = Color(0xFF8FA6FF),
    background = RainBg,
    onBackground = RainText,
    surface = RainSurface,
    onSurface = RainText,
    surfaceVariant = RainSurfaceHigh,
    onSurfaceVariant = RainTextDim,
    error = RainRed,
)

@Composable
fun RainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = RainColors,
        typography = MaterialTheme.typography.copy(
            titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
            bodyMedium = TextStyle(fontSize = 14.sp),
            labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
