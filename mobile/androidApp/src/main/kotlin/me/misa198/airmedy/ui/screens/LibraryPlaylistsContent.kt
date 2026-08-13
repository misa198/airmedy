package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.LibraryVirtualList
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.PlaylistRow

@Composable
internal fun LibraryPlaylistsContent(
    uiState: LibraryPlaylistsUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    contentPadding: PaddingValues = PaddingValues(),
    onPlaylistClick: (String) -> Unit = {},
) {
    val listPadding = remember(contentPadding) { PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()) }
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
        PlaylistRow(
            playlist.id,
            if (playlist.isFavorite) stringResource(R.string.library_favorites) else playlist.name,
            playlist.artworkPaths,
            syncFailed = playlist.syncFailed,
            onClick = { onPlaylistClick(playlist.id) },
        )
    }
}
