package me.misa198.airmedy.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.ModifierNodeElement
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectNode
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.ui.theme.AirmedyColors
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalHazeApi::class)
class LiquidGlassTest {
    @Test
    fun backdropKeepsFullInputResolution() {
        val colors = AirmedyColors(
            background = Color.Unspecified,
            playerBackdrop = Color.Unspecified,
            card = Color.Unspecified,
            glass = Color.White.copy(alpha = 0.4f),
            glassOpaque = Color.White,
            glassElevated = Color.Unspecified,
            sliderInactive = Color.Unspecified,
            buttonSecondary = Color.Unspecified,
            textFieldClear = Color.Unspecified,
            borderGlass = Color.Unspecified,
            textMain = Color.Unspecified,
            textMuted = Color.Unspecified,
            primary = Color.Unspecified,
            onPrimary = Color.Unspecified,
            foregroundSubtle = Color.Unspecified,
            success = Color.Unspecified,
            navigationActive = Color.Unspecified,
        )
        val nodes = Modifier.liquidGlassBackground(HazeState(), colors)
            .foldIn(emptyList<HazeEffectNode>()) { nodes, element ->
                val node = (element as? ModifierNodeElement<*>)?.create()
                if (node is HazeEffectNode) nodes + node else nodes
            }
        val effect = nodes.single()
        // Apply the modifier's real configuration without attaching an Android renderer.
        effect.block!!.invoke(effect)
        assertEquals(HazeInputScale.None, effect.inputScale)
    }
}
