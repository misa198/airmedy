package me.misa198.airmedy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryTrack

enum class AlbumSortOption {
    Name,
    Artist,
    DateAdded,
}

data class LibraryAlbumsUiState(
    val albums: List<LibraryAlbum> = emptyList(),
    internal val tracks: List<LibraryTrack> = emptyList(),
    val sortOption: AlbumSortOption = AlbumSortOption.Name,
    val sortOrder: SortOrder = SortOrder.Ascending,
)

internal class LibraryAlbumsViewModel(
    syncStore: AndroidLibrarySyncStore,
) : ViewModel() {
    class Factory(private val syncStore: AndroidLibrarySyncStore) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryAlbumsViewModel(syncStore) as T
    }

    private val sortOptionFlow = MutableStateFlow(AlbumSortOption.Name)
    private val sortOrderFlow = MutableStateFlow(SortOrder.Ascending)

    val uiState: StateFlow<LibraryAlbumsUiState> = combine(
        syncStore.albums, syncStore.tracks,
        sortOptionFlow,
        sortOrderFlow,
    ) { albums, tracks, option, order ->
        LibraryAlbumsUiState(sortAlbums(albums, option, order), tracks, option, order)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryAlbumsUiState(),
    )

    fun setSortOption(option: AlbumSortOption) {
        sortOptionFlow.value = option
    }

    fun toggleSortOrder() {
        sortOrderFlow.value = if (sortOrderFlow.value == SortOrder.Ascending) SortOrder.Descending else SortOrder.Ascending
    }
}

internal fun sortAlbums(
    albums: List<LibraryAlbum>,
    option: AlbumSortOption,
    order: SortOrder,
): List<LibraryAlbum> {
    val comparator = when (option) {
        AlbumSortOption.Name -> compareBy<LibraryAlbum, String>(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortArtist }
            .thenBy { it.id }
        AlbumSortOption.Artist -> compareBy<LibraryAlbum, String>(String.CASE_INSENSITIVE_ORDER) { it.sortArtist }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }
            .thenBy { it.id }
        AlbumSortOption.DateAdded -> compareBy<LibraryAlbum> { it.createdAt }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }
            .thenBy { it.id }
    }
    return albums.sortedWith(comparator).let { if (order == SortOrder.Descending) it.reversed() else it }
}
