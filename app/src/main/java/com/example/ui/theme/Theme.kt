package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CircadiaDarkColorScheme = darkColorScheme(
    primary = WarmAmber400,
    onPrimary = MidnightSlate950,
    primaryContainer = WarmAmber900,
    onPrimaryContainer = WarmAmber100,
    secondary = SoftTeal300,
    onSecondary = MidnightSlate950,
    secondaryContainer = SoftTeal900,
    onSecondaryContainer = SoftTeal100,
    tertiary = SleepIndigo400,
    onTertiary = MidnightSlate950,
    tertiaryContainer = SleepIndigo900,
    onTertiaryContainer = MidnightSlate100,
    background = MidnightSlate950,
    onBackground = MidnightSlate100,
    surface = MidnightSlate900,
    onSurface = MidnightSlate100,
    surfaceVariant = MidnightSlate850,
    onSurfaceVariant = MidnightSlate400,
    surfaceContainerLowest = MidnightSlate950,
    surfaceContainerLow = MidnightSlate900,
    surfaceContainer = MidnightSlate850,
    surfaceContainerHigh = MidnightSlate800,
    surfaceContainerHighest = ColorTokens.ElevatedSurface,
    outline = MidnightSlate700,
    outlineVariant = ColorTokens.SubtleDivider,
    error = SeverityCriticalCrimson,
    onError = MidnightSlate950,
    errorContainer = ColorTokens.ErrorSurface,
    onErrorContainer = ColorTokens.ErrorText
)

private object ColorTokens {
    val ElevatedSurface = androidx.compose.ui.graphics.Color(0xFF243247)
    val SubtleDivider = androidx.compose.ui.graphics.Color(0xFF1E293B)
    val ErrorSurface = androidx.compose.ui.graphics.Color(0xFF3F161A)
    val ErrorText = androidx.compose.ui.graphics.Color(0xFFFECACA)
}

val CircadiaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CircadiaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CircadiaDarkColorScheme,
        typography = CircadiaTypography,
        shapes = CircadiaShapes,
        content = content
    )
}
