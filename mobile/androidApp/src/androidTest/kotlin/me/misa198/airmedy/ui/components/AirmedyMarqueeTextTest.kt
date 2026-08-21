package me.misa198.airmedy.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test

class AirmedyMarqueeTextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysTextContentCorrectly() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AirmedyMarqueeText(
                    text = "Very Long Track Title That Will Overflow The Container Width",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        composeTestRule.onNodeWithText("Very Long Track Title That Will Overflow The Container Width")
            .assertIsDisplayed()
    }
}
