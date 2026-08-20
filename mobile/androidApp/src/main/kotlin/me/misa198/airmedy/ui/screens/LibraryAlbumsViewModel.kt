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
import kotlinx.coroutines.launch
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.libraryAlphabeticalComparator
import me.misa198.airmedy.player.PlaybackController

enum class AlbumSortOption {
    Name,
    Artist,
    DateAdded,
}

enum class AlbumLayoutMode(val storageValue: String) {
    List("list"),
    Grid("grid"),
    ;

    companion object {
        fun fromStorage(value: String?): AlbumLayoutMode = entries.firstOrNull { it.storageValue == value } ?: List
    }
}

data class LibraryAlbumsUiState(
    val isLoaded: Boolean = true,
    val albums: List<LibraryAlbum> = emptyList(),
    internal val tracks: List<LibraryTrack> = emptyList(),
    val filterQuery: String = "",
    val sortOption: AlbumSortOption = AlbumSortOption.Name,
    val sortOrder: SortOrder = SortOrder.Ascending,
    val layoutMode: AlbumLayoutMode = AlbumLayoutMode.List,
)

internal class LibraryAlbumsViewModel(
    syncStore: AndroidLibrarySyncStore,
    private val playbackController: PlaybackController,
    private val layoutStore: LibraryAlbumsLayoutStore,
) : ViewModel() {
    class Factory(
        private val syncStore: AndroidLibrarySyncStore,
        private val playbackController: PlaybackController,
        private val layoutStore: LibraryAlbumsLayoutStore,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryAlbumsViewModel(syncStore, playbackController, layoutStore) as T
    }

    private val sortOptionFlow = MutableStateFlow(AlbumSortOption.Name)
    private val sortOrderFlow = MutableStateFlow(SortOrder.Ascending)
    private val filterQueryFlow = MutableStateFlow("")

    private val sortedAlbumsState = combine(
        syncStore.albums, syncStore.tracks,
        sortOptionFlow,
        sortOrderFlow,
        filterQueryFlow,
    ) { albums, tracks, option, order, query ->
        LibraryAlbumsUiState(
            isLoaded = true,
            albums = sortAlbums(albums.filter { matchesLibraryTextFilter(query, it.title, it.artist) }, option, order),
            tracks = tracks,
            filterQuery = query,
            sortOption = option,
            sortOrder = order,
        )
    }

    val uiState: StateFlow<LibraryAlbumsUiState> = combine(
        sortedAlbumsState,
        layoutStore.layoutMode,
    ) { state, layoutMode ->
        state.copy(layoutMode = layoutMode)
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryAlbumsUiState(isLoaded = false),
    )

    fun setSortOption(option: AlbumSortOption) {
        sortOptionFlow.value = option
    }

    fun setFilterQuery(query: String) {
        filterQueryFlow.value = query
    }

    fun toggleSortOrder() {
        sortOrderFlow.value = if (sortOrderFlow.value == SortOrder.Ascending) SortOrder.Descending else SortOrder.Ascending
    }

    fun setLayoutMode(layoutMode: AlbumLayoutMode) {
        viewModelScope.launch { layoutStore.setLayoutMode(layoutMode) }
    }

    fun playAll(shuffle: Boolean) {
        collectionPlaybackRequestFor(albumCollectionTrackIdsFor(uiState.value), shuffle)?.let { request ->
            if (shuffle) playbackController.shuffle(request) else playbackController.play(request)
        }
    }
}

internal fun albumCollectionTrackIdsFor(state: LibraryAlbumsUiState): List<String> = state.albums.flatMap { album ->
    albumDetailsUiStateFor(
        AlbumDetailsUiState(albums = state.albums, tracks = state.tracks),
        album.id,
    ).tracks.map { track -> track.id }
}

internal fun sortAlbums(
    albums: List<LibraryAlbum>,
    option: AlbumSortOption,
    order: SortOrder,
): List<LibraryAlbum> {
    val comparator = when (option) {
        AlbumSortOption.Name -> compareBy<LibraryAlbum, String>(libraryAlphabeticalComparator) { it.sortTitle }
            .thenBy(libraryAlphabeticalComparator) { it.sortArtist }
            .thenBy { it.id }
        AlbumSortOption.Artist -> compareBy<LibraryAlbum, String>(libraryAlphabeticalComparator) { it.sortArtist }
            .thenBy(libraryAlphabeticalComparator) { it.sortTitle }
            .thenBy { it.id }
        AlbumSortOption.DateAdded -> compareBy<LibraryAlbum> { it.createdAt }
            .thenBy(libraryAlphabeticalComparator) { it.sortTitle }
            .thenBy { it.id }
    }
    return albums.sortedWith(comparator).let { if (order == SortOrder.Descending) it.reversed() else it }
}
