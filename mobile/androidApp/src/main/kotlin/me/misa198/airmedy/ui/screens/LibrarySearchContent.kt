package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.AlbumContextMenu
import me.misa198.airmedy.ui.components.DiscCard
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.LibraryTextFilter
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import me.misa198.airmedy.ui.components.TrackContextMenu
import me.misa198.airmedy.ui.components.TrackRow
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun LibrarySearchContent(
    uiState: LibrarySearchUiState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onQueryChange: (String) -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onComposerClick: (String) -> Unit = {},
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    onTrackPlayNext: (LibraryTrack) -> Unit = {},
    onTrackAddToQueue: (LibraryTrack) -> Unit = {},
    onTrackFavoriteToggle: (LibraryTrack, Boolean) -> Unit = { _, _ -> },
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
    onAlbumPlayNext: (List<String>) -> Unit = {},
    onAlbumAddToQueue: (List<String>) -> Unit = {},
    onAlbumAddToFavorites: (List<String>) -> Unit = {},
) {
    val colors = LocalAirmedyColors.current
    var contextTrack by remember { mutableStateOf<LibraryTrack?>(null) }
    var contextAlbumId by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier.fillMaxSize()) {
    val trackDividerWidth = maxWidth * 0.8f
    Column(Modifier.fillMaxSize()) {
        LibraryTextFilter(
            value = uiState.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.library_search_placeholder),
            modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
            if (uiState.query.isBlank()) {
                item { SearchEmptyState(R.string.library_search_empty_title, R.string.library_search_empty_description) }
            } else if (uiState.tracks.isEmpty() && uiState.albums.isEmpty() && uiState.artists.isEmpty() && uiState.playlists.isEmpty() && uiState.composers.isEmpty()) {
                item { SearchEmptyState(R.string.library_search_no_results_title, R.string.library_search_no_results_description) }
            } else {
                searchSection(R.string.library_tracks, uiState.tracks, { it.id }, rows = 3, topPadding = 20.dp) { track ->
                    TrackContextMenu(
                        track = track, expanded = contextTrack?.id == track.id,
                        onDismiss = { contextTrack = null }, playbackQueue = playbackQueue,
                        onPlayNext = onTrackPlayNext, onAddToQueue = onTrackAddToQueue,
                        onFavoriteChange = onTrackFavoriteToggle, onBottomSheetRequested = onTrackContextBottomSheet,
                    ) {
                        Column {
                            TrackRow(track.title, track.artists, artworkPath = track.artworkPath, contentPadding = PaddingValues(vertical = 6.dp), onClick = { onTrackClick(track.id) }, onMoreClick = { contextTrack = track }, onLongClick = { contextTrack = track })
                            val trackIndex = uiState.tracks.indexOfFirst { it.id == track.id }
                            if (trackHasDivider(trackIndex, uiState.tracks.lastIndex)) {
                                HorizontalDivider(
                                    color = colors.borderGlass,
                                    modifier = Modifier.width(trackDividerWidth).align(Alignment.CenterHorizontally),
                                )
                            }
                        }
                    }
                }
                searchSection(R.string.library_albums, uiState.albums, { it.id }, topPadding = 32.dp) { album ->
                    val tracks = uiState.allTracks.filter { it.albumId == album.id }
                    AlbumContextMenu(
                        tracks = tracks, expanded = contextAlbumId == album.id, onDismiss = { contextAlbumId = null }, playbackQueue = playbackQueue,
                        onPlayNext = onAlbumPlayNext, onAddToQueue = onAlbumAddToQueue, onAddToFavorites = onAlbumAddToFavorites,
                        onBottomSheetRequested = onTrackContextBottomSheet,
                    ) {
                        DiscCard(
                            title = album.title,
                            subtitle = album.artist,
                            artworkPath = album.artworkPath,
                            fallbackSymbol = MaterialSymbols.Album,
                            onClick = { onAlbumClick(album.id) },
                            onLongClick = { contextAlbumId = album.id },
                        )
                    }
                }
                searchSection(R.string.library_artists, uiState.artists, { it.id }, topPadding = 16.dp) { artist ->
                    DiscCard(artist.name, "", artworkPath = artist.artworkPath, fallbackSymbol = MaterialSymbols.Person, artworkShape = CircleShape, onClick = { onArtistClick(artist.id) })
                }
                searchSection(R.string.library_playlists, uiState.playlists, { it.id }, topPadding = 16.dp) { playlist ->
                    DiscCard(
                        title = playlist.name,
                        subtitle = "",
                        artworkPath = uiState.allTracks.firstOrNull { it.id in playlist.trackIds }?.artworkPath,
                        fallbackSymbol = MaterialSymbols.QueueMusic,
                        onClick = { onPlaylistClick(playlist.id) },
                    )
                }
                searchSection(R.string.library_composers, uiState.composers, { it.id }, topPadding = 16.dp) { composer ->
                    DiscCard(composer.name, "", artworkPath = composer.artworkPath, fallbackSymbol = MaterialSymbols.StylusFountainPen, artworkShape = CircleShape, onClick = { onComposerClick(composer.id) })
                }
            }
        }
    }
    }
}

private fun <T> LazyListScope.searchSection(
    title: Int, values: List<T>, key: (T) -> String, rows: Int = 1, topPadding: androidx.compose.ui.unit.Dp = 16.dp, row: @Composable (T) -> Unit,
) {
    if (values.isEmpty()) return
    item("section_$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = LocalAirmedyColors.current.textMuted,
            modifier = Modifier.padding(start = 24.dp, top = topPadding, bottom = 12.dp),
        )
    }
    item("section_content_$title") {
        if (rows == 3) {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().height(183.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(values, key = { searchItemKey(title, key(it)) }) { item ->
                    Box(Modifier.widthIn(max = SearchTrackColumnMaxWidth)) { row(item) }
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems(values, key = { searchItemKey(title, key(it)) }) { item ->
                    Box(Modifier.width(160.dp)) { row(item) }
                }
            }
        }
    }
}

internal fun searchItemKey(section: Int, id: String): String = "search_$section:$id"

private val SearchTrackColumnMaxWidth = 320.dp

internal fun trackHasDivider(index: Int, lastIndex: Int): Boolean = index >= 0 && index < lastIndex && index % 3 != 2

internal const val SearchEmptyStateTag = "search-empty-state"

@Composable private fun SearchEmptyState(title: Int, description: Int) = Box(
    Modifier.padding(24.dp).testTag(SearchEmptyStateTag),
) {
    HeroCard(
        title = stringResource(title),
        description = stringResource(description),
        symbol = MaterialSymbols.Search,
    )
}
