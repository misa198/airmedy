package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.AnchoredPopupMenuHost
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryAlbumsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysEmptyStateWhenNoAlbums() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) { LibraryAlbumsContent(LibraryAlbumsUiState()) }
        }
        composeTestRule.onNodeWithText("No albums in library").assertExists()
    }

    @Test
    fun displaysAlbumsInVirtualizedRowsAndUsesUnknownArtistFallback() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryAlbumsContent(
                    LibraryAlbumsUiState(
                        albums = listOf(
                            LibraryAlbum(id = "a", title = "Album A", artist = "Artist A"),
                            LibraryAlbum(id = "b", title = "Album B"),
                        ),
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("Album A").assertExists()
        composeTestRule.onNodeWithText("Artist A").assertExists()
        composeTestRule.onNodeWithText("Unknown artist").assertExists()
        composeTestRule.onAllNodesWithTag("album-row-divider").assertCountEquals(1)
    }

    @Test
    fun playbackActionsAppearForAlbumsAndDispatchTheirMode() {
        var playMode: Boolean? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryAlbumsContent(
                    LibraryAlbumsUiState(albums = listOf(LibraryAlbum(id = "album", title = "Album"))),
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
    fun holdingAnAlbumOpensItsContextMenu() {
        var request: TrackContextBottomSheetRequest? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryAlbumsContent(
                        LibraryAlbumsUiState(
                            albums = listOf(LibraryAlbum(id = "album", title = "Album A", artist = "Artist A")),
                            tracks = listOf(LibraryTrack("track", "Track", "Artist A", albumId = "album")),
                        ),
                        onTrackContextBottomSheet = { request = it },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Album A").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onNode(hasContentDescription("Add to favourites")).assertExists()
        composeTestRule.onNodeWithText("Add to playlist").performClick()
        assertEquals(
            TrackContextBottomSheetRequest.Playlist(listOf("track"), addOnly = true),
            request,
        )
    }

    @Test
    fun displaysAlbumsInTwoColumnGridWithoutListDividers() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryAlbumsContent(
                    LibraryAlbumsUiState(
                        layoutMode = AlbumLayoutMode.Grid,
                        albums = listOf(
                            LibraryAlbum(id = "a", title = "Album A", artist = "Artist A"),
                            LibraryAlbum(id = "b", title = "Album B"),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Album A").assertExists()
        composeTestRule.onNodeWithText("Album B").assertExists()
        composeTestRule.onNodeWithText("Unknown artist").assertExists()
        composeTestRule.onAllNodesWithTag("album-row-divider").assertCountEquals(0)
    }

    @Test
    fun gridResizesFromTwoToFourColumnsAndKeepsLastRowCardWidth() {
        val width = mutableStateOf(360.dp)
        var clicked: String? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryAlbumsContent(
                    uiState = LibraryAlbumsUiState(
                        layoutMode = AlbumLayoutMode.Grid,
                        albums = (1..5).map { LibraryAlbum(id = "$it", title = "Card $it", artist = "Artist") },
                    ),
                    onAlbumClick = { clicked = it.id },
                    modifier = Modifier.requiredSize(width.value, 1000.dp),
                )
            }
        }
        fun bounds(index: Int) = composeTestRule.onNodeWithText("Card $index").getUnclippedBoundsInRoot()

        assertEquals(bounds(1).top.value, bounds(2).top.value, 1f)
        assertTrue(bounds(3).top.value > bounds(1).top.value)
        composeTestRule.onNodeWithText("Card 2").performClick()
        assertEquals("2", clicked)

        composeTestRule.runOnIdle { width.value = 700.dp }
        composeTestRule.waitForIdle()
        assertEquals(bounds(1).top.value, bounds(4).top.value, 1f)
        assertTrue(bounds(5).top.value > bounds(1).top.value)
        assertEquals((bounds(1).right - bounds(1).left).value, (bounds(5).right - bounds(5).left).value, 1f)
    }

    @Test
    fun alphabeticalJumpTargetsResponsiveGridRowAfterResize() {
        val width = mutableStateOf(360.dp)
        val listState = LazyListState()
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryAlbumsContent(
                    uiState = LibraryAlbumsUiState(
                        layoutMode = AlbumLayoutMode.Grid,
                        albums = ('A'..'Z').map { LibraryAlbum(id = "$it", title = "${it} Album", artist = "Artist") },
                    ),
                    listState = listState,
                    modifier = Modifier.requiredSize(width.value, 300.dp),
                )
            }
        }
        composeTestRule.onNodeWithText("E").performClick()
        composeTestRule.runOnIdle { assertEquals(4, listState.firstVisibleItemIndex) }

        composeTestRule.runOnIdle { width.value = 200.dp }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("E").performClick()
        composeTestRule.runOnIdle { assertEquals(6, listState.firstVisibleItemIndex) }
    }
}
