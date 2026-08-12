package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CrossfadeDurationSliderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun boundsAreBelowTheSliderTrack() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                CrossfadeDurationSlider(
                    seconds = 4,
                    enabled = true,
                    onSecondsChanged = {},
                    modifier = androidx.compose.ui.Modifier.width(280.dp),
                )
            }
        }

        val trackBounds = composeTestRule.onNodeWithTag(CrossfadeDurationSliderTrackTag)
            .fetchSemanticsNode().boundsInRoot
        val minimumBounds = composeTestRule.onNodeWithTag(CrossfadeDurationSliderMinimumTag)
            .fetchSemanticsNode().boundsInRoot
        val maximumBounds = composeTestRule.onNodeWithTag(CrossfadeDurationSliderMaximumTag)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(minimumBounds.top >= trackBounds.bottom)
        assertTrue(maximumBounds.top >= trackBounds.bottom)
        assertTrue(
            (trackBounds.right - trackBounds.left) >
                (minimumBounds.right - minimumBounds.left) + (maximumBounds.right - maximumBounds.left),
        )
    }

    @Test
    fun tappingNearTheEndSelectsTheTwelveSecondMaximum() {
        var selectedSeconds = 4
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                CrossfadeDurationSlider(
                    seconds = selectedSeconds,
                    enabled = true,
                    onSecondsChanged = { selectedSeconds = it },
                    modifier = androidx.compose.ui.Modifier.width(280.dp),
                )
            }
        }

        composeTestRule.onNodeWithTag(CrossfadeDurationSliderTrackTag)
            .performTouchInput { click(topRight - androidx.compose.ui.geometry.Offset(2f, 24f)) }

        assertEquals(12, selectedSeconds)
    }
}
