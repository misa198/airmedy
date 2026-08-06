package me.misa198.airmedy

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.chrisbanes.haze.rememberHazeState
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.components.StackPageLayout
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class StackPageLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun backButtonIsShownOnlyForPagesWithPreviousScreenAndInvokesCallback() {
        var backClicks = 0
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Light) {
                val hazeState = rememberHazeState()
                StackPageLayout(
                    title = "Album",
                    hazeState = hazeState,
                    contentBottomPadding = 0.dp,
                    isContentScrolled = false,
                    onBackClick = { backClicks++ },
                ) { _, _ ->
                    Text("Page content")
                }
            }
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back))
            .performClick()

        assertEquals(1, backClicks)
    }

    @Test
    fun rootPageOmitsBackButtonAndDisplaysActions() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Light) {
                val hazeState = rememberHazeState()
                StackPageLayout(
                    title = "Home",
                    hazeState = hazeState,
                    contentBottomPadding = 0.dp,
                    isContentScrolled = false,
                    actions = { Text("More") },
                    hasActions = true,
                ) { _, _ ->
                    Text("Page content")
                }
            }
        }

        composeTestRule.onAllNodesWithContentDescription(string(R.string.navigate_back))
            .assertCountEquals(0)
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("More").assertIsDisplayed()
    }

    private fun string(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
