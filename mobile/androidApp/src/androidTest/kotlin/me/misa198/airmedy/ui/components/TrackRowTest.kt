package me.misa198.airmedy.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrackRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysTitleAndArtistAndHandlesClicks() {
        var rowClicked by mutableStateOf(false)
        var moreClicked by mutableStateOf(false)
        val title = "Starlight"
        val artist = "Muse"

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                TrackRow(
                    title = title,
                    artist = artist,
                    onClick = { rowClicked = true },
                    onMoreClick = { moreClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText(title).assertExists()
        composeTestRule.onNodeWithText(artist).assertExists()

        composeTestRule.onNodeWithText(title).performClick()
        assertTrue(rowClicked)

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()
        assertTrue(moreClicked)
    }
}
