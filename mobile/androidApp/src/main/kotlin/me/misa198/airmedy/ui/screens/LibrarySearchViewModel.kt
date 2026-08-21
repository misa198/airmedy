package me.misa198.airmedy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import me.misa198.airmedy.sync.AndroidLibrarySyncStore
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.sync.LibraryComposer
import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.LibrarySyncProtocol
import me.misa198.airmedy.sync.metadataObject
import me.misa198.airmedy.ui.libraryAlphabeticalComparator

data class LibrarySearchUiState(
    val isLoaded: Boolean = true,
    val query: String = "",
    val tracks: List<LibraryTrack> = emptyList(),
    internal val allTracks: List<LibraryTrack> = emptyList(),
    val albums: List<LibraryAlbum> = emptyList(),
    val artists: List<LibraryArtist> = emptyList(),
    val playlists: List<LibraryPlaylist> = emptyList(),
    val composers: List<LibraryComposer> = emptyList(),
)

internal class LibrarySearchViewModel(syncStore: AndroidLibrarySyncStore) : ViewModel() {
    class Factory(private val syncStore: AndroidLibrarySyncStore) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibrarySearchViewModel(syncStore) as T
    }

    private val query = MutableStateFlow("")
    private val searchQuery = query.debouncedLibrarySearchQuery()
    val uiState: StateFlow<LibrarySearchUiState> = combine(
        query, searchQuery, syncStore.tracks, syncStore.albums, syncStore.artists, syncStore.playlists, syncStore.composers,
    ) { values ->
        val queryText = values[0] as String
        val text = if (queryText.isBlank()) "" else values[1] as String
        @Suppress("UNCHECKED_CAST")
        LibrarySearchUiState(
            isLoaded = true,
            query = queryText,
            tracks = searchTracks(values[2] as List<LibraryTrack>, text),
            allTracks = values[2] as List<LibraryTrack>,
            albums = searchLibrary(values[3] as List<LibraryAlbum>, text, { listOf(it.title, it.artist) }, { it.sortTitle }, { it.id }),
            artists = searchLibrary(values[4] as List<LibraryArtist>, text, { listOf(it.name) }, { it.sortName }, { it.id }),
            playlists = searchLibrary(values[5] as List<LibraryPlaylist>, text, { listOf(it.name, playlistDescription(it)) }, { it.name }, { it.id }),
            composers = searchLibrary(values[6] as List<LibraryComposer>, text, { listOf(it.name) }, { it.sortName }, { it.id }),
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LibrarySearchUiState(isLoaded = false),
    )

    fun setQuery(value: String) { query.value = value }
    fun clear() { query.value = "" }
}

internal const val LibrarySearchDebounceMs = 300L

@OptIn(FlowPreview::class)
internal fun Flow<String>.debouncedLibrarySearchQuery(): Flow<String> = debounce { query ->
    if (query.isBlank()) 0L else LibrarySearchDebounceMs
}

internal fun searchTracks(tracks: List<LibraryTrack>, query: String): List<LibraryTrack> = searchLibrary(
    tracks, query,
    { track -> listOf(track.title, track.artists, track.album) + trackGenres(track) },
    { it.sortTitle }, { it.id },
)

internal fun <T> searchLibrary(
    items: List<T>, query: String, fields: (T) -> List<String>, sortName: (T) -> String, id: (T) -> String,
): List<T> {
    val phrase = normalizedLibrarySearchText(query).trim()
    if (phrase.isEmpty()) return emptyList()
    val terms = searchTokens(phrase)
    return items.mapNotNull { item ->
        val normalizedFields = fields(item).map(::normalizedLibrarySearchText)
        if (!terms.all { term -> normalizedFields.any { field -> searchTokens(field).any { it.startsWith(term) } } }) null
        else item to when {
            normalizedFields.any { it == phrase } -> 0
            normalizedFields.any { it.startsWith(phrase) } -> 1
            else -> 2
        }
    }.sortedWith(compareBy<Pair<T, Int>> { it.second }.thenBy(libraryAlphabeticalComparator) { sortName(it.first) }.thenBy { id(it.first) })
        .map(Pair<T, Int>::first)
}

private val SearchTokenPattern = Regex("[\\p{L}\\p{N}]+")

/** Mirrors Bleve's word-token matching for punctuation-delimited metadata. */
internal fun searchTokens(value: String): List<String> = SearchTokenPattern.findAll(normalizedLibrarySearchText(value)).map { it.value }.toList()

private fun trackGenres(track: LibraryTrack): List<String> = track.metadataObject().metadataValues(
    "genres", "genre", "raw_genre_names", "raw_genres", "genre_names",
)

private fun playlistDescription(playlist: LibraryPlaylist): String = runCatching {
    LibrarySyncProtocol.json.parseToJsonElement(playlist.metadataJson) as? JsonObject
}.getOrNull().metadataValues("description", "playlist").joinToString(" ")

private fun JsonObject?.metadataValues(vararg keys: String): List<String> = keys.flatMap { key ->
    when (val value = this?.get(key)) {
        is JsonArray -> value.flatMap { it.metadataValues() }
        else -> value.metadataValues()
    }
}

private fun JsonElement?.metadataValues(): List<String> = when (this) {
    is JsonPrimitive -> listOfNotNull(contentOrNull)
    is JsonObject -> listOfNotNull(
        (this["name"] as? JsonPrimitive)?.contentOrNull,
        (this["title"] as? JsonPrimitive)?.contentOrNull,
        (this["description"] as? JsonPrimitive)?.contentOrNull,
    )
    is JsonArray -> flatMap { it.metadataValues() }
    else -> emptyList()
}
