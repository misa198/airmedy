package me.misa198.airmedy.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import me.misa198.airmedy.ui.theme.AirmedyColors

/** The shared backdrop treatment for the persistent navigation and page header. */
fun Modifier.liquidGlassBackground(
    hazeState: HazeState,
    colors: AirmedyColors,
): Modifier = hazeEffect(hazeState) {
    inputScale = HazeInputScale.Fixed(0.20f)
    blurEffect {
        blurRadius = 16.dp
        colorEffects = listOf(HazeColorEffect.tint(colors.glass))
    }
}.background(colors.glass)
