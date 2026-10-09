package com.manoj.lofi4a.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// StudyMate AI palette (from the Stitch "Intelligent Focus" design):
// deep blue #2563EB, violet #7C3AED, light background #F8FAFC
val StudyBlue = Color(0xFF2563EB)
val StudyViolet = Color(0xFF7C3AED)
val StudyGreen = Color(0xFF007D55)

private val LightColors = lightColorScheme(
    primary = StudyBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE1FF),
    onPrimaryContainer = Color(0xFF00174B),
    secondary = StudyViolet,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF25005A),
    tertiary = Color(0xFF006242),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBDFFDB),
    onTertiaryContainer = Color(0xFF002113),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF131B2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF131B2E),
    surfaceVariant = Color(0xFFEAEDFF),
    onSurfaceVariant = Color(0xFF434655),
    outline = Color(0xFF737686),
    outlineVariant = Color(0xFFC3C6D7),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF2563EB),
    onPrimaryContainer = Color(0xFFEEEFFF),
    secondary = Color(0xFFD2BBFF),
    onSecondary = Color(0xFF3F008E),
    secondaryContainer = Color(0xFF5A00C6),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFF4EDEA3),
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF005236),
    onTertiaryContainer = Color(0xFFBDFFDB),
    background = Color(0xFF0F1420),
    onBackground = Color(0xFFE2E7FF),
    surface = Color(0xFF171C2B),
    onSurface = Color(0xFFE2E7FF),
    surfaceVariant = Color(0xFF283044),
    onSurfaceVariant = Color(0xFFC3C6D7),
    outline = Color(0xFF8D90A0),
    outlineVariant = Color(0xFF434655),
    error = Color(0xFFFFB4AB)
)

private val StudyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun LoFiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = StudyShapes,
        content = content
    )
}
