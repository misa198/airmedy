package me.misa198.airmedy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.screens.isFavorite

/** Album-specific behavior built on the shared anchored popup and action-list presentation. */
@Composable
internal fun AlbumContextMenu(
    tracks: List<LibraryTrack>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    onPlay: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onPlayNext: (List<String>) -> Unit = {},
    onAddToQueue: (List<String>) -> Unit = {},
    onAddToFavorites: (List<String>) -> Unit = {},
    onBottomSheetRequested: (TrackContextBottomSheetRequest) -> Unit = {},
    anchor: @Composable () -> Unit,
) {
    val trackIds = remember(tracks) { tracks.map(LibraryTrack::id) }
    val showAddToQueue = remember(trackIds, playbackQueue.activeTrackIds) { albumContextShowsAddToQueue(trackIds, playbackQueue) }
    val dismissAll = {
        onDismiss()
    }
    val closeAfter: ((() -> Unit) -> Unit) = { action -> action(); dismissAll() }
    AnchoredPopupMenu(
        expanded = expanded,
        onDismissRequest = dismissAll,
        modifier = modifier,
        width = 272.dp,
        offset = DpOffset(x = (-8).dp, y = 8.dp),
        hazeState = hazeState,
        anchor = anchor,
    ) {
        ContextActionMenu(buildList {
            add(ContextActionMenuEntry.Action(stringResource(R.string.player_play), MaterialSymbols.PlayArrow) { closeAfter(onPlay) })
            add(ContextActionMenuEntry.Action(stringResource(R.string.player_shuffle), MaterialSymbols.Shuffle) { closeAfter(onShuffle) })
            add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_play_next), MaterialSymbols.QueuePlayNext) { closeAfter { onPlayNext(trackIds) } })
            if (showAddToQueue) add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_add_to_queue), MaterialSymbols.AddToQueue) { closeAfter { onAddToQueue(trackIds) } })
            add(ContextActionMenuEntry.Divider)
            add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_add_to_favorites), MaterialSymbols.HeartPlus) {
                closeAfter { onAddToFavorites(tracks.filterNot(LibraryTrack::isFavorite).map(LibraryTrack::id)) }
            })
            add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_add_to_playlist), MaterialSymbols.PlaylistAdd) {
                dismissAll()
                onBottomSheetRequested(TrackContextBottomSheetRequest.Playlist(trackIds, addOnly = true))
            })
        })
    }
}

internal fun albumContextShowsAddToQueue(trackIds: List<String>, queue: PlaybackQueueSnapshot): Boolean =
    trackIds.isEmpty() || trackIds.any { it !in queue.activeTrackIds }
