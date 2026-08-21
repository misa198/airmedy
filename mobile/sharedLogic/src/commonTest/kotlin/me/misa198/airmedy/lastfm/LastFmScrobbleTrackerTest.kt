package me.misa198.airmedy.lastfm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LastFmScrobbleTrackerTest {
    @Test
    fun emitsDesktopThresholdsOncePerPlayback() {
        val tracker = LastFmScrobbleTracker().apply { start("track") }

        assertTrue(tracker.update("track", 2_999, 300_000, true).isEmpty())
        assertEquals(listOf(LastFmPlaybackEvent.NowPlaying), tracker.update("track", 3_000, 300_000, true))
        assertEquals(listOf(LastFmPlaybackEvent.Scrobble), tracker.update("track", 150_000, 300_000, true))
        assertTrue(tracker.update("track", 300_000, 300_000, true).isEmpty())
    }

    @Test
    fun requiresPlayingAndThirtySecondDurationAndUsesFourMinuteCap() {
        val tracker = LastFmScrobbleTracker().apply { start("long") }

        assertTrue(tracker.update("long", 240_000, 600_000, false).isEmpty())
        assertEquals(
            listOf(LastFmPlaybackEvent.NowPlaying, LastFmPlaybackEvent.Scrobble),
            tracker.update("long", 240_000, 600_000, true),
        )

        tracker.start("short")
        assertEquals(
            listOf(LastFmPlaybackEvent.NowPlaying),
            tracker.update("short", 20_000, 29_999, true),
        )
    }
}
