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

    @Test
    fun groupsAlbumsByManifestIdAndResolvesAlbumArtwork() {
        val albums = libraryAlbumsFrom(
            tracks = listOf(
                row("""{"album":{"id":"album-a","title":"Album A","artwork_key":"album-art","created_at":"2026-02-01T00:00:00Z"},"album_artists":[{"name":"Artist A"}]}"""),
                row("""{"album":{"id":"album-a","title":"Album A","artwork_key":"album-art"},"album_artists":[{"name":"Artist A"}]}"""),
                row("""{"album":{"id":"album-b","title":"Album B"},"album_artists":[]}"""),
            ),
            artworkPaths = mapOf("album-art" to "artwork/album-a.jpg"),
        )

        assertEquals(listOf("album-a", "album-b"), albums.map { it.id })
        assertEquals("Artist A", albums.first().artist)
        assertEquals("artwork/album-a.jpg", albums.first().artworkPath)
        assertEquals("2026-02-01T00:00:00Z", albums.first().createdAt)
        assertEquals("", albums.last().artist)
    }

    @Test
    fun ignoresTracksWithoutACompleteAlbum() {
        val albums = libraryAlbumsFrom(
            tracks = listOf(
                row("""{"album":{"title":"Missing id"}}"""),
                row("""{"album":{"id":"missing-title","title":" "}}"""),
                row("not json"),
            ),
            artworkPaths = emptyMap(),
        )

        assertEquals(emptyList<LibraryAlbum>(), albums)
    }

    @Test
    fun fallsBackToTrackArtistsWhenTheManifestOmitsAlbumArtists() {
        val albums = libraryAlbumsFrom(
            tracks = listOf(
                row("""{"album":{"id":"album-a","title":"Album A"},"artists":[{"name":"Artist A"}]}"""),
            ),
            artworkPaths = emptyMap(),
        )

        assertEquals("Artist A", albums.single().artist)
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
