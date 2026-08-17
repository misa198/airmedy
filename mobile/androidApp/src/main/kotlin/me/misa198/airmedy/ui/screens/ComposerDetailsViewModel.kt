package me.misa198.airmedy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.misa198.airmedy.player.PlaybackController
import me.misa198.airmedy.player.PlaybackRequest
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryComposer
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.metadataObject

data class ComposerDetailsUiState(
    val composer: LibraryComposer? = null,
    val albums: List<LibraryAlbum> = emptyList(),
    val tracks: List<LibraryTrack> = emptyList(),
    internal val allComposers: List<LibraryComposer> = emptyList(),
    internal val allAlbums: List<LibraryAlbum> = emptyList(),
)

internal class ComposerDetailsViewModel(
    syncStore: AndroidLibrarySyncStore,
    private val playbackController: PlaybackController,
) : ViewModel() {
    class Factory(
        private val store: AndroidLibrarySyncStore,
        private val playback: PlaybackController,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ComposerDetailsViewModel(store, playback) as T
    }

    val uiState: StateFlow<ComposerDetailsUiState> = combine(
        syncStore.composers,
        syncStore.albums,
        syncStore.tracks,
    ) { composers, albums, tracks ->
        ComposerDetailsUiState(
            tracks = tracks,
            allComposers = composers,
            allAlbums = albums,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComposerDetailsUiState())

    fun play(composerId: String, shuffle: Boolean) {
        val tracks = composerDetailsUiStateFor(uiState.value, composerId).tracks
        if (tracks.isNotEmpty()) {
            val request = PlaybackRequest(tracks.map { it.id }, 0)
            if (shuffle) playbackController.shuffle(request) else playbackController.play(request)
        }
    }

    fun orderedTrackIds(composerId: String): List<String> =
        composerDetailsUiStateFor(uiState.value, composerId).tracks.map(LibraryTrack::id)
}

internal fun composerDetailsUiStateFor(
    state: ComposerDetailsUiState,
    composerId: String,
): ComposerDetailsUiState {
    val composer = state.allComposers.firstOrNull { it.id == composerId }
    val composerTracks = state.tracks.filter { composerId in it.composerIds() }
    val tracksByAlbumId = composerTracks.groupBy { it.composerAlbumIdentifier() }
    val albums = state.allAlbums
        .filter { it.id in tracksByAlbumId }
        .sortedWith(
            compareBy<LibraryAlbum, String>(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortArtist }
                .thenBy { it.id },
        )
    val tracks = albums.flatMap { album ->
        tracksByAlbumId[album.id].orEmpty().sortedWith(composerAlbumTrackComparator)
    }
    return ComposerDetailsUiState(composer = composer, albums = albums, tracks = tracks)
}

private val composerAlbumTrackComparator = compareBy<LibraryTrack> {
    if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE
}.thenBy {
    if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE
}.thenBy { it.syncOrder }

private fun LibraryTrack.composerAlbumIdentifier(): String? = albumId.takeIf(String::isNotBlank)
    ?: metadataObject()?.get("album")
        ?.let { it as? JsonObject }
        ?.get("id")
        ?.let { it as? JsonPrimitive }
        ?.content
        ?.takeIf(String::isNotBlank)

private fun LibraryTrack.composerIds(): Set<String> {
    val root = metadataObject() ?: return emptySet()
    val ids = linkedSetOf<String>()

    fun addComposer(rawId: String?, rawName: String) {
        val name = rawName.trim()
        val id = rawId?.trim()?.takeIf(String::isNotBlank) ?: name.lowercase()
        if (name.isNotBlank()) ids += id
    }

    fun parseElement(element: kotlinx.serialization.json.JsonElement?) {
        when (element) {
            is JsonArray -> element.forEach(::parseElement)
            is JsonObject -> addComposer(
                rawId = (element["id"] as? JsonPrimitive)?.content,
                rawName = (element["name"] as? JsonPrimitive)?.content
                    ?: (element["title"] as? JsonPrimitive)?.content.orEmpty(),
            )
            is JsonPrimitive -> element.content.split(',', ';', '/').forEach { name -> addComposer(null, name) }
            else -> Unit
        }
    }

    parseElement(root["composers"])
    parseElement(root["composer"])
    parseElement(root["raw_composer_names"])
    parseElement(root["raw_composers"])
    parseElement(root["composer_names"])
    return ids
}
