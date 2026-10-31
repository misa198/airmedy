package me.misa198.airmedy.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

class LivingArtworkBackgroundTest {
    @get:Rule val compose = createComposeRule()

    @Test fun noPlaceholderColorsAppearBeforeTheFirstDecodedPalette() {
        var palette by mutableStateOf<FloatArray?>(null)
        var failed by mutableStateOf(false)
        val backdrop = Color(0xff18181b)
        compose.setContent {
            Box(Modifier.size(160.dp).background(backdrop).testTag("background_probe")) {
                LivingArtworkBackground(
                    palette = palette, outgoingPalette = null, isPlaying = false,
                    crossfadeId = null, crossfadeProgress = 1f, onFailure = { failed = true },
                )
            }
        }
        val initial = centerPixel()
        assertEquals(backdrop.red, initial.red, 2f / 255f)
        assertEquals(backdrop.green, initial.green, 2f / 255f)
        assertEquals(backdrop.blue, initial.blue, 2f / 255f)
        // Identical color uniforms isolate initial rendering from noise-field motion.
        compose.runOnIdle { palette = FloatArray(12) { floatArrayOf(0.2f, 0.4f, 0.6f)[it % 3] } }
        var firstColoredFrame = backdrop
        compose.waitUntil(timeoutMillis = 5_000) {
            firstColoredFrame = centerPixel()
            failed || abs(firstColoredFrame.blue - backdrop.blue) > 2f / 255f
        }
        assertFalse("GPU initialization must succeed", failed)
        assertEquals(0.2f, firstColoredFrame.red, 3f / 255f)
        assertEquals(0.4f, firstColoredFrame.green, 3f / 255f)
        assertEquals(0.6f, firstColoredFrame.blue, 3f / 255f)
    }

    @Test fun scaledAndBlurredArtworkStaysInsideThePanelWhileItMoves() {
        var panelOffset by mutableStateOf(128.dp)
        var failed by mutableStateOf(false)
        val backdrop = Color(0xff18181b)
        val palette = FloatArray(12) { floatArrayOf(0.2f, 0.4f, 0.6f)[it % 3] }
        compose.setContent {
            val haze = rememberHazeState()
            Box(Modifier.size(240.dp, 320.dp).background(backdrop).testTag("background_probe")) {
                Box(Modifier.fillMaxSize().offset(y = panelOffset).hazeSource(haze)) {
                    LivingArtworkBackground(
                        palette = palette, outgoingPalette = null, isPlaying = false,
                        crossfadeId = null, crossfadeProgress = 1f, onFailure = { failed = true },
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            val pixels = compose.onNodeWithTag("background_probe").captureToImage().toPixelMap()
            failed || pixels[pixels.width / 2, pixels.height * 3 / 4].blue > 0.5f
        }
        assertFalse("GPU initialization must succeed", failed)
        for (offset in listOf(128.dp, 64.dp)) {
            compose.runOnIdle { panelOffset = offset }
            val pixels = compose.onNodeWithTag("background_probe").captureToImage().toPixelMap()
            val panelTop = (offset.value / 320f * pixels.height).toInt()
            for (y in listOf(0, panelTop / 2, panelTop - 2)) {
                for (x in listOf(pixels.width / 4, pixels.width / 2, pixels.width * 3 / 4)) {
                    val actual = pixels[x, y]
                    assertEquals("Artwork escaped above the moving panel", backdrop.red, actual.red, 2f / 255f)
                    assertEquals(backdrop.green, actual.green, 2f / 255f)
                    assertEquals(backdrop.blue, actual.blue, 2f / 255f)
                }
            }
            assertEquals(0.6f, pixels[pixels.width / 2, pixels.height * 3 / 4].blue, 3f / 255f)
        }
    }

    @Test fun finalDitherSurvivesBlurWithoutVisibleColorSteps() {
        val palette = FloatArray(12) { floatArrayOf(0.2f, 0.4f, 0.6f)[it % 3] }
        var failed by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.size(160.dp).testTag("background_probe")) {
                LivingArtworkBackground(
                    palette = palette, outgoingPalette = null, isPlaying = false,
                    crossfadeId = null, crossfadeProgress = 1f, onFailure = { failed = true },
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { failed || centerPixel().blue > 0.5f }
        assertFalse("GPU initialization must succeed", failed)
        val pixels = compose.onNodeWithTag("background_probe").captureToImage().toPixelMap()
        val values = mutableListOf<Int>()
        for (y in pixels.height / 2 - 32 until pixels.height / 2 + 32) {
            for (x in pixels.width / 2 - 32 until pixels.width / 2 + 32) {
                values += (pixels[x, y].blue * 255f).roundToInt()
            }
        }
        assertTrue("Dither must survive the image blur instead of becoming a flat 8-bit plateau", values.distinct().size > 1)
        assertTrue("Dither must remain below visible grain levels", values.max() - values.min() <= 3)
        assertEquals("Dither must preserve the artwork color", 0.6, values.average() / 255.0, 2.0 / 255.0)
    }

    private fun centerPixel(): Color {
        val pixels = compose.onNodeWithTag("background_probe").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }
}
