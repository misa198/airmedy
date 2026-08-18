package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.LibraryVirtualList
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.PlaylistRow
import me.misa198.airmedy.ui.components.PlaylistContextMenu
import me.misa198.airmedy.ui.components.AirmedyDialog
import me.misa198.airmedy.ui.components.AirmedyPillButtonVariant

@Composable
internal fun LibraryPlaylistsContent(
    uiState: LibraryPlaylistsUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    contentPadding: PaddingValues = PaddingValues(),
    onPlaylistClick: (String) -> Unit = {},
    onPlaylistPlayNext: (List<String>) -> Unit = {},
    onPlaylistAddToQueue: (List<String>) -> Unit = {},
    onPlaylistUpdate: (String, String, android.net.Uri?, Boolean) -> Unit = { _, _, _, _ -> },
    onPlaylistDelete: (String) -> Unit = {},
) {
    val listPadding = remember(contentPadding) { PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()) }
    var contextPlaylistId by remember { mutableStateOf<String?>(null) }
    var editingPlaylist by remember { mutableStateOf<PlaylistListItem?>(null) }
    var deletingPlaylist by remember { mutableStateOf<PlaylistListItem?>(null) }
    LibraryVirtualList(
        items = uiState.playlists,
        key = { it.id },
        contentType = "playlist_row",
        listState = listState,
        contentPadding = listPadding,
        modifier = modifier,
        dividerTestTag = "playlist-row-divider",
        emptyContent = {
            Column(Modifier.fillMaxSize().padding(contentPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroCard(
                    symbol = MaterialSymbols.QueueMusic,
                    title = stringResource(R.string.playlists_empty_title),
                    description = stringResource(R.string.playlists_empty_description),
                )
            }
        },
    ) { playlist ->
        PlaylistContextMenu(
            playlistId = playlist.id,
            trackIds = playlist.trackIds,
            expanded = contextPlaylistId == playlist.id,
            onDismiss = { if (contextPlaylistId == playlist.id) contextPlaylistId = null },
            onPlayNext = onPlaylistPlayNext,
            onAddToQueue = onPlaylistAddToQueue,
            onEdit = { editingPlaylist = playlist },
            onDelete = { deletingPlaylist = playlist },
        ) {
            PlaylistRow(
                playlist.id,
                if (playlist.isFavorite) stringResource(R.string.library_favorites) else playlist.name,
                playlist.artworkPaths,
                syncFailed = playlist.syncFailed,
                onClick = { onPlaylistClick(playlist.id) },
                onLongClick = { contextPlaylistId = playlist.id },
            )
        }
    }
    editingPlaylist?.let { playlist ->
        EditPlaylistBottomSheet(
            initialName = playlist.name,
            artworkPath = playlist.customArtworkPath,
            showNameInput = !playlist.isFavorite,
            onDismiss = { editingPlaylist = null },
            onSave = { name, artwork, clearArtwork -> onPlaylistUpdate(playlist.id, name, artwork, clearArtwork); editingPlaylist = null },
        )
    }
    deletingPlaylist?.let { playlist ->
        AirmedyDialog(
            title = stringResource(R.string.playlist_delete_confirm_title),
            description = stringResource(R.string.playlist_delete_confirm_description),
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { deletingPlaylist = null },
            confirmLabel = stringResource(R.string.playlist_delete),
            onConfirm = { onPlaylistDelete(playlist.id); deletingPlaylist = null },
            confirmVariant = AirmedyPillButtonVariant.Destructive,
        )
    }
}
