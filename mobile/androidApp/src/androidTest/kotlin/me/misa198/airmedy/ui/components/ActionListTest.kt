package me.misa198.airmedy.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
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
                            labelRes = R.string.library_artists,
                            leadingSymbol = MaterialSymbols.PlayCircle,
                            onClick = {},
                        ),
                        ActionListItem(
                            labelRes = R.string.library_albums,
                            leadingSymbol = MaterialSymbols.QueueMusic,
                        ),
                    ),
                    containerStyle = ActionListContainerStyle.Card,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.library_artists)).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(string(R.string.library_albums)).assertHasNoClickAction()
    }

    @Test
    fun supportsAnIconlessTrailingSlotAtTheStandardRowHeight() {
        val version = "1.0"

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Light) {
                ActionList(
                    items = listOf(
                        ActionListItem(
                            labelRes = R.string.appearance_theme_title,
                            trailingContent = { Text(version) },
                        ),
                    ),
                    containerStyle = ActionListContainerStyle.Plain,
                    modifier = Modifier.testTag("iconless-action-list"),
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.appearance_theme_title)).assertExists()
        composeTestRule.onNodeWithText(version).assertExists()
        composeTestRule.onNodeWithTag("iconless-action-list").assertHeightIsEqualTo(56.dp)
        composeTestRule
            .onNodeWithContentDescription(string(R.string.appearance_theme_title))
            .assertHasNoClickAction()
    }

    @Test
    fun longSettingTitlesLeaveTwelveDpBeforeTheirSwitchAtDifferentWidths() {
        var width by mutableStateOf(280.dp)
        var checked by mutableStateOf(false)
        var minimumGap = 0f
        val label = string(R.string.playback_keep_screen_on_for_lyrics)
        composeTestRule.setContent {
            minimumGap = with(LocalDensity.current) { 12.dp.toPx() }
            AirmedyTheme(themeMode = ThemeMode.Light) {
                Box(Modifier.width(width)) {
                    ActionList(
                        items = listOf(ActionListItem(
                            labelRes = R.string.playback_keep_screen_on_for_lyrics,
                            trailingContent = {
                                Switch(checked = checked, onCheckedChange = { checked = it },
                                    modifier = Modifier.testTag("settings_switch"))
                            },
                            onClick = { checked = !checked },
                        )),
                        containerStyle = ActionListContainerStyle.Card,
                    )
                }
            }
        }
        for (availableWidth in listOf(280.dp, 360.dp)) {
            composeTestRule.runOnIdle { width = availableWidth }
            val titleBounds = composeTestRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val switchBounds = composeTestRule.onNodeWithTag("settings_switch").fetchSemanticsNode().boundsInRoot
            assertTrue("Title must not touch the switch", switchBounds.left - titleBounds.right >= minimumGap - 1f)
        }
        composeTestRule.onNodeWithTag("settings_switch").performClick()
        composeTestRule.runOnIdle { assertTrue(checked) }
    }

    private fun string(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
