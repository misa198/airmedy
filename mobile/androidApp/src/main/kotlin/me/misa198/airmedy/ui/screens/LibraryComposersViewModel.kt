package me.misa198.airmedy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.LibraryComposer

enum class ComposerSortOption {
    Name,
    DateAdded,
}

data class LibraryComposersUiState(
    val composers: List<LibraryComposer> = emptyList(),
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

    val uiState: StateFlow<LibraryComposersUiState> = combine(
        syncStore.composers,
        sortOptionFlow,
        sortOrderFlow,
    ) { composers, option, order ->
        LibraryComposersUiState(
            composers = sortComposers(composers, option, order),
            sortOption = option,
            sortOrder = order,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryComposersUiState(),
    )

    fun setSortOption(option: ComposerSortOption) {
        sortOptionFlow.value = option
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
        ComposerSortOption.Name -> compareBy<LibraryComposer, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        ComposerSortOption.DateAdded -> compareBy<LibraryComposer> { it.createdAt }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    val sorted = composers.sortedWith(comparator)
    return if (order == SortOrder.Descending) sorted.reversed() else sorted
}
