package me.misa198.airmedy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.screens.FavoritesPlaylistId

/** Playlist-specific actions shared by list-row long presses and the details hero. */
@Composable
internal fun PlaylistContextMenu(
    playlistId: String,
    trackIds: List<String>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    onPlayNext: (List<String>) -> Unit = {},
    onAddToQueue: (List<String>) -> Unit = {},
    onReorder: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    anchor: @Composable () -> Unit,
) {
    val playableIds = remember(trackIds) { trackIds.distinct().filter(String::isNotBlank) }
    val canDelete = playlistId != FavoritesPlaylistId
    fun closeAfter(action: () -> Unit) {
        action()
        onDismiss()
    }
    AnchoredPopupMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        width = 272.dp,
        offset = DpOffset(x = (-8).dp, y = 8.dp),
        hazeState = hazeState,
        anchor = anchor,
    ) {
        ContextActionMenu(buildList {
            add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_play_next), MaterialSymbols.QueuePlayNext) {
                closeAfter { onPlayNext(playableIds) }
            })
            add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_add_to_queue), MaterialSymbols.AddToQueue) {
                closeAfter { onAddToQueue(playableIds) }
            })
            add(ContextActionMenuEntry.Divider)
            if (canDelete) {
                add(ContextActionMenuEntry.Action(stringResource(R.string.playlist_reorder), MaterialSymbols.Menu) { closeAfter(onReorder) })
                add(ContextActionMenuEntry.Divider)
            }
            add(ContextActionMenuEntry.Action(stringResource(R.string.playlist_edit), MaterialSymbols.Edit) { closeAfter(onEdit) })
            if (canDelete) {
                add(ContextActionMenuEntry.Action(stringResource(R.string.playlist_delete), MaterialSymbols.Delete, destructive = true) { closeAfter(onDelete) })
            }
        })
    }
}
