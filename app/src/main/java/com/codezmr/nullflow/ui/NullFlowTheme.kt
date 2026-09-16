package com.codezmr.nullflow.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * NullFlow palette.
 *
 * The app is ALWAYS dark (it's a "rest mode" app). The psychological hook is
 * the BACKGROUND shift: bright/neutral when OFF → deep dark (#121212) when
 * the shield is ACTIVE. That's driven by animateColorAsState in the screen,
 * not by the color scheme.
 */
private val NullFlowDarkColors = darkColorScheme(
    primary = Color(0xFF4F8CFF),
    onPrimary = Color(0xFF001028),
    primaryContainer = Color(0xFF1A2C4E),
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Color(0xFF9DB2CE),
    onSecondary = Color(0xFF0E1D33),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF1A1D24),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF232833),
    onSurfaceVariant = Color(0xFFB9C2D0),
    outline = Color(0xFF3A4150),
    error = Color(0xFFFF5470),
    onError = Color(0xFF40000A)
)

val NullFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NullFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NullFlowDarkColors,
        shapes = NullFlowShapes,
        content = content
    )
}
