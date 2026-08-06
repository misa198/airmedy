package me.misa198.airmedy.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import me.misa198.airmedy.R
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SelectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun selectingAnOptionInvokesTheCallbackAndMarksItSelected() {
        var selectedValue by mutableStateOf(ThemeMode.System)
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Light) {
                Selection(
                    labelRes = R.string.appearance_theme_title,
                    options = ThemeMode.entries.map { mode ->
                        SelectionOption(value = mode, labelRes = mode.labelRes)
                    },
                    selectedValue = selectedValue,
                    onValueSelected = { selectedValue = it },
                )
            }
        }

        val themeLabel = string(R.string.appearance_theme_title)
        val systemLabel = string(R.string.theme_system)
        val darkLabel = string(R.string.theme_dark)

        composeTestRule.onNodeWithText(themeLabel).assertHasNoClickAction()
        composeTestRule.onNodeWithText(systemLabel).assertHasClickAction().performClick()
        composeTestRule.onNodeWithText(darkLabel).performClick()

        composeTestRule.runOnIdle {
            assertEquals(ThemeMode.Dark, selectedValue)
        }
        composeTestRule.onNodeWithText(darkLabel).assertIsDisplayed()
    }

    private fun string(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
