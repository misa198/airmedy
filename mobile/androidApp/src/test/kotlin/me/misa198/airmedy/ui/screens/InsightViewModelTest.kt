package me.misa198.airmedy.ui.screens

import java.time.LocalDate
import me.misa198.airmedy.pairing.MobileIdentity
import me.misa198.airmedy.pairing.MobilePlatform
import me.misa198.airmedy.pairing.PairedDesktop
import me.misa198.airmedy.player.DailyPlaybackAttemptStat
import me.misa198.airmedy.player.DailyTrackListeningStat
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.sync.LibraryArtist
import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.TrackAudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightViewModelTest {
    private val tracks = listOf(
        track("one", "Alpha", "2026-01-02T00:00:00Z", "flac", 24, "artist-a", "Artist A", listOf("Rock", "Pop", "Soul", "Jazz", "Folk", "Metal"), 1_000),
        track("two", "Beta", "2026-01-09T00:00:00Z", "mp3", 0, "artist-b", "Artist B", listOf("Jazz"), 2_000),
        track("three", "Gamma", "2025-06-01T00:00:00Z", "unknown", 0, "artist-c", "Artist C", listOf("Ambient"), 3_000),
    )
    private val bundle = LibraryBundle(
        tracks,
        listOf(LibraryArtist("artist-a", "Artist A"), LibraryArtist("artist-b", "Artist B"), LibraryArtist("artist-c", "Artist C")),
        listOf(LibraryAlbum("album-a", "Album A"), LibraryAlbum("album-b", "Album B")),
        listOf(LibraryPlaylist("favorites", "Favorites", emptyList(), "{}"), LibraryPlaylist("mix", "Mix", emptyList(), "{}")),
    )
    private val raw = InsightRawData(
        library = bundle,
        dailyTracks = listOf(
            DailyTrackListeningStat("phone", "2026-01-10", "one", 600, 3),
            DailyTrackListeningStat("desktop", "2026-01-09", "two", 300, 2),
            DailyTrackListeningStat("other", "2026-01-08", "three", 60, 1),
            DailyTrackListeningStat("phone", "2026-01-03", "one", 300, 1),
        ),
        dailyAttempts = listOf(
            DailyPlaybackAttemptStat("phone", "2026-01-10", 2, 1, 1, 0, 500),
            DailyPlaybackAttemptStat("desktop", "2026-01-09", 1, 0, 0, 1, 200),
        ),
        identity = MobileIdentity("phone", "Phone", MobilePlatform.Android, byteArrayOf()),
        desktop = PairedDesktop("desktop", "Studio Mac", byteArrayOf()),
    )

    @Test
    fun mirrorsDesktopListeningCalculationsAndRankings() {
        val state = buildInsightUiState(raw, InsightPeriod.SevenDays, InsightPeriod.SevenDays, InsightSourceFilter.All, LocalDate.parse("2026-01-10"))

        assertEquals(960, state.listening.listenedSeconds)
        assertEquals(6, state.listening.plays)
        assertEquals(3, state.listening.streakDays)
        assertEquals(233, state.listening.averageSessionSeconds)
        assertEquals(220.0, state.listening.changePercent!!, 0.01)
        assertEquals(listOf("one", "two", "three"), state.listening.topTracks.map { it.track.id })
        assertEquals("Artist A", state.listening.topArtists.first().name)
        assertTrue(state.listening.genres.last().isOther)
        assertEquals(7, state.listening.activity.size)
    }

    @Test
    fun sourceFilterAndLibraryProjectionUseTheMirroredSnapshot() {
        val state = buildInsightUiState(raw, InsightPeriod.SevenDays, InsightPeriod.SevenDays, InsightSourceFilter.ThisPhone, LocalDate.parse("2026-01-10"))

        assertEquals(600, state.listening.listenedSeconds)
        assertEquals(1, state.listening.streakDays)
        assertEquals("Studio Mac", state.desktopName)
        assertTrue(state.hasDesktopSource)
        assertTrue(state.hasOtherSources)
        assertEquals(3, state.library.tracks)
        assertEquals(2, state.library.albums)
        assertEquals(1, state.library.playlists)
        assertEquals(6_000, state.library.bytes)
        assertEquals(3, state.library.growth.last().value)
        assertEquals(1, state.library.quality.first { it.quality == TrackAudioQuality.HiRes }.count)
        assertEquals(1, state.library.quality.first { it.quality == TrackAudioQuality.Lossy }.count)
    }

    @Test
    fun longerListeningRangesKeepTheActivityData() {
        listOf(InsightPeriod.ThirtyDays, InsightPeriod.All).forEach { period ->
            val state = buildInsightUiState(raw, InsightPeriod.SevenDays, period, InsightSourceFilter.All, LocalDate.parse("2026-01-10"))

            assertTrue(state.listening.listenedSeconds > 0)
            assertTrue(state.listening.activity.any { it.value > 0 })
        }
    }

    private fun track(
        id: String,
        title: String,
        createdAt: String,
        format: String,
        bitDepth: Int,
        artistId: String,
        artist: String,
        genres: List<String>,
        fileSize: Long,
    ) = LibraryTrack(
        id = id,
        title = title,
        artists = artist,
        createdAt = createdAt,
        metadataJson = """{"format":"$format","bit_depth":$bitDepth,"file_size":$fileSize,"artists":[{"id":"$artistId","name":"$artist"}],"genres":[${genres.joinToString { "{\"id\":\"$it\",\"name\":\"$it\"}" }}]}""",
    )
}
