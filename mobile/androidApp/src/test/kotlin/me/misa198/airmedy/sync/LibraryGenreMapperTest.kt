package me.misa198.airmedy.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGenreMapperTest {
    @Test
    fun groupsNormalizedManifestGenres() {
        val genres = libraryGenresFrom(
            tracks = listOf(
                row("""{"genres":[{"id":"rock","name":"Rock","normalization_key":"rock","created_at":"2026-02-01T00:00:00Z"},{"id":"pop","name":"Pop"}]}"""),
                row("""{"genres":[{"id":"rock","name":"Rock","created_at":"2026-01-01T00:00:00Z"}]}"""),
            ),
        )

        assertEquals(listOf("rock", "pop"), genres.map { it.id })
        assertEquals("Rock", genres.first().name)
        assertEquals("rock", genres.first().sortName)
        assertEquals("2026-01-01T00:00:00Z", genres.first().createdAt)
    }

    @Test
    fun parsesGenresFromObjectsWithoutIdOrFromStringsOrRawGenreNames() {
        val genres = libraryGenresFrom(
            tracks = listOf(
                row("""{"genres":[{"name":"Electronic"}]}"""),
                row("""{"genres":["Ambient", "Chill"]}"""),
                row("""{"genre":"Jazz / Blues"}"""),
                row("""{"raw_genre_names":"Classical, Instrumental"}"""),
            ),
        )

        assertEquals(listOf("electronic", "ambient", "chill", "jazz", "blues", "classical", "instrumental"), genres.map { it.id })
        assertEquals(listOf("Electronic", "Ambient", "Chill", "Jazz", "Blues", "Classical", "Instrumental"), genres.map { it.name })
    }

    @Test
    fun ignoresMalformedOrIncompleteGenreEntries() {
        val genres = libraryGenresFrom(
            tracks = listOf(row("""{"genres":[{"name":""}]}"""), row("not json")),
        )

        assertEquals(emptyList<LibraryGenre>(), genres)
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
