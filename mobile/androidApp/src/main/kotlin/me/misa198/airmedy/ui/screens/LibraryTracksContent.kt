package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.TrackRow
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

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
    val colors = LocalAirmedyColors.current
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
        val listPadding = remember(contentPadding) {
            PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
                start = 0.dp,
                end = 0.dp,
            )
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding,
        ) {
            itemsIndexed(
                items = uiState.tracks,
                key = { _, track -> track.id.ifBlank { "${track.title}_${track.artists}" } },
                contentType = { _, _ -> "track_row" },
            ) { index, track ->
                val onItemClick = remember(onTrackClick, track) {
                    if (onTrackClick != null) { { onTrackClick(track) } } else null
                }
                val onItemMoreClick = remember(onTrackMoreClick, track) {
                    if (onTrackMoreClick != null) { { onTrackMoreClick(track) } } else null
                }
                TrackRow(
                    title = track.title,
                    artist = track.artists,
                    artworkPath = track.artworkPath,
                    onClick = onItemClick,
                    onMoreClick = onItemMoreClick,
                )
                if (index < uiState.tracks.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(1.dp)
                            .background(colors.borderGlass)
                            .testTag("track-row-divider"),
                    )
                }
            }
        }
    }
}
