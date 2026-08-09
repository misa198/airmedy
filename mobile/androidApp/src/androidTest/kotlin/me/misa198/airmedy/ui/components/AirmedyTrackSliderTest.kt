package me.misa198.airmedy.ui.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
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
}
