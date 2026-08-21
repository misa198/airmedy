package me.misa198.airmedy.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test

class HeroCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysItsTitleAndDescription() {
        val title = "No connected devices"
        val description = "Connect a device to keep your listening in sync."

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Light) {
                HeroCard(
                    symbol = MaterialSymbols.Power,
                    title = title,
                    description = description,
                )
            }
        }

        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(description).assertIsDisplayed()
    }
}
