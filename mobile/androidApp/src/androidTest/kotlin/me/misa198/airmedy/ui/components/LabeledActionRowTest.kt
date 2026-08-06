package me.misa198.airmedy.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import me.misa198.airmedy.R
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test

class LabeledActionRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysLabelAndActionAtTheStandardHeight() {
        val label = string(R.string.appearance_theme_title)
        val action = string(R.string.theme_dark)

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Light) {
                LabeledActionRow(
                    labelRes = R.string.appearance_theme_title,
                    modifier = Modifier.testTag("labeled-action-row"),
                ) {
                    Text(action)
                }
            }
        }

        composeTestRule.onNodeWithText(label).assertExists()
        composeTestRule.onNodeWithText(action).assertExists()
        composeTestRule.onNodeWithTag("labeled-action-row").assertHeightIsEqualTo(56.dp)
    }

    private fun string(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
