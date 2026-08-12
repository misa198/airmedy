package me.misa198.airmedy

import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.chrisbanes.haze.rememberHazeState
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.components.StackPageLayout
import me.misa198.airmedy.ui.components.StackPageHeader
import me.misa198.airmedy.ui.components.StackPageTitleTag
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

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

    @Test
    fun stackPagesWithBackButtonUseNormalSizedTitleText() {
        var showsBackButton by mutableStateOf(false)
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Light) {
                StackPageHeader(
                    title = "Album",
                    hazeState = null,
                    isContentScrolled = false,
                    onBackClick = if (showsBackButton) ({}) else null,
                    animateChanges = false,
                )
            }
        }

        val rootTitleHeight = composeTestRule.onNodeWithTag(StackPageTitleTag)
            .fetchSemanticsNode().boundsInRoot.bottom -
            composeTestRule.onNodeWithTag(StackPageTitleTag).fetchSemanticsNode().boundsInRoot.top
        showsBackButton = true
        composeTestRule.waitForIdle()
        val stackedTitleHeight = composeTestRule.onNodeWithTag(StackPageTitleTag)
            .fetchSemanticsNode().boundsInRoot.bottom -
            composeTestRule.onNodeWithTag(StackPageTitleTag).fetchSemanticsNode().boundsInRoot.top

        assertTrue("A stack title with Back must be smaller than a root title", stackedTitleHeight < rootTitleHeight)
    }

    @Test
    fun headerOverlayPreventsTapsFromReachingContentBelowIt() {
        var contentClicks = 0
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Light) {
                StackPageLayout(
                    title = "Library",
                    hazeState = null,
                    contentBottomPadding = 0.dp,
                    isContentScrolled = true,
                ) { _, _ ->
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .clickable { contentClicks++ },
                    )
                }
            }
        }

        val titleBounds = composeTestRule.onNodeWithTag(StackPageTitleTag)
            .fetchSemanticsNode().boundsInRoot
        composeTestRule.onRoot().performTouchInput { click(titleBounds.center) }

        assertEquals(0, contentClicks)
    }

    private fun string(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
