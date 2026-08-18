package me.misa198.airmedy

import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.screens.SortOrder
import me.misa198.airmedy.ui.screens.TrackSortOption
import me.misa198.airmedy.ui.screens.sortTracks
import me.misa198.airmedy.ui.screens.playbackRequestFor
import me.misa198.airmedy.ui.screens.collectionPlaybackRequestFor
import me.misa198.airmedy.ui.screens.matchesVisibleTrackTextFilter
import me.misa198.airmedy.ui.screens.keepListeningTracks
import me.misa198.airmedy.ui.screens.mostPlayedTracks
import me.misa198.airmedy.ui.screens.forgottenTracks
import me.misa198.airmedy.player.MaxPlaybackQueueSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LibraryTracksViewModelTest {

    private val sampleTracks = listOf(
        LibraryTrack(id = "1", title = "Charlie", artists = "Zebra", album = "A1", playCount = 10, createdAt = "2026-01-01T00:00:00Z", sortTitle = "Charlie", sortArtists = "Zebra"),
        LibraryTrack(id = "2", title = "Alpha", artists = "Beta", album = "A2", playCount = 5, createdAt = "2026-05-01T00:00:00Z", sortTitle = "Alpha", sortArtists = "Beta"),
        LibraryTrack(id = "3", title = "Bravo", artists = "Delta", album = "A3", playCount = 50, createdAt = "2025-12-01T00:00:00Z", sortTitle = "Bravo", sortArtists = "Delta"),
    )

    @Test
    fun filtersTracksOnlyByLabelsVisibleInTheRow() {
        val track = LibraryTrack(
            id = "1",
            title = "Title shown",
            artists = "Artist shown",
            album = "Hidden album metadata",
        )

        assertTrue(matchesVisibleTrackTextFilter("artist", track))
        assertFalse(matchesVisibleTrackTextFilter("hidden", track))
    }

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
    fun usesCanonicalTrackAndArtistSortKeysInsteadOfDisplayLabels() {
        val tracks = listOf(
            LibraryTrack(id = "1", title = "Zulu", artists = "Zulu Artist", sortTitle = "Alpha", sortArtists = "Beta"),
            LibraryTrack(id = "2", title = "Alpha", artists = "Alpha Artist", sortTitle = "Zulu", sortArtists = "Alpha"),
        )

        assertEquals(listOf("Zulu", "Alpha"), sortTracks(tracks, TrackSortOption.Name, SortOrder.Ascending).map { it.title })
        assertEquals(listOf("Alpha Artist", "Zulu Artist"), sortTracks(tracks, TrackSortOption.Artist, SortOrder.Ascending).map { it.artists })
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

    @Test
    fun clickedTrackBuildsQueueFromVisibleSortedOrder() {
        val request = playbackRequestFor(sampleTracks, "2")

        requireNotNull(request)
        assertEquals(listOf("1", "2", "3"), request.trackIds)
        assertEquals(1, request.startIndex)
    }

    @Test
    fun clickedRecentlyAddedTrackBuildsQueueFromRecentOrder() {
        val recentTracks = listOf(sampleTracks[1], sampleTracks[0], sampleTracks[2])

        val request = requireNotNull(playbackRequestFor(recentTracks, "1"))

        assertEquals(listOf("2", "1", "3"), request.trackIds)
        assertEquals(1, request.startIndex)
    }

    @Test
    fun recentTracksLimitsTo50AndSortsByCreatedAtDescending() {
        val manyTracks = (1..60).map { index ->
            LibraryTrack(
                id = "t$index",
                title = "Track $index",
                artists = "Artist $index",
                album = "Album",
                createdAt = String.format("2026-01-%02dT00:00:00Z", (index % 28) + 1),
                sortTitle = "Track $index",
            )
        }
        val recent = manyTracks
            .sortedWith(compareByDescending<LibraryTrack> { it.createdAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }.thenBy { it.id })
            .take(50)

        assertEquals(50, recent.size)
        assertEquals(
            recent.sortedWith(compareByDescending<LibraryTrack> { it.createdAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortTitle }.thenBy { it.id }),
            recent,
        )
    }

    @Test
    fun homeSectionsMatchDesktopListeningOrderingAndLimit() {
        val tracks = (1..30).map { index ->
            LibraryTrack(
                id = "t$index",
                title = "Track $index",
                artists = "Artist",
                playCount = if (index == 1) 0 else index,
                updatedAt = String.format("2026-01-%02dT00:00:00Z", index),
                sortTitle = "Track $index",
            )
        }

        assertEquals(28, keepListeningTracks(tracks).size)
        assertEquals("t30", keepListeningTracks(tracks).first().id)
        assertEquals("t30", mostPlayedTracks(tracks).first().id)
        assertEquals("t1", forgottenTracks(tracks).first().id)
        assertEquals(28, forgottenTracks(tracks).size)
    }

    @Test
    fun collectionPlayUsesTheFirstThousandDistinctVisibleTracks() {
        val request = requireNotNull(collectionPlaybackRequestFor((1..1_200).map { "track-$it" }, shuffle = false))

        assertEquals(MaxPlaybackQueueSize, request.trackIds.size)
        assertEquals((1..1_000).map { "track-$it" }, request.trackIds)
        assertEquals(0, request.startIndex)
    }

    @Test
    fun collectionShuffleSamplesThousandDistinctTracksFromTheFullCollection() {
        val request = requireNotNull(
            collectionPlaybackRequestFor((1..1_200).map { "track-$it" }, shuffle = true, random = Random(1)),
        )

        assertEquals(MaxPlaybackQueueSize, request.trackIds.size)
        assertEquals(request.trackIds.size, request.trackIds.distinct().size)
        assertTrue(request.trackIds.any { it.removePrefix("track-").toInt() > MaxPlaybackQueueSize })
    }

    @Test
    fun collectionPlaybackDoesNotCreateAnEmptyRequest() {
        assertNull(collectionPlaybackRequestFor(emptyList(), shuffle = false))
    }
}
