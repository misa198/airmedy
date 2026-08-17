package me.misa198.airmedy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R

/** Context actions for the complete, ordered track scope of an artist. */
@Composable
internal fun ArtistContextMenu(
    trackIds: List<String>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: (List<String>) -> Unit = {},
    onBottomSheetRequested: (TrackContextBottomSheetRequest) -> Unit = {},
    addToPlaylistOnly: Boolean = false,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    anchor: @Composable () -> Unit,
) {
    val orderedTrackIds = remember(trackIds) { trackIds.toList() }
    val dismissAll = { onDismiss() }
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
        ContextActionMenu(
            listOf(
                ContextActionMenuEntry.Action(stringResource(R.string.track_context_play_next), MaterialSymbols.QueuePlayNext) {
                    closeAfter { onPlayNext(orderedTrackIds) }
                },
                ContextActionMenuEntry.Action(stringResource(R.string.track_context_add_to_playlist), MaterialSymbols.PlaylistAdd) {
                    dismissAll()
                    onBottomSheetRequested(TrackContextBottomSheetRequest.Playlist(orderedTrackIds, addOnly = addToPlaylistOnly))
                },
            ),
        )
    }
}
