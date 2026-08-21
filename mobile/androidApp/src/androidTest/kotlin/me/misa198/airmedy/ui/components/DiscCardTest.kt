package me.misa198.airmedy.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiscCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun discCardRendersTitleAndSubtitleAndTriggersOnClick() {
        var clicked = false

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                DiscCard(
                    title = "Sample Track Title",
                    subtitle = "Sample Artist Subtitle",
                    onClick = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Sample Track Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sample Artist Subtitle").assertIsDisplayed().performClick()

        assertTrue(clicked)
    }
}
