package me.misa198.airmedy.ui.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrackContextBottomSheetTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun playlistRowAddsSingleTrackWhenItIsNotAMember() {
        var change: Triple<String, List<String>, Boolean>? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                TrackContextBottomSheet(
                    request = TrackContextBottomSheetRequest.Playlist(listOf("track-1")),
                    onDismiss = {},
                    onArtistSelected = {},
                    playlists = listOf(LibraryPlaylist("playlist-1", "Road trip", emptyList(), "{}")),
                    onPlaylistMembershipChange = { id, ids, add -> change = Triple(id, ids, add) },
                )
            }
        }

        composeTestRule.onNodeWithText("Road trip").performClick()

        assertEquals(Triple("playlist-1", listOf("track-1"), true), change)
    }

    @Test
    fun playlistRowAddsEveryMissingTrackForAnAlbumSelection() {
        var change: Triple<String, List<String>, Boolean>? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                TrackContextBottomSheet(
                    request = TrackContextBottomSheetRequest.Playlist(listOf("track-1", "track-2")),
                    onDismiss = {},
                    onArtistSelected = {},
                    playlists = listOf(LibraryPlaylist("playlist-1", "Road trip", listOf("track-1"), "{}")),
                    onPlaylistMembershipChange = { id, ids, add -> change = Triple(id, ids, add) },
                )
            }
        }

        composeTestRule.onNodeWithText("Road trip").performClick()

        assertEquals(Triple("playlist-1", listOf("track-2"), true), change)
    }

    @Test
    fun trackInfoRendersSelectedTrackMetadata() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                TrackContextBottomSheet(
                    request = TrackContextBottomSheetRequest.Info(
                        LibraryTrack(
                            id = "track-1",
                            title = "Track",
                            artists = "Artist",
                            metadataJson = """{"format":"flac","duration":245,"sample_rate":96000,"bit_depth":24}""",
                        ),
                    ),
                    onDismiss = {},
                    onArtistSelected = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Track").assertIsDisplayed()
        composeTestRule.onNodeWithText("Artist").assertIsDisplayed()
        composeTestRule.onNodeWithText("4:05").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hi-Res").assertIsDisplayed()
    }
}
