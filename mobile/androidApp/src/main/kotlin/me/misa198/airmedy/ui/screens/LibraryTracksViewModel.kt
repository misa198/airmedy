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
import me.misa198.airmedy.player.MaxPlaybackQueueSize
import me.misa198.airmedy.sync.LibraryTrack
import kotlin.random.Random

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
            .sortedWith(compareByDescending<LibraryTrack> { it.createdAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }.thenBy { it.id })
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
        playFromQueue(trackId, uiState.value.tracks, "Track row")
    }

    fun playRecentTrack(trackId: String) {
        playFromQueue(trackId, uiState.value.recentTracks, "Recently added track")
    }

    fun playAll(shuffle: Boolean) {
        collectionPlaybackRequestFor(uiState.value.tracks.map { it.id }, shuffle)?.let { request ->
            if (shuffle) playbackController.shuffle(request) else playbackController.play(request)
        }
    }

    private fun playFromQueue(trackId: String, queueTracks: List<LibraryTrack>, source: String) {
        playbackRequestFor(queueTracks, trackId)?.let { request ->
            Log.d(PlaybackLogTag, "$source clicked id=$trackId queueSize=${request.trackIds.size} startIndex=${request.startIndex}")
            playbackController.play(request)
        } ?: Log.w(PlaybackLogTag, "$source click ignored: id=$trackId is not in its visible queue")
    }
}

internal fun playbackRequestFor(tracks: List<LibraryTrack>, trackId: String): PlaybackRequest? {
    val startIndex = tracks.indexOfFirst { it.id == trackId }
    return startIndex.takeIf { it >= 0 }?.let { PlaybackRequest(tracks.map { track -> track.id }, it) }
}

/** Bounds a collection action before it crosses the Android service Intent boundary. */
internal fun collectionPlaybackRequestFor(
    trackIds: List<String>,
    shuffle: Boolean,
    random: Random = Random.Default,
): PlaybackRequest? {
    val distinctIds = trackIds.distinct()
    val selected = if (shuffle) {
        distinctIds.shuffled(random).take(MaxPlaybackQueueSize)
    } else {
        distinctIds.take(MaxPlaybackQueueSize)
    }
    return selected.takeIf(List<String>::isNotEmpty)?.let(::PlaybackRequest)
}

internal fun sortTracks(
    tracks: List<LibraryTrack>,
    option: TrackSortOption,
    order: SortOrder,
): List<LibraryTrack> {
    val comparator = when (option) {
        TrackSortOption.Name -> compareBy<LibraryTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortArtists }
            .thenBy { it.id }
        TrackSortOption.Artist -> compareBy<LibraryTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.sortArtists }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }
            .thenBy { it.id }
        TrackSortOption.PlayCount -> compareBy<LibraryTrack> { it.playCount }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }
            .thenBy { it.id }
        TrackSortOption.DateAdded -> compareBy<LibraryTrack> { it.createdAt }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }
            .thenBy { it.id }
    }

    val sorted = tracks.sortedWith(comparator)
    return if (order == SortOrder.Descending) {
        sorted.reversed()
    } else {
        sorted
    }
}
