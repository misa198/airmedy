package me.misa198.airmedy.ui.navigation

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ArtworkPaletteTest {
    @Test fun firstDecodedPalettePaintsImmediatelyWithoutAnimatingFromPlaceholderColors() {
        val animation = ArtworkPaletteAnimation()
        animation.update(null, null, null, 1f)
        assertNull(animation.frame(0.034f, true))
        val decoded = floatArrayOf(0.1f, 0.2f, 0.3f)
        animation.update(decoded, null, null, 1f)
        assertArrayEquals(decoded, animation.frame(0.034f, true), 0f)
    }

    @Test fun temporarilyMissingPaletteRetainsTheDisplayedArtworkColors() {
        val animation = ArtworkPaletteAnimation()
        val decoded = floatArrayOf(0.1f, 0.2f, 0.3f)
        animation.update(decoded, null, null, 1f)
        animation.frame(0f, true)
        animation.update(null, null, null, 1f)
        assertArrayEquals(decoded, animation.frame(0.034f, true), 0f)
    }

    @Test fun transparentPixelsHaveNoPalette() {
        assertNull(extractArtworkPalette(intArrayOf(0x00ff0000, 0x7fffffff)))
        assertNull(extractArtworkPalette(intArrayOf()))
    }

    @Test fun flatArtworkKeepsItsColorInAllFields() {
        val palette = extractArtworkPalette(IntArray(64) { 0xff336699.toInt() })!!
        val expected = Color(0xff336699)
        assertEquals(expected, palette.dominant)
        assertEquals(expected, palette.vibrant)
        assertEquals(expected, palette.muted)
        assertEquals(expected, palette.backdrop)
    }

    @Test fun tinySaturatedLogoCannotBecomeVibrantOrMuted() {
        val pixels = IntArray(4096) { 0xff888888.toInt() }
        repeat(8) { pixels[it] = 0xffff0000.toInt() }
        val palette = extractArtworkPalette(pixels)!!
        assertEquals(Color(0xff888888), palette.vibrant)
        assertEquals(palette.backdrop, palette.muted)
    }

    @Test fun clustersKeepDominantAndVibrantSeparate() {
        val palette = extractArtworkPalette(IntArray(100) {
            when { it < 70 -> 0xff888888.toInt(); it < 90 -> 0xffff0000.toInt(); else -> 0xff222244.toInt() }
        })!!
        assertEquals(Color(0xff888888), palette.dominant)
        assertEquals(Color.Red, palette.vibrant)
        assertEquals(Color(0xff222244), palette.muted)
    }

    @Test fun brightCoverUsesBackdropInsteadOfDarkInkAndClampsLuminance() {
        val palette = ArtworkPalette(Color.White, Color.White, Color.Black, Color.White).shaderColors()
        for (offset in listOf(0, 3, 6)) {
            val luminance = palette[offset]*0.2126f + palette[offset+1]*0.7152f + palette[offset+2]*0.0722f
            assertEquals(0.85f, luminance, 0.0001f)
        }
        assertEquals(palette[3] * 0.2f, palette[9], 0.0001f)
    }

    @Test fun crossfadeUsesAbsoluteProgressAndSupportsInterruptedTransitions() {
        val from = floatArrayOf(0.2f, 0.1f, 0.3f)
        val to = floatArrayOf(0.4f, 0.2f, 0.1f)
        assertArrayEquals(from, blendArtworkPalette(from, to, 0f), 0.0001f)
        assertArrayEquals(to, blendArtworkPalette(from, to, 1f), 0.0001f)
        val midpoint = blendArtworkPalette(from, to, 0.5f)
        assertEquals((from[0] + to[0]) / kotlin.math.sqrt(2f), midpoint[0], 0.0001f)
        assertArrayEquals(midpoint, blendArtworkPalette(midpoint, from, 0f), 0.0001f)
    }

    @Test fun latePaletteAndNewTransitionKeepTheDisplayedStartingColors() {
        val animation = ArtworkPaletteAnimation()
        val first = floatArrayOf(0.2f)
        val second = floatArrayOf(0.4f)
        val third = floatArrayOf(0.1f)
        animation.update(first, first, null, 1f)
        animation.frame(0f, true)
        animation.update(second, first, null, 1f)
        val displayed = animation.frame(0.034f, true)!!
        animation.update(third, second, 1L, 0f)
        assertArrayEquals(displayed, animation.frame(0.034f, true), 0.0001f)
        animation.update(second, second, 1L, 0.5f)
        val halfway = animation.frame(0.034f, true)!!
        assertArrayEquals(blendArtworkPalette(displayed, second, 0.5f), halfway, 0.0001f)
        animation.update(third, second, 2L, 0f)
        assertArrayEquals(halfway, animation.frame(0.034f, true), 0.0001f)
    }

    @Test fun disabledMotionUsesNewPaletteImmediately() {
        val animation = ArtworkPaletteAnimation()
        val first = floatArrayOf(0.2f)
        val second = floatArrayOf(0.4f)
        animation.update(first, first, null, 1f)
        animation.frame(0f, true)
        animation.update(second, first, 1L, 0.5f)
        assertArrayEquals(second, animation.frame(0f, false), 0f)
    }

    @Test fun ordinaryTransitionsApproachNewPaletteWithoutJumping() {
        val from = floatArrayOf(0f)
        val to = floatArrayOf(1f)
        val step = approachArtworkPalette(from, to, 0.034f)
        assertTrue(step[0] > 0f && step[0] < 0.1f)
        val interrupted = approachArtworkPalette(step, from, 0.034f)
        assertTrue(interrupted[0] > 0f && interrupted[0] < step[0])
        assertArrayEquals(from, approachArtworkPalette(from, to, 0f), 0f)
    }
}
