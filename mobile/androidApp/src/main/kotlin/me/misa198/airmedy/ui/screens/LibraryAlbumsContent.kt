package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.AlbumRow
import me.misa198.airmedy.ui.components.AlbumContextMenu
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.LibraryVirtualList
import me.misa198.airmedy.ui.components.LibraryPlaybackActions
import me.misa198.airmedy.ui.components.LibraryTextFilter
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.player.PlaybackQueueSnapshot

@Composable
internal fun LibraryAlbumsContent(
    uiState: LibraryAlbumsUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState = remember(uiState.sortOption, uiState.sortOrder) { LazyListState() },
    contentPadding: PaddingValues = PaddingValues(),
    onAlbumClick: ((me.misa198.airmedy.sync.LibraryAlbum) -> Unit)? = null,
    hazeState: HazeState? = null,
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    onAlbumPlay: (String, Boolean) -> Unit = { _, _ -> },
    onAlbumPlayNext: (List<String>) -> Unit = {},
    onAlbumAddToQueue: (List<String>) -> Unit = {},
    onAlbumAddToFavorites: (List<String>) -> Unit = {},
    onPlayAll: (Boolean) -> Unit = {},
    onFilterQueryChange: (String) -> Unit = {},
) {
    var contextAlbumId by remember { mutableStateOf<String?>(null) }
    val listPadding = remember(contentPadding) {
        PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
            start = 0.dp,
            end = 0.dp,
        )
    }
    LibraryVirtualList(
        items = uiState.albums,
        key = { album -> album.id },
        contentType = "album_row",
        listState = listState,
        contentPadding = listPadding,
        modifier = modifier,
        dividerTestTag = "album-row-divider",
        filterKey = "albums",
        filterActive = uiState.filterQuery.isNotBlank(),
        filterContent = { showPlaceholderAndLeadingSymbol ->
            LibraryTextFilter(
                value = uiState.filterQuery,
                onValueChange = onFilterQueryChange,
                placeholder = stringResource(R.string.filter_placeholder_search),
                showPlaceholderAndLeadingSymbol = showPlaceholderAndLeadingSymbol,
            )
        },
        leadingContent = {
            LibraryPlaybackActions(
                playLabel = stringResource(R.string.player_play),
                shuffleLabel = stringResource(R.string.player_shuffle),
                onPlay = { onPlayAll(false) },
                onShuffle = { onPlayAll(true) },
                hazeState = hazeState,
            )
        },
        emptyContent = {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeroCard(
                    title = stringResource(if (uiState.filterQuery.isBlank()) R.string.albums_empty_title else R.string.albums_no_match_title),
                    description = stringResource(if (uiState.filterQuery.isBlank()) R.string.albums_empty_description else R.string.filter_no_match_description),
                    symbol = MaterialSymbols.Album,
                )
            }
        },
    ) { album ->
        val tracks = remember(album.id, uiState.tracks) {
            albumDetailsUiStateFor(AlbumDetailsUiState(albums = uiState.albums, tracks = uiState.tracks), album.id).tracks
        }
        AlbumContextMenu(
            tracks = tracks,
            expanded = contextAlbumId == album.id,
            onDismiss = { if (contextAlbumId == album.id) contextAlbumId = null },
            hazeState = hazeState,
            playbackQueue = playbackQueue,
            onPlay = { onAlbumPlay(album.id, false) },
            onShuffle = { onAlbumPlay(album.id, true) },
            onPlayNext = onAlbumPlayNext,
            onAddToQueue = onAlbumAddToQueue,
            onAddToFavorites = onAlbumAddToFavorites,
        ) {
            AlbumRow(
                title = album.title,
                artist = album.artist.ifBlank { stringResource(R.string.album_unknown_artist) },
                artworkPath = album.artworkPath,
                onClick = onAlbumClick?.let { callback -> { callback(album) } },
                onLongClick = { contextAlbumId = album.id },
            )
        }
    }
}
