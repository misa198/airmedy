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
import me.misa198.airmedy.sync.LibraryGenre
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.metadataObject

data class GenreDetailsUiState(
    val genre: LibraryGenre? = null,
    val albums: List<LibraryAlbum> = emptyList(),
    val tracks: List<LibraryTrack> = emptyList(),
    internal val allGenres: List<LibraryGenre> = emptyList(),
    internal val allAlbums: List<LibraryAlbum> = emptyList(),
)

internal class GenreDetailsViewModel(
    syncStore: AndroidLibrarySyncStore,
    private val playbackController: PlaybackController,
) : ViewModel() {
    class Factory(
        private val store: AndroidLibrarySyncStore,
        private val playback: PlaybackController,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GenreDetailsViewModel(store, playback) as T
    }

    val uiState: StateFlow<GenreDetailsUiState> = combine(
        syncStore.genres,
        syncStore.albums,
        syncStore.tracks,
    ) { genres, albums, tracks ->
        GenreDetailsUiState(
            tracks = tracks,
            allGenres = genres,
            allAlbums = albums,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenreDetailsUiState())

    fun play(genreId: String, shuffle: Boolean) {
        val tracks = genreDetailsUiStateFor(uiState.value, genreId).tracks
        if (tracks.isNotEmpty()) {
            val request = PlaybackRequest(tracks.map { it.id }, 0)
            if (shuffle) playbackController.shuffle(request) else playbackController.play(request)
        }
    }
}

internal fun genreDetailsUiStateFor(
    state: GenreDetailsUiState,
    genreId: String,
): GenreDetailsUiState {
    val genre = state.allGenres.firstOrNull { it.id == genreId }
    val genreTracks = state.tracks.filter { genreId in it.genreIds() }
    val tracksByAlbumId = genreTracks.groupBy { it.genreAlbumIdentifier() }
    val albums = state.allAlbums
        .filter { it.id in tracksByAlbumId }
        .sortedWith(
            compareBy<LibraryAlbum, String>(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortArtist }
                .thenBy { it.id },
        )
    val tracks = albums.flatMap { album ->
        tracksByAlbumId[album.id].orEmpty().sortedWith(genreAlbumTrackComparator)
    }
    return GenreDetailsUiState(genre = genre, albums = albums, tracks = tracks)
}

private val genreAlbumTrackComparator = compareBy<LibraryTrack> {
    if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE
}.thenBy {
    if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE
}.thenBy { it.syncOrder }

private fun LibraryTrack.genreAlbumIdentifier(): String? = albumId.takeIf(String::isNotBlank)
    ?: metadataObject()?.get("album")
        ?.let { it as? JsonObject }
        ?.get("id")
        ?.let { it as? JsonPrimitive }
        ?.content
        ?.takeIf(String::isNotBlank)

private fun LibraryTrack.genreIds(): Set<String> {
    val root = metadataObject() ?: return emptySet()
    val ids = linkedSetOf<String>()

    fun addGenre(rawId: String?, rawName: String) {
        val name = rawName.trim()
        val id = rawId?.trim()?.takeIf(String::isNotBlank) ?: name.lowercase()
        if (name.isNotBlank()) ids += id
    }

    fun parseElement(element: kotlinx.serialization.json.JsonElement?) {
        when (element) {
            is JsonArray -> element.forEach(::parseElement)
            is JsonObject -> addGenre(
                rawId = (element["id"] as? JsonPrimitive)?.content,
                rawName = (element["name"] as? JsonPrimitive)?.content
                    ?: (element["title"] as? JsonPrimitive)?.content.orEmpty(),
            )
            is JsonPrimitive -> element.content.split(',', ';', '/').forEach { name -> addGenre(null, name) }
            else -> Unit
        }
    }

    parseElement(root["genres"])
    parseElement(root["genre"])
    parseElement(root["raw_genre_names"])
    parseElement(root["raw_genres"])
    parseElement(root["genre_names"])
    return ids
}
