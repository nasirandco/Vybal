package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

private val VybalDarkColorScheme = darkColorScheme(
    primary = VybalViolet,
    onPrimary = VybalOnViolet,
    primaryContainer = VybalVioletContainer,
    onPrimaryContainer = VybalLavender,
    secondary = VybalPurple,
    onSecondary = VybalOnViolet,
    secondaryContainer = VybalSurfaceHigh,
    onSecondaryContainer = VybalLavender,
    tertiary = VybalLavender,
    onTertiary = VybalObsidian,
    background = VybalObsidian,
    onBackground = VybalTextPrimary,
    surface = VybalSurfaceLow,
    onSurface = VybalTextPrimary,
    surfaceVariant = VybalSurface,
    onSurfaceVariant = VybalTextSecondary,
    surfaceContainerLowest = VybalSurfaceLowest,
    surfaceContainerLow = VybalSurfaceLow,
    surfaceContainer = VybalSurface,
    surfaceContainerHigh = VybalSurfaceHigh,
    surfaceContainerHighest = VybalSurfaceHighest,
    outline = VybalOutline,
    outlineVariant = VybalOutlineSubtle,
    error = VybalError,
    onError = VybalOnViolet,
    errorContainer = VybalErrorContainer,
    onErrorContainer = VybalTextPrimary
)

val VybalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object VybalSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val maxContentWidth = 600.dp
}

object VybalBrushes {
    val primaryAccent = Brush.linearGradient(
        colors = listOf(VybalViolet, VybalPurple)
    )

    val subtleAmbientGlow = Brush.verticalGradient(
        colors = listOf(
            VybalVioletContainer.copy(alpha = 0.42f),
            VybalObsidian
        )
    )

    val cardSheen = Brush.linearGradient(
        colors = listOf(
            VybalSurfaceHigh.copy(alpha = 0.85f),
            VybalSurface.copy(alpha = 0.92f)
        )
    )
}

@Composable
fun VybalTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VybalDarkColorScheme,
        typography = VybalTypography,
        shapes = VybalShapes,
        content = content
    )
}
