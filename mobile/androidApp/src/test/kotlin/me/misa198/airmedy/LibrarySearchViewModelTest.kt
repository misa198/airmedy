package me.misa198.airmedy

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.sync.LibraryComposer
import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.searchFtsMatch
import me.misa198.airmedy.ui.screens.searchLibrary
import me.misa198.airmedy.ui.screens.searchTracks
import me.misa198.airmedy.ui.screens.searchTokens
import me.misa198.airmedy.ui.screens.searchItemKey
import me.misa198.airmedy.ui.screens.trackHasDivider
import me.misa198.airmedy.ui.screens.debouncedLibrarySearchQuery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySearchViewModelTest {
    @Test fun debouncesNonBlankQueriesButClearsImmediately() = runTest {
        val input = MutableSharedFlow<String>()
        val values = mutableListOf<String>()
        backgroundScope.launch { input.debouncedLibrarySearchQuery().collect { values += it } }
        runCurrent()

        input.emit("m")
        advanceTimeBy(199)
        assertTrue(values.isEmpty())
        input.emit("mu")
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("mu"), values)

        input.emit("")
        runCurrent()
        assertEquals(listOf("mu", ""), values)
    }

    @Test fun foldsDiacriticsAndRequiresEveryPrefixTerm() {
        val tracks = listOf(LibraryTrack(id = "1", title = "Déjà Vu", artists = "Beyoncé", album = "Renaissance"), LibraryTrack(id = "2", title = "Deja", artists = "Other", album = "Elsewhere"))
        assertEquals(listOf("1"), searchTracks(tracks, "deja bey").map { it.id })
    }

    @Test fun ranksExactPhraseBeforePrefixAndSearchesEachEntityField() {
        val albums = listOf(LibraryAlbum("1", "Muse", "Artist"), LibraryAlbum("2", "Museum", "Artist"))
        assertEquals(listOf("1", "2"), searchLibrary(albums, "muse", { listOf(it.title, it.artist) }, { it.title }, { it.id }).map { it.id })
        assertTrue(searchLibrary(listOf(LibraryArtist("a", "Björk")), "bjo", { listOf(it.name) }, { it.name }, { it.id }).isNotEmpty())
        assertTrue(searchLibrary(listOf(LibraryPlaylist("p", "Focus", emptyList(), "{}")), "foc", { listOf(it.name) }, { it.name }, { it.id }).isNotEmpty())
        assertTrue(searchLibrary(listOf(LibraryComposer("c", "Claude Debussy")), "deb", { listOf(it.name) }, { it.name }, { it.id }).isNotEmpty())
    }

    @Test fun emptyQueryReturnsNoResults() {
        assertTrue(searchTracks(listOf(LibraryTrack(id = "1", title = "Anything", artists = "Artist")), "").isEmpty())
    }

    @Test fun matchesDesktopMetadataAndPunctuationTokens() {
        val track = LibraryTrack(id = "1", title = "Song", artists = "AC/DC", metadataJson = "{\"genres\":[{\"name\":\"Electronic Pop\"}]}")
        assertEquals(listOf("1"), searchTracks(listOf(track), "dc elec").map { it.id })
        assertEquals(listOf("ac", "dc"), searchTokens("AC/DC"))
    }

    @Test fun ranksOnlyFtsCandidatesWithTheExistingSearchRules() {
        val tracks = listOf(
            LibraryTrack(id = "exact", title = "Muse", artists = "Artist"),
            LibraryTrack(id = "prefix", title = "Museum", artists = "Artist"),
        )
        assertEquals(listOf("prefix"), searchTracks(tracks, "muse", setOf("prefix")).map { it.id })
    }

    @Test fun createsSafePrefixFtsTerms() {
        assertEquals("ac* AND dc* AND deja*", searchFtsMatch("AC/DC Déjà"))
        assertEquals(null, searchFtsMatch("---"))
    }

    @Test fun namespacesIdenticalEntityIdsBySearchSection() {
        assertTrue(searchItemKey(1, "same-id") != searchItemKey(2, "same-id"))
    }

    @Test fun omitsDividersAtTheEndOfEachThreeRowTrackColumn() {
        assertTrue(trackHasDivider(0, 4))
        assertTrue(trackHasDivider(1, 4))
        assertFalse(trackHasDivider(2, 4))
        assertTrue(trackHasDivider(3, 4))
        assertFalse(trackHasDivider(4, 4))
    }
}
