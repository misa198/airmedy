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
import me.misa198.airmedy.sync.LibraryGenre
import me.misa198.airmedy.ui.libraryAlphabeticalComparator

enum class GenreSortOption {
    Name,
    DateAdded,
}

data class LibraryGenresUiState(
    val genres: List<LibraryGenre> = emptyList(),
    val filterQuery: String = "",
    val sortOption: GenreSortOption = GenreSortOption.Name,
    val sortOrder: SortOrder = SortOrder.Ascending,
)

internal class LibraryGenresViewModel(
    syncStore: AndroidLibrarySyncStore,
) : ViewModel() {
    class Factory(private val syncStore: AndroidLibrarySyncStore) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryGenresViewModel(syncStore) as T
    }

    private val sortOptionFlow = MutableStateFlow(GenreSortOption.Name)
    private val sortOrderFlow = MutableStateFlow(SortOrder.Ascending)
    private val filterQueryFlow = MutableStateFlow("")

    val uiState: StateFlow<LibraryGenresUiState> = combine(
        syncStore.genres,
        sortOptionFlow,
        sortOrderFlow,
        filterQueryFlow,
    ) { genres, option, order, query ->
        LibraryGenresUiState(
            genres = sortGenres(genres.filter { matchesLibraryTextFilter(query, it.name) }, option, order),
            filterQuery = query,
            sortOption = option,
            sortOrder = order,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryGenresUiState(),
    )

    fun setSortOption(option: GenreSortOption) {
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

internal fun sortGenres(
    genres: List<LibraryGenre>,
    option: GenreSortOption,
    order: SortOrder,
): List<LibraryGenre> {
    val comparator = when (option) {
        GenreSortOption.Name -> compareBy<LibraryGenre, String>(libraryAlphabeticalComparator) { it.sortName }
            .thenBy { it.id }
        GenreSortOption.DateAdded -> compareBy<LibraryGenre> { it.createdAt }
            .thenBy(libraryAlphabeticalComparator) { it.sortName }
            .thenBy { it.id }
    }
    val sorted = genres.sortedWith(comparator)
    return if (order == SortOrder.Descending) sorted.reversed() else sorted
}
