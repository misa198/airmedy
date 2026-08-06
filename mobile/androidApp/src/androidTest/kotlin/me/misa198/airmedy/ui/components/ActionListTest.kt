package me.misa198.airmedy.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.R
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test

class ActionListTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun actionRowsExposeClickOnlyWhenTheyHaveAnAction() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Light) {
                ActionList(
                    items = listOf(
                        ActionListItem(
                            labelRes = R.string.home_action_one,
                            iconRes = LucideR.drawable.lucide_ic_circle_play,
                            onClick = {},
                        ),
                        ActionListItem(
                            labelRes = R.string.home_action_two,
                            iconRes = LucideR.drawable.lucide_ic_list_music,
                        ),
                    ),
                    containerStyle = ActionListContainerStyle.Card,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.home_action_one)).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(string(R.string.home_action_two)).assertHasNoClickAction()
    }

    private fun string(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
