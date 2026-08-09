package me.misa198.airmedy.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryArtistMapperTest {
    @Test
    fun groupsNormalizedManifestArtistsAndResolvesArtworkAssets() {
        val artists = libraryArtistsFrom(
            tracks = listOf(
                row("""{"artists":[{"id":"a","name":"Artist A","artwork_key":"artist-a","created_at":"2026-02-01T00:00:00Z"},{"id":"b","name":"Artist B","artwork_key_local":"artist-b"}]}"""),
                row("""{"artists":[{"id":"a","name":"Artist A","created_at":"2026-01-01T00:00:00Z"}]}"""),
            ),
            artworkPaths = mapOf("artist-a" to "artwork/a.jpg"),
        )

        assertEquals(listOf("a", "b"), artists.map { it.id })
        assertEquals("artwork/a.jpg", artists.first().artworkPath)
        assertEquals("2026-01-01T00:00:00Z", artists.first().createdAt)
        assertNull(artists.last().artworkPath)
    }

    @Test
    fun ignoresMalformedOrIncompleteArtistEntries() {
        val artists = libraryArtistsFrom(
            tracks = listOf(row("""{"artists":[{"name":"Missing id"},"bad"]}"""), row("not json")),
            artworkPaths = emptyMap(),
        )

        assertEquals(emptyList<LibraryArtist>(), artists)
    }

    private fun row(rawJson: String) = LibraryTrackRow(
        id = "track",
        title = "Track",
        artists = "",
        album = "",
        artworkKey = null,
        playCount = 0,
        createdAt = "",
        artworkPath = null,
        audioPath = null,
        rawJson = rawJson,
    )
}
