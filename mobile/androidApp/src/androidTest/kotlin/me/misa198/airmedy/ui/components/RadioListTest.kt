package me.misa198.airmedy.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import me.misa198.airmedy.R
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyColors
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class RadioListTest {
    @get:Rule val compose = createComposeRule()

    @Test fun genericValuesResourceLabelsAndPrimaryCheckFollowTheCallerState() {
        var selected by mutableStateOf(0)
        var theme by mutableStateOf(ThemeMode.Light)
        lateinit var colors: AirmedyColors
        val resourceLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.theme_light)
        compose.setContent {
            AirmedyTheme(themeMode = theme) {
                colors = LocalAirmedyColors.current
                RadioList(
                    options = listOf(
                        SelectionOption(0, label = "First"),
                        SelectionOption(1, labelRes = R.string.theme_light),
                        SelectionOption(2, label = "Third"),
                    ),
                    selectedValue = selected,
                    onValueSelected = { selected = it },
                )
            }
        }
        compose.onNodeWithText("First").assertIsSelected()
        compose.onNodeWithText(resourceLabel).assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(1, selected) }
        compose.onNodeWithText("First").assertIsNotSelected()
        compose.onNodeWithText(resourceLabel).assertIsSelected()
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            compose.runOnIdle { theme = mode }
            val pixels = compose.onNodeWithTag("radio_list_selected_check", useUnmergedTree = true).captureToImage().toPixelMap()
            var primaryPixelFound = false
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (abs(pixel.red - colors.primary.red) < 0.02f &&
                    abs(pixel.green - colors.primary.green) < 0.02f &&
                    abs(pixel.blue - colors.primary.blue) < 0.02f) primaryPixelFound = true
            }
            assertTrue("Selected check must use primary in $mode", primaryPixelFound)
        }
        compose.onNodeWithText(resourceLabel).performClick()
        compose.runOnIdle { assertEquals(1, selected) }
        compose.runOnIdle { selected = 2 }
        compose.onNodeWithText("Third").assertIsSelected()
        compose.onNodeWithText(resourceLabel).assertIsNotSelected()
    }
}
