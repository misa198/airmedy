package me.misa198.airmedy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
import me.misa198.airmedy.sync.LibrarySearchCandidate
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class LibrarySearchViewModel(syncStore: AndroidLibrarySyncStore) : ViewModel() {
    class Factory(private val syncStore: AndroidLibrarySyncStore) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibrarySearchViewModel(syncStore) as T
    }

    private val query = MutableStateFlow("")
    private val searchQuery = query.debouncedLibrarySearchQuery()
    private val searchResults = combine(
        searchQuery.flatMapLatest { text ->
            syncStore.searchCandidates(text).map { candidates -> SearchCandidates(text, candidates.groupBy { it.entityType }.mapValues { (_, values) -> values.mapTo(mutableSetOf(), LibrarySearchCandidate::entityId) }) }
        },
        syncStore.tracks, syncStore.albums, syncStore.artists, syncStore.playlists, syncStore.composers,
    ) { values ->
        val search = values[0] as SearchCandidates
        val text = search.query
        @Suppress("UNCHECKED_CAST")
        LibrarySearchUiState(
            isLoaded = true,
            query = text,
            tracks = searchTracks(values[1] as List<LibraryTrack>, text, search.ids("track")),
            allTracks = values[1] as List<LibraryTrack>,
            albums = searchLibrary(values[2] as List<LibraryAlbum>, text, { listOf(it.title, it.artist) }, { it.sortTitle }, { it.id }, search.ids("album")),
            artists = searchLibrary(values[3] as List<LibraryArtist>, text, { listOf(it.name) }, { it.sortName }, { it.id }, search.ids("artist")),
            playlists = searchLibrary(values[4] as List<LibraryPlaylist>, text, { listOf(it.name, playlistDescription(it)) }, { it.name }, { it.id }, search.ids("playlist")),
            composers = searchLibrary(values[5] as List<LibraryComposer>, text, { listOf(it.name) }, { it.sortName }, { it.id }, search.ids("composer")),
        )
    }.flowOn(Dispatchers.Default)
    val uiState: StateFlow<LibrarySearchUiState> = combine(query, searchResults) { query, results ->
        results.copy(query = query)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LibrarySearchUiState(isLoaded = false),
    )

    fun setQuery(value: String) { query.value = value }
    fun clear() { query.value = "" }
}

private data class SearchCandidates(val query: String, val idsByType: Map<String, Set<String>>) {
    fun ids(type: String): Set<String> = idsByType[type].orEmpty()
}

internal const val LibrarySearchDebounceMs = 200L

@OptIn(FlowPreview::class)
internal fun Flow<String>.debouncedLibrarySearchQuery(): Flow<String> = debounce { query ->
    if (query.isBlank()) 0L else LibrarySearchDebounceMs
}

internal fun searchTracks(tracks: List<LibraryTrack>, query: String, candidateIds: Set<String>? = null): List<LibraryTrack> = searchLibrary(
    tracks, query,
    { track -> listOf(track.title, track.artists, track.album) + trackGenres(track) },
    { it.sortTitle }, { it.id }, candidateIds,
)

internal fun <T> searchLibrary(
    items: List<T>, query: String, fields: (T) -> List<String>, sortName: (T) -> String, id: (T) -> String, candidateIds: Set<String>? = null,
): List<T> {
    val phrase = normalizedLibrarySearchText(query).trim()
    if (phrase.isEmpty()) return emptyList()
    val terms = searchTokens(phrase)
    return items.asSequence().filter { candidateIds == null || id(it) in candidateIds }.mapNotNull { item ->
        val normalizedFields = fields(item).map(::normalizedLibrarySearchText)
        if (!terms.all { term -> normalizedFields.any { field -> searchTokens(field).any { it.startsWith(term) } } }) null
        else item to when {
            normalizedFields.any { it == phrase } -> 0
            normalizedFields.any { it.startsWith(phrase) } -> 1
            else -> 2
        }
    }.sortedWith(compareBy<Pair<T, Int>> { it.second }.thenBy(libraryAlphabeticalComparator) { sortName(it.first) }.thenBy { id(it.first) })
        .map(Pair<T, Int>::first)
        .toList()
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
