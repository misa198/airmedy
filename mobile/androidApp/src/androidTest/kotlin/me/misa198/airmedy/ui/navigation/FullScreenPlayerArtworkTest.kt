package me.misa198.airmedy.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FullScreenPlayerArtworkTest {
    @get:Rule val compose = createComposeRule()

    @Test fun livingArtworkAddsShadowOutsideTheCoverInBothThemes() {
        var theme by mutableStateOf(ThemeMode.Light)
        var living by mutableStateOf(false)
        compose.setContent {
            AirmedyTheme(theme) {
                val colors = LocalAirmedyColors.current
                Box(
                    Modifier.size(256.dp).background(colors.card).testTag("shadow_probe"),
                    contentAlignment = Alignment.Center,
                ) {
                    FullScreenPlayerArtwork(
                        artwork = null, outgoingArtwork = null, incomingArtwork = null,
                        crossfadeProgress = 1f, isArtworkCrossfading = false,
                        modifier = Modifier.size(128.dp), livingArtworkBackground = living,
                    )
                }
            }
        }
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            compose.runOnIdle { theme = mode; living = false }
            val solid = compose.onNodeWithTag("shadow_probe").captureToImage()
            compose.runOnIdle { living = true }
            val raised = compose.onNodeWithTag("shadow_probe").captureToImage()
            assertTrue("Living artwork must cast a shadow in $mode", belowCoverBrightness(raised) < belowCoverBrightness(solid) - 0.001f)
            val solidPixels = solid.toPixelMap()
            val raisedPixels = raised.toPixelMap()
            assertEquals("Shadow must not tint the cover", solidPixels[solid.width / 3, solid.height / 3], raisedPixels[raised.width / 3, raised.height / 3])
            compose.runOnIdle { living = false }
            val restored = compose.onNodeWithTag("shadow_probe").captureToImage()
            assertEquals(belowCoverBrightness(solid), belowCoverBrightness(restored), 0.001f)
        }
    }

    private fun belowCoverBrightness(image: ImageBitmap): Float {
        val pixels = image.toPixelMap()
        var total = 0f
        var count = 0
        for (y in image.height * 76 / 100 until image.height * 82 / 100) {
            for (x in image.width * 3 / 8 until image.width * 5 / 8) {
                val color = pixels[x, y]
                total += (color.red + color.green + color.blue) / 3f
                count++
            }
        }
        return total / count
    }
}
