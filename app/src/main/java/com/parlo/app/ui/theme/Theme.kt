package com.parlo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Teal = Color(0xFF1E5F74)
private val TealLight = Color(0xFF6FB3C4)
private val Coral = Color(0xFFE8735A)

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEAF2),
    secondary = Coral,
    tertiary = Color(0xFF5B8C5A),
)

private val DarkScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF0B4A5B),
    secondary = Color(0xFFF2A18C),
    tertiary = Color(0xFF9CCB9B),
)

@Composable
fun ParloTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
