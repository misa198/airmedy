package me.misa198.airmedy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import me.misa198.airmedy.settings.ThemeMode

data class AirmedyColors(
    val background: Color,
    val card: Color,
    val glass: Color,
    val glassElevated: Color,
    val borderGlass: Color,
    val textMain: Color,
    val textMuted: Color,
    val primary: Color,
    val navigationActive: Color,
)

private val LightColors = AirmedyColors(
    background = Color(0xFFF4F4F5),
    card = Color.White,
    glass = Color.White.copy(alpha = 0.70f),
    glassElevated = Color.White.copy(alpha = 0.90f),
    borderGlass = Color.Black.copy(alpha = 0.10f),
    textMain = Color(0xFF0A0A0A),
    textMuted = Color(0xFF52525B),
    primary = Color(0xFFE11D48),
    navigationActive = Color.Black.copy(alpha = 0.08f),
)

private val DarkColors = AirmedyColors(
    background = Color(0xFF18181B),
    card = Color(0xFF27272A),
    glass = Color(0xFF232326).copy(alpha = 0.60f),
    glassElevated = Color(0xFF37373C).copy(alpha = 0.40f),
    borderGlass = Color.White.copy(alpha = 0.10f),
    textMain = Color.White,
    textMuted = Color(0xFFA1A1AA),
    primary = Color(0xFFE11D48),
    navigationActive = Color.Black.copy(alpha = 0.40f),
)

val LocalAirmedyColors = staticCompositionLocalOf { LightColors }

@Composable
fun AirmedyTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (darkTheme) DarkColors else LightColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            background = colors.background,
            surface = colors.glass,
            onBackground = colors.textMain,
            onSurface = colors.textMain,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            background = colors.background,
            surface = colors.glass,
            onBackground = colors.textMain,
            onSurface = colors.textMain,
        )
    }

    CompositionLocalProvider(LocalAirmedyColors provides colors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
