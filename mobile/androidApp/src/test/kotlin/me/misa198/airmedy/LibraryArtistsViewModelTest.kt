package me.misa198.airmedy

import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.ui.screens.ArtistSortOption
import me.misa198.airmedy.ui.screens.SortOrder
import me.misa198.airmedy.ui.screens.sortArtists
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryArtistsViewModelTest {
    private val artists = listOf(
        LibraryArtist(id = "1", name = "Zebra", createdAt = "2026-01-01T00:00:00Z"),
        LibraryArtist(id = "2", name = "Alpha", createdAt = "2026-05-01T00:00:00Z"),
        LibraryArtist(id = "3", name = "Bravo", createdAt = "2025-12-01T00:00:00Z"),
    )

    @Test
    fun sortsByNameInBothDirections() {
        assertEquals(
            listOf("Alpha", "Bravo", "Zebra"),
            sortArtists(artists, ArtistSortOption.Name, SortOrder.Ascending).map { it.name },
        )
        assertEquals(
            listOf("Zebra", "Bravo", "Alpha"),
            sortArtists(artists, ArtistSortOption.Name, SortOrder.Descending).map { it.name },
        )
    }

    @Test
    fun sortsByDateAddedWithNameTieBreaker() {
        assertEquals(
            listOf("Bravo", "Zebra", "Alpha"),
            sortArtists(artists, ArtistSortOption.DateAdded, SortOrder.Ascending).map { it.name },
        )
    }
}
