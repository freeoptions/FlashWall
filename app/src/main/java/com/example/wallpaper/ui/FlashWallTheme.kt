package com.example.wallpaper.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** FlashWall 的浅色冰蓝视觉基线，与 AutoSend 保持同一套明亮层级。 */
object FlashWallColors {
    val background = Color(0xFFF1F2FF)
    val ink = Color(0xFF1D2A50)
    val muted = Color(0xFF667399)
    val blue = Color(0xFF4868AE)
    val blueTint = Color(0xFFDDE5FF)
    val hero = Color(0xFFB7C8FA)
    val line = Color(0xFFE3E6F5)
}

private val FlashWallLightColors = lightColorScheme(
    primary = FlashWallColors.blue,
    onPrimary = Color.White,
    primaryContainer = FlashWallColors.blueTint,
    onPrimaryContainer = FlashWallColors.ink,
    secondary = Color(0xFFB9C8FF),
    onSecondary = FlashWallColors.ink,
    secondaryContainer = FlashWallColors.blueTint,
    onSecondaryContainer = FlashWallColors.ink,
    tertiary = Color(0xFF6D7DBA),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6EAFF),
    onTertiaryContainer = FlashWallColors.ink,
    background = FlashWallColors.background,
    onBackground = FlashWallColors.ink,
    surface = Color.White,
    onSurface = FlashWallColors.ink,
    surfaceVariant = Color(0xFFE9ECF8),
    onSurfaceVariant = FlashWallColors.muted,
    outline = Color(0xFF8993B2),
    outlineVariant = FlashWallColors.line,
    error = Color(0xFFB45467),
    errorContainer = Color(0xFFFFE3E8),
    onError = Color.White,
    onErrorContainer = Color(0xFF8E3346)
)

private val FlashWallTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 36.sp
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp
        ),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        ),
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
        bodySmall = base.bodySmall.copy(fontFamily = FontFamily.SansSerif)
    )
}

private val FlashWallShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FlashWallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlashWallLightColors,
        typography = FlashWallTypography,
        shapes = FlashWallShapes,
        content = content
    )
}
