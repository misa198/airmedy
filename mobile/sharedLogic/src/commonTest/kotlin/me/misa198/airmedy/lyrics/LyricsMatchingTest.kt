package me.misa198.airmedy.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsMatchingTest {
    @Test fun kugouTieBreaksOnProviderScore() {
        val winner = bestLyricsCandidate(listOf(LyricsCandidate("Song", "Artist", 180.0, 1), LyricsCandidate("Song", "Artist", 180.0, 2)), "song", "artist", 180)
        assertEquals(2, winner?.providerScore)
    }
}
