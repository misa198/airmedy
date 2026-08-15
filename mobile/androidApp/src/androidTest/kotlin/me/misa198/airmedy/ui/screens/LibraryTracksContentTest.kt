package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.ui.components.AnchoredPopupMenuHost
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryTracksContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysEmptyStateWhenNoTracks() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(tracks = emptyList()),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No tracks in library").assertExists()
    }

    @Test
    fun displaysTracksList() {
        val sampleTracks = listOf(
            LibraryTrack(id = "1", title = "Song A", artists = "Artist A", album = "Album A"),
            LibraryTrack(id = "2", title = "Song B", artists = "Artist B", album = "Album B"),
        )

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(
                        tracks = sampleTracks,
                        sortOption = TrackSortOption.Name,
                        sortOrder = SortOrder.Ascending,
                    ),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Song A").assertExists()
        composeTestRule.onNodeWithText("Song B").assertExists()
        composeTestRule.onAllNodesWithTag("track-row-divider").assertCountEquals(1)
    }

    @Test
    fun playbackActionsAppearForTracksAndDispatchTheirMode() {
        var playMode: Boolean? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(tracks = listOf(LibraryTrack("track", "Song", "Artist"))),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                    onPlayAll = { playMode = it },
                )
            }
        }

        composeTestRule.onNode(hasContentDescription("Play")).performClick()
        assertEquals(false, playMode)
        composeTestRule.onNode(hasContentDescription("Shuffle")).performClick()
        assertEquals(true, playMode)
    }

    @Test
    fun clickingTrackEmitsTheSelectedTrack() {
        val track = LibraryTrack(id = "selected", title = "Song A", artists = "Artist A", album = "Album A")
        var selectedId: String? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(tracks = listOf(track)),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                    onTrackClick = { selectedId = it.id },
                )
            }
        }

        composeTestRule.onNodeWithText("Song A").performClick()
        assertEquals("selected", selectedId)
    }

    @Test
    fun moreOptionsOpensTrackContextMenu() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "Artist A")
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()
        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onNodeWithText("Add to queue").assertExists()
        composeTestRule.onNodeWithText("Add to favorites").assertExists()
        composeTestRule.onNodeWithText("Add to playlist").assertExists()
    }

    @Test
    fun holdingTrackOpensTrackContextMenu() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "Artist A")
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Song A").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Track info").assertExists()
    }

    @Test
    fun contextMenuHidesMissingAlbumAndArtistNavigation() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "")
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()

        composeTestRule.onAllNodesWithText("Go to album").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Go to artist").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Go to artists").assertCountEquals(0)
    }

    @Test
    fun trackRemainsVisibleWhileContextBottomSheetIsOpen() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "Artist A")
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()
        composeTestRule.onNodeWithText("Track info").performClick()

        composeTestRule.onNodeWithText("Track info").assertExists()
        composeTestRule.onNodeWithText("Song A").assertExists()
    }

    @Test
    fun currentTrackHidesQueueActions() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "Artist A")
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                        playbackQueue = PlaybackQueueSnapshot(
                            activeTrackIds = listOf(track.id),
                            currentIndex = 0,
                        ),
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()
        composeTestRule.onAllNodesWithText("Play next").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Add to queue").assertCountEquals(0)
    }

    @Test
    fun alreadyQueuedTrackHidesAddToQueueAction() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "Artist A")
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                        playbackQueue = PlaybackQueueSnapshot(
                            activeTrackIds = listOf("current", track.id),
                            currentIndex = 0,
                        ),
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()

        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onAllNodesWithText("Add to queue").assertCountEquals(0)
    }
}
