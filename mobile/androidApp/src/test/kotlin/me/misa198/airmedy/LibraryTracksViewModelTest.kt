package me.misa198.airmedy

import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.screens.SortOrder
import me.misa198.airmedy.ui.screens.TrackSortOption
import me.misa198.airmedy.ui.screens.sortTracks
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTracksViewModelTest {

    private val sampleTracks = listOf(
        LibraryTrack(id = "1", title = "Charlie", artists = "Zebra", album = "A1", playCount = 10, createdAt = "2026-01-01T00:00:00Z"),
        LibraryTrack(id = "2", title = "Alpha", artists = "Beta", album = "A2", playCount = 5, createdAt = "2026-05-01T00:00:00Z"),
        LibraryTrack(id = "3", title = "Bravo", artists = "Delta", album = "A3", playCount = 50, createdAt = "2025-12-01T00:00:00Z"),
    )

    @Test
    fun sortsByNameAscending() {
        val sorted = sortTracks(sampleTracks, TrackSortOption.Name, SortOrder.Ascending)
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), sorted.map { it.title })
    }

    @Test
    fun sortsByNameDescending() {
        val sorted = sortTracks(sampleTracks, TrackSortOption.Name, SortOrder.Descending)
        assertEquals(listOf("Charlie", "Bravo", "Alpha"), sorted.map { it.title })
    }

    @Test
    fun sortsByArtistAscending() {
        val sorted = sortTracks(sampleTracks, TrackSortOption.Artist, SortOrder.Ascending)
        assertEquals(listOf("Beta", "Delta", "Zebra"), sorted.map { it.artists })
    }

    @Test
    fun sortsByArtistDescending() {
        val sorted = sortTracks(sampleTracks, TrackSortOption.Artist, SortOrder.Descending)
        assertEquals(listOf("Zebra", "Delta", "Beta"), sorted.map { it.artists })
    }

    @Test
    fun sortsByPlayCountAscending() {
        val sorted = sortTracks(sampleTracks, TrackSortOption.PlayCount, SortOrder.Ascending)
        assertEquals(listOf(5, 10, 50), sorted.map { it.playCount })
    }

    @Test
    fun sortsByPlayCountDescending() {
        val sorted = sortTracks(sampleTracks, TrackSortOption.PlayCount, SortOrder.Descending)
        assertEquals(listOf(50, 10, 5), sorted.map { it.playCount })
    }

    @Test
    fun sortsByDateAddedAscending() {
        val sorted = sortTracks(sampleTracks, TrackSortOption.DateAdded, SortOrder.Ascending)
        assertEquals(listOf("2025-12-01T00:00:00Z", "2026-01-01T00:00:00Z", "2026-05-01T00:00:00Z"), sorted.map { it.createdAt })
    }

    @Test
    fun sortsByDateAddedDescending() {
        val sorted = sortTracks(sampleTracks, TrackSortOption.DateAdded, SortOrder.Descending)
        assertEquals(listOf("2026-05-01T00:00:00Z", "2026-01-01T00:00:00Z", "2025-12-01T00:00:00Z"), sorted.map { it.createdAt })
    }
}
