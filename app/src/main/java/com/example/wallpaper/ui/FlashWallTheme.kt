package com.example.wallpaper.ui

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

private val FlashWallFallbackColors = lightColorScheme(
    primary = Color(0xFF35618E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF526070),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4F7),
    onSecondaryContainer = Color(0xFF0F1D2A),
    tertiary = Color(0xFF6A5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E3EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7CF)
)

private val FlashWallTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = Typography().titleLarge.copy(
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = Typography().titleMedium.copy(
        fontSize = 16.sp,
        lineHeight = 22.sp
    )
)

private val FlashWallShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FlashWallTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context)
    } else {
        FlashWallFallbackColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FlashWallTypography,
        shapes = FlashWallShapes,
        content = content
    )
}
