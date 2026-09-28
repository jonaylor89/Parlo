package com.parlo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF1E5F74)
private val TealLight = Color(0xFF6FB3C4)
private val Coral = Color(0xFFE8735A)

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEAF2),
    onPrimaryContainer = Color(0xFF00363F),
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBD1),
    onSecondaryContainer = Color(0xFF3B0A02),
    tertiary = Color(0xFF5B8C5A),
    tertiaryContainer = Color(0xFFD7F0D4),
    onTertiaryContainer = Color(0xFF0F2A10),
    surface = Color(0xFFFBFAF6),
    surfaceVariant = Color(0xFFE5EBED),
    onSurfaceVariant = Color(0xFF3F4A4E),
)

private val DarkScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF0B4A5B),
    onPrimaryContainer = Color(0xFFCDEAF2),
    secondary = Color(0xFFF2A18C),
    onSecondary = Color(0xFF4A1508),
    secondaryContainer = Color(0xFF6B2A19),
    onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = Color(0xFF9CCB9B),
    tertiaryContainer = Color(0xFF2B4A2B),
    onTertiaryContainer = Color(0xFFD7F0D4),
    surface = Color(0xFF121517),
    surfaceVariant = Color(0xFF2A3336),
    onSurfaceVariant = Color(0xFFC0CBCF),
)

private val ParloShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val ParloTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(letterSpacing = 0.2.sp),
    )
}

/**
 * Parlo's own teal/coral palette by default. [dynamicColor] opts into Material You (wallpaper)
 * colours on Android 12+.
 */
@Composable
fun ParloTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, shapes = ParloShapes, typography = ParloTypography, content = content)
}
