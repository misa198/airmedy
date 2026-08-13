package me.misa198.airmedy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibrarySyncProtocol
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.PlaylistMutation
import me.misa198.airmedy.sync.PlaylistMutationOperation
import me.misa198.airmedy.sync.PlaylistMutationPayload
import java.util.UUID

internal data class PlaylistListItem(
    val id: String,
    val name: String,
    val artworkPaths: List<String> = emptyList(),
    val syncFailed: Boolean = false,
) { val isFavorite: Boolean get() = id == FavoritesPlaylistId }

internal data class LibraryPlaylistsUiState(val playlists: List<PlaylistListItem> = emptyList())

internal class LibraryPlaylistsViewModel(syncStore: AndroidLibrarySyncStore) : ViewModel() {
    class Factory(private val syncStore: AndroidLibrarySyncStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryPlaylistsViewModel(syncStore) as T
    }

    private val syncStore = syncStore
    private val _createdPlaylistIds = MutableSharedFlow<String>()
    val createdPlaylistIds: SharedFlow<String> = _createdPlaylistIds.asSharedFlow()

    val uiState: StateFlow<LibraryPlaylistsUiState> = combine(
        syncStore.playlists,
        syncStore.tracks,
        syncStore.artworkPaths,
    ) { playlists, tracks, artworkPaths ->
        LibraryPlaylistsUiState(playlistsWithFavorites(playlists).map { playlist ->
            PlaylistListItem(playlist.id, playlist.name, playlistArtworkPaths(playlist, tracks, artworkPaths), playlist.syncFailed)
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPlaylistsUiState())

    fun createPlaylist(rawName: String) {
        val name = rawName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val playlistId = UUID.randomUUID().toString()
            syncStore.createLocalPlaylist(
                PlaylistMutation(
                    mutationId = UUID.randomUUID().toString(),
                    playlistId = playlistId,
                    operation = PlaylistMutationOperation.CREATE,
                    updatedAt = System.currentTimeMillis(),
                    payload = PlaylistMutationPayload(name = name),
                ),
            )
            _createdPlaylistIds.emit(playlistId)
        }
    }
}

internal const val FavoritesPlaylistId = "favorites"

internal fun playlistsWithFavorites(playlists: List<LibraryPlaylist>): List<LibraryPlaylist> {
    val favorites = playlists.firstOrNull { it.id == FavoritesPlaylistId }
        ?: LibraryPlaylist(FavoritesPlaylistId, "", emptyList(), "{}")
    return listOf(favorites) + playlists.filterNot { it.id == FavoritesPlaylistId }
}

internal fun playlistArtworkPaths(
    playlist: LibraryPlaylist,
    tracks: List<LibraryTrack>,
    artworkPaths: Map<String, String>,
): List<String> {
    playlistArtworkKey(playlist.metadataJson)?.let { key ->
        artworkPaths[key]?.let { return listOf(it) }
    }
    val tracksById = tracks.associateBy { it.id }
    return playlist.trackIds.asSequence()
        .mapNotNull { tracksById[it] }
        .mapNotNull { track -> track.artworkKey?.let { key -> key to track.artworkPath } }
        .filter { (_, path) -> !path.isNullOrBlank() }
        .distinctBy { (key, _) -> key }
        .take(4)
        .map { (_, path) -> path!! }
        .toList()
}

private fun playlistArtworkKey(metadataJson: String): String? = runCatching {
    val root = LibrarySyncProtocol.json.parseToJsonElement(metadataJson) as? JsonObject
    val playlist = root?.get("playlist") as? JsonObject ?: root
    (playlist?.get("artwork_key") as? JsonPrimitive)?.contentOrNull
}.getOrNull()?.takeIf { it.isNotBlank() }
