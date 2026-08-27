package me.misa198.airmedy.mood

import kotlin.test.Test
import kotlin.test.assertEquals

class MoodRadioTest {
    @Test fun selectsNearestUsableTracksAndExcludesQueue() {
        fun track(id: String, energy: Double?, album: String = "", artist: String = "") = MoodRadioTrack(id, album, artist, energy, energy, energy, 100.0)
        val result = selectMoodRadio("seed", listOf(track("seed", .5), track("near", .51), track("queued", .5001), track("missing", null)), setOf("queued"), random = { 0.0 })
        assertEquals(listOf("near"), result.map { it.id })
    }

    @Test fun `caps nearest-neighbour pool before diversity selection`() {
        val seed = MoodRadioTrack("seed", energy = 0.0, danceability = 0.0, brightness = 0.0, tempo = 100.0)
        val tracks = (1..100).map { index ->
            val value = index / 100.0
            MoodRadioTrack("track-$index", energy = value, danceability = value, brightness = value, tempo = 100.0)
        }

        val result = selectMoodRadio("seed", listOf(seed) + tracks, emptySet(), limit = 80, random = { 0.0 })

        assertEquals((1..80).map { "track-$it" }, result.map { it.id })
    }
}
