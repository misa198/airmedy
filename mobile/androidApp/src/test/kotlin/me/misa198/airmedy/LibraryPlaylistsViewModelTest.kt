package me.misa198.airmedy

import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.screens.playlistArtworkPaths
import me.misa198.airmedy.ui.screens.playlistManualArtworkPath
import me.misa198.airmedy.ui.screens.playlistsWithFavorites
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPlaylistsViewModelTest {
    private val tracks = listOf(
        LibraryTrack(id = "a", title = "A", artists = "Artist", artworkKey = "cover-a", artworkPath = "a.jpg"),
        LibraryTrack(id = "b", title = "B", artists = "Artist", artworkKey = "cover-a", artworkPath = "a.jpg"),
        LibraryTrack(id = "c", title = "C", artists = "Artist", artworkKey = "cover-c", artworkPath = "c.jpg"),
        LibraryTrack(id = "d", title = "D", artists = "Artist", artworkKey = "cover-d", artworkPath = "d.jpg"),
        LibraryTrack(id = "e", title = "E", artists = "Artist", artworkKey = "cover-e", artworkPath = "e.jpg"),
        LibraryTrack(id = "f", title = "F", artists = "Artist", artworkKey = "cover-f", artworkPath = "f.jpg"),
    )

    @Test
    fun placesFavoritesFirstAndCreatesItWhenTheManifestOmitsIt() {
        val playlist = LibraryPlaylist("p", "Playlist", emptyList(), "{}")

        assertEquals(listOf("favorites", "p"), playlistsWithFavorites(listOf(playlist)).map { it.id })
        assertEquals(listOf("favorites"), playlistsWithFavorites(emptyList()).map { it.id })
    }

    @Test
    fun derivesFavoritesArtworkFromFavoriteTracks() {
        val favorite = tracks.first().copy(metadataJson = """{"is_favorite":true}""")
        val playlist = playlistsWithFavorites(emptyList(), listOf(favorite)).single()

        assertEquals(listOf("a.jpg"), playlistArtworkPaths(playlist, listOf(favorite), emptyMap()))
    }

    @Test
    fun usesCustomArtworkBeforeTrackMosaic() {
        val playlist = LibraryPlaylist("p", "Playlist", listOf("a", "c"), """{"playlist":{"artwork_key":"custom"}}""")

        assertEquals(listOf("custom.jpg"), playlistArtworkPaths(playlist, tracks, mapOf("custom" to "custom.jpg")))
    }

    @Test
    fun retainsOrderAndUsesFirstFourUniqueTrackArtworks() {
        val playlist = LibraryPlaylist("p", "Playlist", listOf("a", "b", "c", "d", "e", "f"), "{}")

        assertEquals(listOf("a.jpg", "c.jpg", "d.jpg", "e.jpg"), playlistArtworkPaths(playlist, tracks, emptyMap()))
    }

    @Test
    fun fallsBackToTrackArtworkWhenCustomArtworkIsUnavailable() {
        val playlist = LibraryPlaylist("p", "Playlist", listOf("a"), """{"playlist":{"artwork_key":"missing"}}""")

        assertEquals(listOf("a.jpg"), playlistArtworkPaths(playlist, tracks, emptyMap()))
    }

    @Test
    fun editorArtworkUsesOnlyThePlaylistManualCover() {
        val fallback = LibraryPlaylist("p", "Playlist", listOf("a"), "{}")
        val custom = LibraryPlaylist("p", "Playlist", listOf("a"), """{"playlist":{"artwork_key":"custom"}}""")

        assertEquals(null, playlistManualArtworkPath(fallback, emptyMap()))
        assertEquals("custom.jpg", playlistManualArtworkPath(custom, mapOf("custom" to "custom.jpg")))
    }
}
