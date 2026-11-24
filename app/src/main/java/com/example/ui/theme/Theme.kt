package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeStyle

val LocalAppThemeStyle = staticCompositionLocalOf { AppThemeStyle.DARK_STUDIO }

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun AAVisualizerTheme(
    themeStyle: AppThemeStyle = AppThemeStyle.DARK_STUDIO,
    accentHex: Long = 0xFF00E5FF,
    content: @Composable () -> Unit
) {
    val accent = Color(accentHex)
    val secondary = if (accentHex == 0xFF7C4DFF) CyberCyan else ElectricViolet

    val colorScheme = when (themeStyle) {
        AppThemeStyle.PURE_OLED -> darkColorScheme(
            primary = accent,
            onPrimary = OledBlack,
            secondary = secondary,
            onSecondary = TextPrimary,
            tertiary = EmberCoral,
            background = OledBlack,
            onBackground = TextPrimary,
            surface = OledSurface,
            onSurface = TextPrimary,
            surfaceVariant = Color(0xFF0F121B),
            onSurfaceVariant = TextSecondary,
            outline = OledOutline
        )
        AppThemeStyle.GLASS -> darkColorScheme(
            primary = accent,
            onPrimary = StudioObsidian,
            secondary = secondary,
            onSecondary = TextPrimary,
            tertiary = SignalGreen,
            background = Color(0xFF070A12),
            onBackground = TextPrimary,
            surface = Color(0xAA131A2C),
            onSurface = TextPrimary,
            surfaceVariant = Color(0x881D2742),
            onSurfaceVariant = TextSecondary,
            outline = Color(0x55FFFFFF)
        )
        AppThemeStyle.SKEUOMORPHIC -> darkColorScheme(
            primary = accent,
            onPrimary = StudioObsidian,
            secondary = AmberPeak,
            onSecondary = OledBlack,
            tertiary = SignalGreen,
            background = SkeuoMetal,
            onBackground = TextPrimary,
            surface = SkeuoPanel,
            onSurface = TextPrimary,
            surfaceVariant = Color(0xFF28303E),
            onSurfaceVariant = TextSecondary,
            outline = SkeuoBezel
        )
        AppThemeStyle.FLAT_MINIMAL -> darkColorScheme(
            primary = accent,
            onPrimary = OledBlack,
            secondary = secondary,
            onSecondary = TextPrimary,
            tertiary = EmberCoral,
            background = FlatBg,
            onBackground = TextPrimary,
            surface = FlatCard,
            onSurface = TextPrimary,
            surfaceVariant = Color(0xFF242933),
            onSurfaceVariant = TextSecondary,
            outline = Color(0xFF343B48)
        )
        AppThemeStyle.DARK_STUDIO -> darkColorScheme(
            primary = accent,
            onPrimary = StudioObsidian,
            secondary = secondary,
            onSecondary = TextPrimary,
            tertiary = SignalGreen,
            background = StudioObsidian,
            onBackground = TextPrimary,
            surface = StudioSurface,
            onSurface = TextPrimary,
            surfaceVariant = StudioSurfaceElevated,
            onSurfaceVariant = TextSecondary,
            outline = StudioOutline
        )
    }

    CompositionLocalProvider(LocalAppThemeStyle provides themeStyle) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
