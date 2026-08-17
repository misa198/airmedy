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
import me.misa198.airmedy.sync.LibraryGenre
import me.misa198.airmedy.ui.components.GenreRow
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.LibraryVirtualList
import me.misa198.airmedy.ui.components.LibraryTextFilter
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.GenreContextMenu
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import dev.chrisbanes.haze.HazeState

@Composable
internal fun LibraryGenresContent(
    uiState: LibraryGenresUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState = remember(uiState.sortOption, uiState.sortOrder) { LazyListState() },
    contentPadding: PaddingValues = PaddingValues(),
    onGenreClick: (LibraryGenre) -> Unit = {},
    onFilterQueryChange: (String) -> Unit = {},
    orderedTrackIdsForGenre: (String) -> List<String> = { emptyList() },
    onGenrePlayNext: (List<String>) -> Unit = {},
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
    hazeState: HazeState? = null,
) {
    var contextGenreId by remember { mutableStateOf<String?>(null) }
    val listPadding = remember(contentPadding) {
        PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
            start = 0.dp,
            end = 0.dp,
        )
    }
    LibraryVirtualList(
        items = uiState.genres,
        key = { genre -> genre.id },
        contentType = "genre_row",
        listState = listState,
        contentPadding = listPadding,
        modifier = modifier,
        dividerTestTag = "genre-row-divider",
        filterKey = "genres",
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
                    title = stringResource(if (uiState.filterQuery.isBlank()) R.string.genres_empty_title else R.string.genres_no_match_title),
                    description = stringResource(if (uiState.filterQuery.isBlank()) R.string.genres_empty_description else R.string.filter_no_match_description),
                    symbol = MaterialSymbols.Label,
                )
            }
        },
    ) { genre ->
        GenreContextMenu(
            trackIds = orderedTrackIdsForGenre(genre.id),
            expanded = contextGenreId == genre.id,
            onDismiss = { if (contextGenreId == genre.id) contextGenreId = null },
            onPlayNext = onGenrePlayNext,
            onBottomSheetRequested = onTrackContextBottomSheet,
            addToPlaylistOnly = true,
            hazeState = hazeState,
        ) {
            GenreRow(
                name = genre.name,
                onClick = { onGenreClick(genre) },
                onLongClick = { contextGenreId = genre.id },
            )
        }
    }
}
