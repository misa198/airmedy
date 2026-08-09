package me.misa198.airmedy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.LibraryGenre

enum class GenreSortOption {
    Name,
    DateAdded,
}

data class LibraryGenresUiState(
    val genres: List<LibraryGenre> = emptyList(),
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

    val uiState: StateFlow<LibraryGenresUiState> = combine(
        syncStore.genres,
        sortOptionFlow,
        sortOrderFlow,
    ) { genres, option, order ->
        LibraryGenresUiState(
            genres = sortGenres(genres, option, order),
            sortOption = option,
            sortOrder = order,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryGenresUiState(),
    )

    fun setSortOption(option: GenreSortOption) {
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

internal fun sortGenres(
    genres: List<LibraryGenre>,
    option: GenreSortOption,
    order: SortOrder,
): List<LibraryGenre> {
    val comparator = when (option) {
        GenreSortOption.Name -> compareBy<LibraryGenre, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        GenreSortOption.DateAdded -> compareBy<LibraryGenre> { it.createdAt }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    val sorted = genres.sortedWith(comparator)
    return if (order == SortOrder.Descending) sorted.reversed() else sorted
}
