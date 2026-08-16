package me.misa198.airmedy.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AirmedyTextFieldTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nonEmptyValueShowsClearActionAndClearsTheValue() {
        var value by mutableStateOf("Night Drive")
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AirmedyTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "Playlist name",
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Clear text").assertIsDisplayed().performClick()

        composeTestRule.runOnIdle { assertEquals("", value) }
        composeTestRule.onAllNodesWithContentDescription("Clear text").assertCountEquals(0)
    }

    @Test
    fun clearActionCanBeHidden() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AirmedyTextField(
                    value = "Night Drive",
                    onValueChange = {},
                    placeholder = "Playlist name",
                    showClearButton = false,
                )
            }
        }

        composeTestRule.onAllNodesWithContentDescription("Clear text").assertCountEquals(0)
    }

    @Test
    fun customTrailingContentReplacesClearAction() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AirmedyTextField(
                    value = "Night Drive",
                    onValueChange = {},
                    placeholder = "Playlist name",
                    trailingContent = { Text("Custom action") },
                )
            }
        }

        composeTestRule.onAllNodesWithContentDescription("Clear text").assertCountEquals(0)
        composeTestRule.onNodeWithText("Custom action").assertIsDisplayed()
    }
}
