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

// StudyMate AI palette: calm indigo with a warm amber accent
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF0B1B8F),
    primaryContainer = Color(0xFF2B3DB0),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFFFB95C),
    onSecondary = Color(0xFF462B00),
    secondaryContainer = Color(0xFF633F00),
    onSecondaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF12131B),
    onBackground = Color(0xFFE4E1EC),
    surface = Color(0xFF12131B),
    onSurface = Color(0xFFE4E1EC),
    surfaceVariant = Color(0xFF2A2C3C),
    onSurfaceVariant = Color(0xFFD7D9EC)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4457D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2C1800),
    background = Color(0xFFFAF9FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFAF9FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE5E7F5),
    onSurfaceVariant = Color(0xFF3A3D4F)
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
