package com.example.weatherpal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun WeatherPalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF8DD4C5))
            else lightColorScheme(primary = Color(0xFF006B5D)),
        content = content,
    )
}
