package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.TrackRow

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember

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
) {
    if (uiState.tracks.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroCard(
                title = stringResource(R.string.tracks_empty_title),
                description = stringResource(R.string.tracks_empty_description),
                iconRes = LucideR.drawable.lucide_ic_music,
            )
        }
    } else {
        val listPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
            start = 0.dp,
            end = 0.dp,
        )

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding,
        ) {
            items(
                items = uiState.tracks,
                key = { track -> track.id.ifBlank { "${track.title}_${track.artists}" } },
                contentType = { "track_row" },
            ) { track ->
                TrackRow(
                    title = track.title,
                    artist = track.artists,
                    artworkPath = track.artworkPath,
                    onClick = { onTrackClick?.invoke(track) },
                    onMoreClick = { onTrackMoreClick?.invoke(track) },
                )
            }
        }
    }
}
