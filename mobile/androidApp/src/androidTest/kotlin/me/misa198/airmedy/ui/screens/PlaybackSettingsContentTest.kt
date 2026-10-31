package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.ui.theme.AirmedyColors
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import org.junit.Assert.assertEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.test.platform.app.InstrumentationRegistry
import me.misa198.airmedy.R
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class PlaybackSettingsContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playbackCardStaysDistinctFromThePaddedScrollablePageInBothThemes() {
        var theme by mutableStateOf(ThemeMode.Light)
        lateinit var colors: AirmedyColors
        var density = 1f
        compose.setContent {
            AirmedyTheme(themeMode = theme) {
                colors = LocalAirmedyColors.current
                density = LocalDensity.current.density
                Box(Modifier.requiredSize(320.dp, 560.dp).background(colors.background).testTag("playback_page")) {
                    PlaybackSettingsContent(
                        showFullscreenQualityBadge = true, onShowFullscreenQualityBadgeChanged = {},
                        keepScreenOnForLyrics = false, onKeepScreenOnForLyricsChanged = {},
                        onSongTransitionSelected = {}, onVolumeNormalizationSelected = {}, onEqualizerSelected = {},
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    )
                }
            }
        }
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            compose.runOnIdle { theme = mode }
            val pixels = compose.onNodeWithTag("playback_page").captureToImage().toPixelMap()
            for ((x, y, expected) in listOf(
                Triple(8, 64, colors.background),
                Triple(32, 64, colors.card),
                Triple(160, 548, colors.background),
            )) {
                val actual = pixels[(x * density).toInt(), (y * density).toInt()]
                assertEquals("Incorrect card/page surface in $mode", expected.red, actual.red, 1f / 255f)
                assertEquals(expected.green, actual.green, 1f / 255f)
                assertEquals(expected.blue, actual.blue, 1f / 255f)
            }
        }
    }

    @Test fun backgroundRadioSectionFollowsPlaybackCardAndChangesSelection() {
        var living by mutableStateOf(true)
        var theme by mutableStateOf(ThemeMode.Light)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val solid = context.getString(R.string.playback_background_solid)
        val animated = context.getString(R.string.playback_background_living)
        val helper = context.getString(R.string.playback_background_helper, animated, solid)
        compose.setContent {
            AirmedyTheme(themeMode = theme) {
                PlaybackSettingsContent(
                    showFullscreenQualityBadge = true, onShowFullscreenQualityBadgeChanged = {},
                    keepScreenOnForLyrics = false, onKeepScreenOnForLyricsChanged = {},
                    onSongTransitionSelected = {}, onVolumeNormalizationSelected = {}, onEqualizerSelected = {},
                    livingArtworkBackground = living, onLivingArtworkBackgroundChanged = { living = it },
                )
            }
        }
        val background = compose.onNodeWithText(context.getString(R.string.playback_fullscreen_background)).fetchSemanticsNode().boundsInRoot
        val previous = compose.onNodeWithText(context.getString(R.string.playback_keep_screen_on_for_lyrics)).fetchSemanticsNode().boundsInRoot
        assertTrue("Background selection must follow the last playback option", background.top >= previous.bottom)
        compose.onNodeWithText(animated).assertIsSelected()
        val helperBounds = compose.onNodeWithText(helper).assertHasNoClickAction().fetchSemanticsNode().boundsInRoot
        val lastOption = compose.onNodeWithText(animated).fetchSemanticsNode().boundsInRoot
        assertTrue("GPU guidance must sit below the background options", helperBounds.top >= lastOption.bottom)
        compose.onNodeWithText(solid).assertIsNotSelected()
        compose.runOnIdle { theme = ThemeMode.Dark }
        compose.onNodeWithText(solid).performClick()
        compose.runOnIdle { assertFalse(living) }
        compose.onNodeWithText(solid).assertIsSelected()
        compose.onNodeWithText(helper).assertExists()
        compose.onNodeWithText(animated).assertIsNotSelected()
        compose.onNodeWithText(solid).performClick()
        compose.runOnIdle { assertFalse("Tapping a selected radio must keep it selected", living) }
    }
}
