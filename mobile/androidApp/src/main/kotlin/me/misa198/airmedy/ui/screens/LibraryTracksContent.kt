package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.LibraryVirtualList
import me.misa198.airmedy.ui.components.TrackRow
import me.misa198.airmedy.ui.components.TrackContextArtist
import me.misa198.airmedy.ui.components.TrackContextMenu

@Composable
internal fun LibraryTracksContent(
    uiState: LibraryTracksUiState,
    onSortOptionSelected: (TrackSortOption) -> Unit,
    onToggleSortOrder: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = remember(uiState.sortOption, uiState.sortOrder) { LazyListState() },
    contentPadding: PaddingValues = PaddingValues(),
    onTrackClick: ((LibraryTrack) -> Unit)? = null,
    onTrackMoreClick: ((LibraryTrack) -> Unit)? = null,
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    onTrackPlayNext: (LibraryTrack) -> Unit = {},
    onTrackAddToQueue: (LibraryTrack) -> Unit = {},
    onTrackFavoriteToggle: (LibraryTrack, Boolean) -> Unit = { _, _ -> },
    onTrackAlbumClick: (LibraryTrack) -> Unit = {},
    onTrackArtistClick: (TrackContextArtist) -> Unit = {},
) {
    var contextTrack by remember { mutableStateOf<LibraryTrack?>(null) }
    val listPadding = remember(contentPadding) {
        PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
            start = 0.dp,
            end = 0.dp,
        )
    }
    LibraryVirtualList(
        items = uiState.tracks,
        key = { track -> track.id.ifBlank { "${track.title}_${track.artists}" } },
        contentType = "track_row",
        listState = listState,
        contentPadding = listPadding,
        modifier = modifier,
        dividerTestTag = "track-row-divider",
        emptyContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeroCard(
                    title = stringResource(R.string.tracks_empty_title),
                    description = stringResource(R.string.tracks_empty_description),
                    symbol = MaterialSymbols.MusicNote,
                )
            }
        },
    ) { track ->
        val onItemClick = remember(onTrackClick, track) {
            if (onTrackClick != null) { { onTrackClick(track) } } else null
        }
        val onItemMoreClick = remember(onTrackMoreClick, track) {
            { onTrackMoreClick?.invoke(track); contextTrack = track }
        }
        TrackContextMenu(
            track = track,
            expanded = contextTrack?.id == track.id,
            onDismiss = { if (contextTrack?.id == track.id) contextTrack = null },
            playbackQueue = playbackQueue,
            onPlayNext = onTrackPlayNext,
            onAddToQueue = onTrackAddToQueue,
            onFavoriteChange = onTrackFavoriteToggle,
            onGoToAlbum = onTrackAlbumClick,
            onGoToArtist = onTrackArtistClick,
        ) {
            TrackRow(
                title = track.title,
                artist = track.artists,
                artworkPath = track.artworkPath,
                onClick = onItemClick,
                onMoreClick = onItemMoreClick,
                onLongClick = { contextTrack = track },
            )
        }
    }
}
