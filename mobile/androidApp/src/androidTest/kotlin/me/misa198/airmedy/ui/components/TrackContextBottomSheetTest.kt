package me.misa198.airmedy.ui.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.lyrics.LyricsSearchResult
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

    @Test
    fun findLyricsPreviewPopRestoresSearchThenSavesTheSelectedResult() {
        var selected: LyricsSearchResult? = null
        val result = LyricsSearchResult("lrclib", "Found track", "Found artist", 245, "Preview lyrics", "lrclib-plain")
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                TrackContextBottomSheet(
                    request = TrackContextBottomSheetRequest.FindLyrics(
                        LibraryTrack(id = "track-1", title = "Track", artists = "Artist", metadataJson = "{\"duration\":245}"),
                    ),
                    onDismiss = {},
                    onArtistSelected = {},
                    onSearchLyrics = { _, _, _ -> listOf(result) },
                    onLyricsSelected = { _, lyric -> selected = lyric },
                )
            }
        }

        composeTestRule.onAllNodesWithText("Track title").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription("Track title").apply {
            performTextClearance()
            performTextInput("Retained title")
        }
        composeTestRule.onNodeWithText("Search").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Found track").performClick()
        composeTestRule.onNodeWithText("Preview lyrics").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitUntil(1_000) {
            composeTestRule.onAllNodesWithContentDescription("Track title").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Track title").assertTextEquals("Retained title")
        composeTestRule.onNodeWithText("Found track").performClick()
        composeTestRule.onNodeWithText("Select").performClick()

        assertEquals(result, selected)
    }
}
