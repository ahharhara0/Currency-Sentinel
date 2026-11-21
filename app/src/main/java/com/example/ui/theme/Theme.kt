package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ForensicDarkColorScheme = darkColorScheme(
    primary = OpticalCyan,
    onPrimary = ObsidianBg,
    primaryContainer = OpticalCyanDim,
    onPrimaryContainer = CoolWhite,
    secondary = CalibratedEmerald,
    onSecondary = ObsidianBg,
    secondaryContainer = CalibratedEmeraldBg,
    onSecondaryContainer = CoolWhite,
    tertiary = ForensicAmber,
    onTertiary = ObsidianBg,
    tertiaryContainer = ForensicAmberBg,
    onTertiaryContainer = CoolWhite,
    error = CrimsonSignal,
    onError = CoolWhite,
    errorContainer = CrimsonSignalBg,
    onErrorContainer = CoolWhite,
    background = ObsidianBg,
    onBackground = CoolWhite,
    surface = CarbonSurface,
    onSurface = CoolWhite,
    surfaceVariant = RecessedPanel,
    onSurfaceVariant = SoftSlateText,
    surfaceContainerHigh = ElevatedCarbon,
    outline = HairlineSlate,
    outlineVariant = HairlineSlate
)

private val ForensicShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun CurrencySentinelTheme(
    isArabic: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ForensicDarkColorScheme,
        typography = buildSentinelTypography(isArabic = isArabic),
        shapes = ForensicShapes,
        content = content
    )
}
