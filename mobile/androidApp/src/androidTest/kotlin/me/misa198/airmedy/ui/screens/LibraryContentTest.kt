package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import me.misa198.airmedy.ui.components.AnchoredPopupMenuHost
import androidx.test.platform.app.InstrumentationRegistry
import me.misa198.airmedy.R
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun libraryContentDisplaysActionListItems() {
        var searchClicked = false
        var artistsClicked = false
        var albumsClicked = false
        var tracksClicked = false
        var genresClicked = false
        var composersClicked = false

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryContent(
                    onSearchSelected = { searchClicked = true },
                    onArtistsSelected = { artistsClicked = true },
                    onAlbumsSelected = { albumsClicked = true },
                    onTracksSelected = { tracksClicked = true },
                    onGenresSelected = { genresClicked = true },
                    onComposersSelected = { composersClicked = true },
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.onNodeWithText(context.getString(R.string.library_search)).assertIsDisplayed().performClick()
        assertTrue(searchClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_artists)).assertIsDisplayed().performClick()
        assertTrue(artistsClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_albums)).assertIsDisplayed().performClick()
        assertTrue(albumsClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_tracks)).assertIsDisplayed().performClick()
        assertTrue(tracksClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_genres)).assertIsDisplayed().performClick()
        assertTrue(genresClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_composers)).assertIsDisplayed().performClick()
        assertTrue(composersClicked)
    }

    @Test
    fun libraryContentRendersRecentTracksGridAndTriggersTrackClick() {
        var clickedTrackId: String? = null
        val recentTracks = listOf(
            LibraryTrack(id = "r1", title = "Recent Song 1", artists = "Artist 1"),
            LibraryTrack(id = "r2", title = "Recent Song 2", artists = "Artist 2"),
        )

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryContent(
                    recentTracks = recentTracks,
                    onTrackClick = { clickedTrackId = it },
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.onNodeWithText(context.getString(R.string.library_recently_added)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Recent Song 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recent Song 2").assertIsDisplayed().performClick()

        assertEquals("r2", clickedTrackId)
    }

    @Test
    fun holdingRecentlyAddedTrackOpensContextMenu() {
        val track = LibraryTrack(id = "r1", title = "Recent Song", artists = "Artist")
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryContent(recentTracks = listOf(track))
                }
            }
        }

        composeTestRule.onNodeWithText("Recent Song").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Track info").assertIsDisplayed()
    }

    @Test
    fun trackInfoSheetOpensFromRecentlyAddedGrid() {
        val track = LibraryTrack(id = "r1", title = "Recent Song", artists = "Artist")
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryContent(recentTracks = listOf(track))
                }
            }
        }

        composeTestRule.onNodeWithText("Recent Song").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Track info").performClick()

        composeTestRule.onNodeWithText("Coming soon").assertIsDisplayed()
    }
}
