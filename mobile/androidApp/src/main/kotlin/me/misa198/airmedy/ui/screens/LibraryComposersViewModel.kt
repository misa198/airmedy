package me.misa198.airmedy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.LibraryComposer
import me.misa198.airmedy.ui.libraryAlphabeticalComparator

enum class ComposerSortOption {
    Name,
    DateAdded,
}

data class LibraryComposersUiState(
    val composers: List<LibraryComposer> = emptyList(),
    val filterQuery: String = "",
    val sortOption: ComposerSortOption = ComposerSortOption.Name,
    val sortOrder: SortOrder = SortOrder.Ascending,
)

internal class LibraryComposersViewModel(
    syncStore: AndroidLibrarySyncStore,
) : ViewModel() {
    class Factory(private val syncStore: AndroidLibrarySyncStore) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryComposersViewModel(syncStore) as T
        }
    }

    private val sortOptionFlow = MutableStateFlow(ComposerSortOption.Name)
    private val sortOrderFlow = MutableStateFlow(SortOrder.Ascending)
    private val filterQueryFlow = MutableStateFlow("")

    val uiState: StateFlow<LibraryComposersUiState> = combine(
        syncStore.composers,
        sortOptionFlow,
        sortOrderFlow,
        filterQueryFlow,
    ) { composers, option, order, query ->
        LibraryComposersUiState(
            composers = sortComposers(composers.filter { matchesLibraryTextFilter(query, it.name) }, option, order),
            filterQuery = query,
            sortOption = option,
            sortOrder = order,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryComposersUiState(),
    )

    fun setSortOption(option: ComposerSortOption) {
        sortOptionFlow.value = option
    }

    fun setFilterQuery(query: String) {
        filterQueryFlow.value = query
    }

    fun toggleSortOrder() {
        sortOrderFlow.value = if (sortOrderFlow.value == SortOrder.Ascending) {
            SortOrder.Descending
        } else {
            SortOrder.Ascending
        }
    }
}

internal fun sortComposers(
    composers: List<LibraryComposer>,
    option: ComposerSortOption,
    order: SortOrder,
): List<LibraryComposer> {
    val comparator = when (option) {
        ComposerSortOption.Name -> compareBy<LibraryComposer, String>(libraryAlphabeticalComparator) { it.sortName }
            .thenBy { it.id }
        ComposerSortOption.DateAdded -> compareBy<LibraryComposer> { it.createdAt }
            .thenBy(libraryAlphabeticalComparator) { it.sortName }
            .thenBy { it.id }
    }
    val sorted = composers.sortedWith(comparator)
    return if (order == SortOrder.Descending) sorted.reversed() else sorted
}
