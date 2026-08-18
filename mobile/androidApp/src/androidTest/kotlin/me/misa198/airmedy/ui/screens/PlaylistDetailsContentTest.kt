package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.ui.components.AnchoredPopupMenuHost
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class PlaylistDetailsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysTrackCountAndDesktopStyleDurationWithoutArtistOrCopyright() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                PlaylistDetailsContent(
                    PlaylistDetailsUiState(
                        playlist = LibraryPlaylist("mix", "Night drive", listOf("one", "two"), "{}"),
                        tracks = listOf(
                            LibraryTrack("one", "One", "Artist", metadataJson = """{"duration":60}"""),
                            LibraryTrack("two", "Two", "Artist", metadataJson = """{"duration":120}"""),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Night drive").assertExists()
        composeTestRule.onNodeWithText("2 tracks · 3 min").assertExists()
        composeTestRule.onAllNodesWithTag("playlist-detail-track-divider").assertCountEquals(3)
    }

    @Test
    fun trackMenuEndsWithDestructiveRemoveFromPlaylistAction() {
        val track = LibraryTrack("one", "One", "Artist")
        var removedTrackId: String? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    PlaylistDetailsContent(
                        PlaylistDetailsUiState(
                            playlist = LibraryPlaylist("mix", "Night drive", listOf(track.id), "{}"),
                            tracks = listOf(track),
                        ),
                        playbackQueue = PlaybackQueueSnapshot(activeTrackIds = listOf(track.id)),
                        onTrackRemoveFromPlaylist = { removedTrackId = it },
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()
        composeTestRule.onNodeWithText("Remove from playlist").performClick()
        assertEquals(track.id, removedTrackId)
    }

    @Test
    fun heroMoreShowsPlaylistPlaybackAndEditingActions() {
        val track = LibraryTrack("one", "One", "Artist")
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    PlaylistDetailsContent(
                        PlaylistDetailsUiState(
                            playlist = LibraryPlaylist("mix", "Night drive", listOf(track.id), "{}"),
                            tracks = listOf(track),
                        ),
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("More options")).performClick()
        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onNodeWithText("Add to queue").assertExists()
        composeTestRule.onNodeWithText("Edit playlist").assertExists()
        composeTestRule.onNodeWithText("Delete playlist").assertExists()
    }

    @Test
    fun favoritesMenuOffersEditButNotDelete() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    PlaylistDetailsContent(
                        PlaylistDetailsUiState(playlist = LibraryPlaylist(FavoritesPlaylistId, "", emptyList(), "{}")),
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("More options")).performClick()
        composeTestRule.onNodeWithText("Edit playlist").assertExists()
        composeTestRule.onAllNodesWithText("Delete playlist").assertCountEquals(0)
    }
}
