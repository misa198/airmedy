package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.ui.components.AnchoredPopupMenuHost
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import org.junit.Rule
import org.junit.Test

class AlbumDetailsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysPublishedYearTrackCountAndTotalDurationInHeroMetadata() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(
                    AlbumDetailsUiState(
                        album = LibraryAlbum("album", "Absolution", "Muse", year = 2003),
                        tracks = listOf(
                            LibraryTrack("one", "One", "Muse", metadataJson = """{"duration":60}"""),
                            LibraryTrack("two", "Two", "Muse", metadataJson = """{"duration":120}"""),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Muse").assertExists()
        composeTestRule.onNodeWithText("2003 · 2 tracks · 3 min").assertExists()
        composeTestRule.onAllNodesWithTag("album-detail-track-divider").assertCountEquals(3)
    }

    @Test
    fun displaysCopyrightBelowTheTrackListWhenPresent() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(
                    AlbumDetailsUiState(
                        album = LibraryAlbum("album", "Absolution", "Muse", copyright = "© 2003 Taste Media"),
                        tracks = listOf(LibraryTrack("one", "One", "Muse")),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("© 2003 Taste Media").assertExists()
    }

    @Test
    fun trackOverflowShowsContextMenuWithoutGoToAlbum() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    AlbumDetailsContent(
                        uiState = AlbumDetailsUiState(
                            album = LibraryAlbum("album", "Absolution", "Muse"),
                            tracks = listOf(LibraryTrack("one", "One", "Muse")),
                        ),
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()
        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onAllNodesWithText("Go to album").assertCountEquals(0)
    }

    @Test
    fun holdingTrackOpensContextMenu() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    AlbumDetailsContent(
                        uiState = AlbumDetailsUiState(
                            album = LibraryAlbum("album", "Absolution", "Muse"),
                            tracks = listOf(LibraryTrack("one", "One", "Muse")),
                        ),
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("One").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Track info").assertExists()
    }

    @Test
    fun heroOverflowShowsAlbumActionsAndAddsOnlyNonFavoriteTracks() {
        var favoriteIds = emptyList<String>()
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    AlbumDetailsContent(
                        uiState = AlbumDetailsUiState(
                            album = LibraryAlbum("album", "Absolution", "Muse"),
                            tracks = listOf(
                                LibraryTrack("one", "One", "Muse"),
                                LibraryTrack("two", "Two", "Muse", metadataJson = """{"is_favorite":true}"""),
                            ),
                        ),
                        onAlbumAddToFavorites = { favoriteIds = it },
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Album options")).performClick()
        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onNodeWithText("Add to queue").assertExists()
        composeTestRule.onNodeWithText("Add to favourites").performClick()

        org.junit.Assert.assertEquals(listOf("one"), favoriteIds)
    }

    @Test
    fun heroOverflowHidesAddToQueueAndRequestsPlaylistPicker() {
        var request: TrackContextBottomSheetRequest? = null
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    AlbumDetailsContent(
                        uiState = AlbumDetailsUiState(
                            album = LibraryAlbum("album", "Absolution", "Muse"),
                            tracks = listOf(LibraryTrack("one", "One", "Muse")),
                        ),
                        playbackQueue = PlaybackQueueSnapshot(activeTrackIds = listOf("one")),
                        onTrackContextBottomSheet = { request = it },
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Album options")).performClick()
        composeTestRule.onAllNodesWithText("Add to queue").assertCountEquals(0)
        composeTestRule.onNodeWithText("Add to playlist").performClick()
                org.junit.Assert.assertEquals(TrackContextBottomSheetRequest.Playlist(listOf("one"), addOnly = true), request)
    }
}
