package me.misa198.airmedy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.player.PlaybackController
import me.misa198.airmedy.player.PlaybackRequest
import me.misa198.airmedy.player.PlaybackLogTag
import me.misa198.airmedy.sync.LibraryTrack

enum class TrackSortOption {
    Name,
    Artist,
    PlayCount,
    DateAdded,
}

enum class SortOrder {
    Ascending,
    Descending,
}

data class LibraryTracksUiState(
    val tracks: List<LibraryTrack> = emptyList(),
    val recentTracks: List<LibraryTrack> = emptyList(),
    val sortOption: TrackSortOption = TrackSortOption.Name,
    val sortOrder: SortOrder = SortOrder.Ascending,
)

internal class LibraryTracksViewModel(
    syncStore: AndroidLibrarySyncStore,
    private val playbackController: PlaybackController,
) : ViewModel() {
    class Factory(
        private val syncStore: AndroidLibrarySyncStore,
        private val playbackController: PlaybackController,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryTracksViewModel(syncStore, playbackController) as T
        }
    }

    private val sortOptionFlow = MutableStateFlow(TrackSortOption.Name)
    private val sortOrderFlow = MutableStateFlow(SortOrder.Ascending)

    val uiState: StateFlow<LibraryTracksUiState> = combine(
        syncStore.tracks,
        sortOptionFlow,
        sortOrderFlow,
    ) { rawTracks, option, order ->
        val sorted = sortTracks(rawTracks, option, order)
        val recent = rawTracks
            .sortedWith(compareByDescending<LibraryTrack> { it.createdAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            .take(50)
        LibraryTracksUiState(
            tracks = sorted,
            recentTracks = recent,
            sortOption = option,
            sortOrder = order,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryTracksUiState(),
    )

    fun setSortOption(option: TrackSortOption) {
        sortOptionFlow.value = option
    }

    fun setSortOrder(order: SortOrder) {
        sortOrderFlow.value = order
    }

    fun toggleSortOrder() {
        sortOrderFlow.value = if (sortOrderFlow.value == SortOrder.Ascending) {
            SortOrder.Descending
        } else {
            SortOrder.Ascending
        }
    }

    fun playTrack(trackId: String) {
        playbackRequestFor(uiState.value.tracks, trackId)?.let { request ->
            Log.d(PlaybackLogTag, "Track row clicked id=$trackId queueSize=${request.trackIds.size} startIndex=${request.startIndex}")
            playbackController.play(request)
        } ?: Log.w(PlaybackLogTag, "Track row click ignored: id=$trackId is not in visible tracks")
    }
}

internal fun playbackRequestFor(tracks: List<LibraryTrack>, trackId: String): PlaybackRequest? {
    val startIndex = tracks.indexOfFirst { it.id == trackId }
    return startIndex.takeIf { it >= 0 }?.let { PlaybackRequest(tracks.map { track -> track.id }, it) }
}

internal fun sortTracks(
    tracks: List<LibraryTrack>,
    option: TrackSortOption,
    order: SortOrder,
): List<LibraryTrack> {
    val comparator = when (option) {
        TrackSortOption.Name -> compareBy<LibraryTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.artists }
        TrackSortOption.Artist -> compareBy<LibraryTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.artists }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        TrackSortOption.PlayCount -> compareBy<LibraryTrack> { it.playCount }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        TrackSortOption.DateAdded -> compareBy<LibraryTrack> { it.createdAt }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    }

    val sorted = tracks.sortedWith(comparator)
    return if (order == SortOrder.Descending) {
        sorted.reversed()
    } else {
        sorted
    }
}
