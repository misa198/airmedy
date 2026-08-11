package me.misa198.airmedy

import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.ui.screens.AlbumSortOption
import me.misa198.airmedy.ui.screens.SortOrder
import me.misa198.airmedy.ui.screens.sortAlbums
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAlbumsViewModelTest {
    private val albums = listOf(
        LibraryAlbum(id = "1", title = "Zebra", artist = "Delta", createdAt = "2026-01-01T00:00:00Z", sortTitle = "Zebra", sortArtist = "Delta"),
        LibraryAlbum(id = "2", title = "Alpha", artist = "Zebra", createdAt = "2026-05-01T00:00:00Z", sortTitle = "Alpha", sortArtist = "Zebra"),
        LibraryAlbum(id = "3", title = "Bravo", artist = "Alpha", createdAt = "2025-12-01T00:00:00Z", sortTitle = "Bravo", sortArtist = "Alpha"),
    )

    @Test
    fun sortsAlbumsByNameArtistAndDateAdded() {
        assertEquals(listOf("Alpha", "Bravo", "Zebra"), sortAlbums(albums, AlbumSortOption.Name, SortOrder.Ascending).map { it.title })
        assertEquals(listOf("Alpha", "Delta", "Zebra"), sortAlbums(albums, AlbumSortOption.Artist, SortOrder.Ascending).map { it.artist })
        assertEquals(listOf("2025-12-01T00:00:00Z", "2026-01-01T00:00:00Z", "2026-05-01T00:00:00Z"), sortAlbums(albums, AlbumSortOption.DateAdded, SortOrder.Ascending).map { it.createdAt })
    }

    @Test
    fun reversesTheSortedAlbumOrder() {
        assertEquals(listOf("Zebra", "Bravo", "Alpha"), sortAlbums(albums, AlbumSortOption.Name, SortOrder.Descending).map { it.title })
    }

    @Test
    fun usesCanonicalAlbumAndArtistSortKeysInsteadOfDisplayLabels() {
        val albums = listOf(
            LibraryAlbum(id = "1", title = "Zulu", artist = "Zulu Artist", sortTitle = "Alpha", sortArtist = "Beta"),
            LibraryAlbum(id = "2", title = "Alpha", artist = "Alpha Artist", sortTitle = "Zulu", sortArtist = "Alpha"),
        )

        assertEquals(listOf("Zulu", "Alpha"), sortAlbums(albums, AlbumSortOption.Name, SortOrder.Ascending).map { it.title })
        assertEquals(listOf("Alpha Artist", "Zulu Artist"), sortAlbums(albums, AlbumSortOption.Artist, SortOrder.Ascending).map { it.artist })
    }
}
