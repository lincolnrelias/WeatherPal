package com.example.weatherpal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF226858),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0EDE5),
        onPrimaryContainer = Color(0xFF174D40),
        secondary = Color(0xFF5D756C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8EEE7),
        onSecondaryContainer = Color(0xFF354B40),
        tertiary = Color(0xFF8B6027),
        tertiaryContainer = Color(0xFFFAE9CC),
        onTertiaryContainer = Color(0xFF644316),
        background = Color(0xFFF5F6F1),
        onBackground = Color(0xFF203832),
        surface = Color(0xFFFCFDF9),
        onSurface = Color(0xFF203832),
        surfaceVariant = Color(0xFFEAEDE5),
        onSurfaceVariant = Color(0xFF627169),
        surfaceContainer = Color(0xFFEDF0E8),
        surfaceContainerLow = Color(0xFFF0F2EB),
        surfaceContainerHigh = Color(0xFFE5EAE1),
        outline = Color(0xFF7D8B81),
        outlineVariant = Color(0xFFDCE2D7),
        error = Color(0xFF9D4038),
        errorContainer = Color(0xFFFCE8E3),
        onErrorContainer = Color(0xFF713027),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFA7D6BB),
        onPrimary = Color(0xFF103B2E),
        primaryContainer = Color(0xFF244A3B),
        onPrimaryContainer = Color(0xFFD5EDDD),
        secondary = Color(0xFFB3C9B9),
        secondaryContainer = Color(0xFF2D4035),
        onSecondaryContainer = Color(0xFFD2E3D5),
        tertiary = Color(0xFFE9C38A),
        tertiaryContainer = Color(0xFF4F3F25),
        onTertiaryContainer = Color(0xFFF4D9AE),
        background = Color(0xFF121D19),
        onBackground = Color(0xFFE4ECE3),
        surface = Color(0xFF1A2720),
        onSurface = Color(0xFFE4ECE3),
        surfaceVariant = Color(0xFF2E3B32),
        onSurfaceVariant = Color(0xFFB3BFB3),
        surfaceContainer = Color(0xFF202E25),
        surfaceContainerLow = Color(0xFF18251E),
        surfaceContainerHigh = Color(0xFF2B3A30),
        outline = Color(0xFF879689),
        outlineVariant = Color(0xFF3B4A3E),
        error = Color(0xFFFFB4A7),
        errorContainer = Color(0xFF522E29),
        onErrorContainer = Color(0xFFF9DCD4),
    )

private val AppTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 58.sp,
                lineHeight = 62.sp,
                letterSpacing = (-2).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 42.sp,
                lineHeight = 47.sp,
                letterSpacing = (-1).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.6).sp,
            ),
        headlineMedium =
            TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 36.sp),
        headlineSmall =
            TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 32.sp),
        titleLarge =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                lineHeight = 27.sp,
                letterSpacing = (-0.3).sp,
            ),
        titleMedium =
            TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall =
            TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge =
            TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium =
            TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                letterSpacing = 1.sp,
            ),
    )

@Composable
fun WeatherPalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes =
            Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
        content = content,
    )
}
