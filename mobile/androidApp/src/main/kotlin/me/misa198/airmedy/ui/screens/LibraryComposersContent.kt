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
import me.misa198.airmedy.ui.components.ComposerRow
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.LibraryVirtualList
import me.misa198.airmedy.ui.components.LibraryTextFilter
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.sync.LibraryComposer
import dev.chrisbanes.haze.HazeState

@Composable
internal fun LibraryComposersContent(
    uiState: LibraryComposersUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState = remember(uiState.sortOption, uiState.sortOrder) { LazyListState() },
    contentPadding: PaddingValues = PaddingValues(),
    onComposerClick: (LibraryComposer) -> Unit = {},
    onFilterQueryChange: (String) -> Unit = {},
    hazeState: HazeState? = null,
) {
    val listPadding = remember(contentPadding) {
        PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
            start = 0.dp,
            end = 0.dp,
        )
    }
    LibraryVirtualList(
        items = uiState.composers,
        key = { composer -> composer.id },
        contentType = "composer_row",
        listState = listState,
        contentPadding = listPadding,
        modifier = modifier,
        dividerTestTag = "composer-row-divider",
        filterKey = "composers",
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
                    title = stringResource(if (uiState.filterQuery.isBlank()) R.string.composers_empty_title else R.string.composers_no_match_title),
                    description = stringResource(if (uiState.filterQuery.isBlank()) R.string.composers_empty_description else R.string.filter_no_match_description),
                    symbol = MaterialSymbols.StylusFountainPen,
                )
            }
        },
    ) { composer ->
        ComposerRow(name = composer.name, onClick = { onComposerClick(composer) })
    }
}
