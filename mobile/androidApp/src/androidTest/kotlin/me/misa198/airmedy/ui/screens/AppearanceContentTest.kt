package me.misa198.airmedy.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import me.misa198.airmedy.ui.theme.AirmedyColors
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import me.misa198.airmedy.R
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppearanceContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun selectingOledAppliesBlackBackgroundAndDarkForeground() {
        var mode by mutableStateOf(ThemeMode.Dark)
        lateinit var colors: AirmedyColors
        var materialBackground = Color.Unspecified
        composeTestRule.setContent {
            AirmedyTheme(themeMode = mode) {
                colors = LocalAirmedyColors.current
                materialBackground = MaterialTheme.colorScheme.background
                AppearanceContent(
                    themeMode = mode,
                    onThemeModeSelected = { mode = it },
                    reduceTransparency = false,
                    onReduceTransparencyChanged = {},
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeTestRule.onNodeWithText(context.getString(R.string.theme_dark)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_oled)).assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertEquals(ThemeMode.Oled, mode)
            assertEquals(Color.Black, colors.background)
            assertEquals(Color.Black, colors.playerBackdrop)
            assertEquals(Color.Black, materialBackground)
            assertEquals(Color.White, colors.textMain)
        }
        composeTestRule.onNodeWithText(context.getString(R.string.theme_oled)).assertIsDisplayed()
    }

    @Test
    fun appearanceContentDisplaysThemeAndReduceTransparencyItems() {
        var selectedThemeMode: ThemeMode? = null
        var reduceTransparencyState = false

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AppearanceContent(
                    themeMode = ThemeMode.Dark,
                    onThemeModeSelected = { selectedThemeMode = it },
                    reduceTransparency = reduceTransparencyState,
                    onReduceTransparencyChanged = { reduceTransparencyState = it },
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.onNodeWithText(context.getString(R.string.appearance_theme_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.appearance_reduce_transparency)).assertIsDisplayed().performClick()

        assertTrue(reduceTransparencyState)
    }
}
