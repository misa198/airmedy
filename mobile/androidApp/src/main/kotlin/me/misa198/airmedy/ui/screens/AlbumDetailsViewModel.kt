package me.misa198.airmedy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.longOrNull
import me.misa198.airmedy.player.PlaybackController
import me.misa198.airmedy.player.PlaybackRequest
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.metadataObject

data class AlbumDetailsUiState(
    val album: LibraryAlbum? = null,
    val tracks: List<LibraryTrack> = emptyList(),
    internal val albums: List<LibraryAlbum> = emptyList(),
)

internal class AlbumDetailsViewModel(syncStore: AndroidLibrarySyncStore, private val playbackController: PlaybackController) : ViewModel() {
    class Factory(private val store: AndroidLibrarySyncStore, private val playback: PlaybackController) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AlbumDetailsViewModel(store, playback) as T
    }
    val uiState: StateFlow<AlbumDetailsUiState> = combine(syncStore.albums, syncStore.tracks) { albums, tracks ->
        AlbumDetailsUiState(tracks = tracks, albums = albums)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumDetailsUiState())

    fun play(albumId: String, shuffle: Boolean) {
        val tracks = albumDetailsUiStateFor(uiState.value, albumId).tracks
        if (tracks.isNotEmpty()) {
            val request = PlaybackRequest(tracks.map { it.id }, 0)
            if (shuffle) playbackController.shuffle(request) else playbackController.play(request)
        }
    }

    fun playTrack(albumId: String, trackId: String) {
        val tracks = albumDetailsUiStateFor(uiState.value, albumId).tracks
        albumPlaybackRequestFor(tracks, trackId)?.let(playbackController::play)
    }
}

internal fun albumPlaybackRequestFor(tracks: List<LibraryTrack>, trackId: String): PlaybackRequest? {
    val startIndex = tracks.indexOfFirst { it.id == trackId }
    return startIndex.takeIf { it >= 0 }?.let { PlaybackRequest(tracks.map { it.id }, it) }
}

internal fun albumDetailsUiStateFor(state: AlbumDetailsUiState, albumId: String): AlbumDetailsUiState {
    val tracks = state.tracks.filter { track ->
        track.albumId == albumId || track.metadataObject()
            ?.get("album")
            ?.let { it as? kotlinx.serialization.json.JsonObject }
            ?.get("id")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.content == albumId
    }.sortedWith(
        compareBy<LibraryTrack> { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
            .thenBy { it.syncOrder },
    )
    return AlbumDetailsUiState(state.albums.firstOrNull { it.id == albumId }, tracks)
}

internal fun albumTotalDurationSeconds(tracks: List<LibraryTrack>): Long = tracks.sumOf { track ->
    (track.metadataObject()?.get("duration") as? kotlinx.serialization.json.JsonPrimitive)
        ?.longOrNull
        ?.coerceAtLeast(0L)
        ?: 0L
}

internal fun formatAlbumTotalDuration(
    totalSeconds: Long,
    day: (Long) -> String,
    hour: (Long) -> String,
    minute: (Long) -> String,
    second: (Long) -> String,
): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val days = seconds / 86_400L
    val hours = seconds % 86_400L / 3_600L
    val minutes = seconds % 3_600L / 60L
    val remainder = seconds % 60L
    return when {
        days > 0 -> "${day(days)} ${hour(hours)}"
        hours > 0 -> "${hour(hours)} ${minute(minutes)}"
        minutes > 0 -> minute(minutes)
        else -> second(remainder)
    }
}
