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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.ArtistRow
import me.misa198.airmedy.ui.components.ArtistContextMenu
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.LibraryVirtualList
import me.misa198.airmedy.ui.components.LibraryTextFilter
import me.misa198.airmedy.ui.components.MaterialSymbols
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest

@Composable
internal fun LibraryArtistsContent(
    uiState: LibraryArtistsUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState = remember(uiState.sortOption, uiState.sortOrder) { LazyListState() },
    contentPadding: PaddingValues = PaddingValues(),
    onArtistClick: ((me.misa198.airmedy.sync.LibraryArtist) -> Unit)? = null,
    onFilterQueryChange: (String) -> Unit = {},
    orderedTrackIdsForArtist: (String) -> List<String> = { emptyList() },
    onArtistPlayNext: (List<String>) -> Unit = {},
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
    hazeState: HazeState? = null,
) {
    var contextArtistId by remember { mutableStateOf<String?>(null) }
    val listPadding = remember(contentPadding) {
        PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
            start = 0.dp,
            end = 0.dp,
        )
    }
    LibraryVirtualList(
        items = uiState.artists,
        key = { artist -> artist.id },
        contentType = "artist_row",
        listState = listState,
        contentPadding = listPadding,
        modifier = modifier,
        dividerTestTag = "artist-row-divider",
        filterKey = "artists",
        filterActive = uiState.filterQuery.isNotBlank(),
        filterContent = { showPlaceholderAndLeadingSymbol ->
            LibraryTextFilter(
                value = uiState.filterQuery,
                onValueChange = onFilterQueryChange,
                placeholder = stringResource(R.string.filter_placeholder_search),
                showPlaceholderAndLeadingSymbol = showPlaceholderAndLeadingSymbol,
            )
        },
        emptyContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeroCard(
                    title = stringResource(if (uiState.filterQuery.isBlank()) R.string.artists_empty_title else R.string.artists_no_match_title),
                    description = stringResource(if (uiState.filterQuery.isBlank()) R.string.artists_empty_description else R.string.filter_no_match_description),
                    symbol = MaterialSymbols.People,
                )
            }
        },
    ) { artist ->
        ArtistContextMenu(
            trackIds = orderedTrackIdsForArtist(artist.id),
            expanded = contextArtistId == artist.id,
            onDismiss = { if (contextArtistId == artist.id) contextArtistId = null },
            onPlayNext = onArtistPlayNext,
            onBottomSheetRequested = onTrackContextBottomSheet,
            addToPlaylistOnly = true,
            hazeState = hazeState,
        ) {
            ArtistRow(
                name = artist.name,
                artworkPath = artist.artworkPath,
                onClick = onArtistClick?.let { callback -> { callback(artist) } },
                onLongClick = { contextArtistId = artist.id },
            )
        }
    }
}
