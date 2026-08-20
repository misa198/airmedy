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
import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.ui.libraryAlphabeticalComparator

enum class ArtistSortOption {
    Name,
    DateAdded,
}

data class LibraryArtistsUiState(
    val artists: List<LibraryArtist> = emptyList(),
    val filterQuery: String = "",
    val sortOption: ArtistSortOption = ArtistSortOption.Name,
    val sortOrder: SortOrder = SortOrder.Ascending,
)

internal class LibraryArtistsViewModel(
    syncStore: AndroidLibrarySyncStore,
) : ViewModel() {
    class Factory(private val syncStore: AndroidLibrarySyncStore) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryArtistsViewModel(syncStore) as T
        }
    }

    private val sortOptionFlow = MutableStateFlow(ArtistSortOption.Name)
    private val sortOrderFlow = MutableStateFlow(SortOrder.Ascending)
    private val filterQueryFlow = MutableStateFlow("")

    val uiState: StateFlow<LibraryArtistsUiState> = combine(
        syncStore.artists,
        sortOptionFlow,
        sortOrderFlow,
        filterQueryFlow,
    ) { artists, option, order, query ->
        LibraryArtistsUiState(
            artists = sortArtists(artists.filter { matchesLibraryTextFilter(query, it.name) }, option, order),
            filterQuery = query,
            sortOption = option,
            sortOrder = order,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryArtistsUiState(),
    )

    fun setSortOption(option: ArtistSortOption) {
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

internal fun sortArtists(
    artists: List<LibraryArtist>,
    option: ArtistSortOption,
    order: SortOrder,
): List<LibraryArtist> {
    val comparator = when (option) {
        ArtistSortOption.Name -> compareBy<LibraryArtist, String>(libraryAlphabeticalComparator) { it.sortName }
            .thenBy { it.id }
        ArtistSortOption.DateAdded -> compareBy<LibraryArtist> { it.createdAt }
            .thenBy(libraryAlphabeticalComparator) { it.sortName }
            .thenBy { it.id }
    }
    val sorted = artists.sortedWith(comparator)
    return if (order == SortOrder.Descending) sorted.reversed() else sorted
}
