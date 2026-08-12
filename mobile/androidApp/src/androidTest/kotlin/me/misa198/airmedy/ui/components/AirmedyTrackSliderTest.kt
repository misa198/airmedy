package me.misa198.airmedy.ui.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AirmedyTrackSliderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun preservesRangeSemanticsForTheThickerThumblessProgressBar() {
        var value = 0f
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AirmedyTrackSlider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = androidx.compose.ui.Modifier.semantics {
                        contentDescription = "Playback position"
                    },
                    trackHeight = 6.dp,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Playback position")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(0.75f))
            }

        composeTestRule.runOnIdle {
            assertEquals(0.75f, value, 0.001f)
        }
    }

    @Test
    fun dragUsesTheLatestCallbackAndOffsetsFromTheCurrentValue() {
        val useNewTrackCallback = mutableStateOf(false)
        var oldTrackValue = -1f
        var newTrackValue = -1f
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AirmedyTrackSlider(
                    value = 0.25f,
                    onValueChange = { value ->
                        if (useNewTrackCallback.value) newTrackValue = value else oldTrackValue = value
                    },
                    modifier = androidx.compose.ui.Modifier.semantics {
                        contentDescription = "Playback position"
                    },
                )
            }
        }

        composeTestRule.runOnIdle { useNewTrackCallback.value = true }
        val sliderWidth = composeTestRule.onNodeWithContentDescription("Playback position")
            .fetchSemanticsNode().boundsInRoot.width
        composeTestRule.onNodeWithContentDescription("Playback position")
            .performTouchInput {
                down(center)
                moveBy(Offset(x = 100f, y = 0f))
                up()
            }

        composeTestRule.runOnIdle {
            assertEquals(-1f, oldTrackValue, 0.001f)
            assertEquals(0.25f + 100f / sliderWidth, newTrackValue, 0.03f)
        }
    }

    @Test
    fun tapDoesNotChangeTheSliderValue() {
        var value = 0.25f
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AirmedyTrackSlider(value = value, onValueChange = { value = it })
            }
        }

        composeTestRule.onNodeWithTag(AirmedyTrackSliderTrackTestTag)
            .performTouchInput { down(center); up() }

        composeTestRule.runOnIdle { assertEquals(0.25f, value, 0.001f) }
    }

    @Test
    fun pressSmoothlyExpandsTheGlassTrack() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AirmedyTrackSlider(
                    value = 0.5f,
                    onValueChange = {},
                    trackHeight = 6.dp,
                )
            }
        }

        val restingHeight = composeTestRule.onNodeWithTag(AirmedyTrackSliderTrackTestTag)
            .fetchSemanticsNode().boundsInRoot.height
        composeTestRule.onNodeWithTag(AirmedyTrackSliderTrackTestTag)
            .performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(220)

        val expandedHeight = composeTestRule.onNodeWithTag(AirmedyTrackSliderTrackTestTag)
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(expandedHeight > restingHeight)
    }
}
