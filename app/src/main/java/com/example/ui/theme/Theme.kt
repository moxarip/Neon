package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NeonRinkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepVoid,
    primaryContainer = CyanMuted,
    onPrimaryContainer = CrispWhite,
    secondary = NeonViolet,
    onSecondary = CrispWhite,
    secondaryContainer = VioletDeep,
    onSecondaryContainer = CrispWhite,
    tertiary = OverdriveOrange,
    onTertiary = DeepVoid,
    background = DeepVoid,
    onBackground = CrispWhite,
    surface = DarkArena,
    onSurface = CrispWhite,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = SurfaceElevated,
    outline = OutlineNeon,
    error = DangerRed,
    onError = CrispWhite
)

private val NeonRinkShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun NeonRinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonRinkColorScheme,
        typography = NeonRinkTypography,
        shapes = NeonRinkShapes,
        content = content
    )
}
