package me.misa198.airmedy.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryComposerMapperTest {
    @Test
    fun groupsNormalizedManifestComposersAndResolvesArtworkAssets() {
        val composers = libraryComposersFrom(
            tracks = listOf(
                row("""{"composers":[{"id":"c1","name":"Beethoven","normalization_key":"beethoven, ludwig van","artwork_key":"beethoven-art","created_at":"2026-02-01T00:00:00Z"},{"id":"c2","name":"Mozart"}]}"""),
                row("""{"composers":[{"id":"c1","name":"Beethoven","created_at":"2026-01-01T00:00:00Z"}]}"""),
            ),
            artworkPaths = mapOf("beethoven-art" to "artwork/beethoven.jpg"),
        )

        assertEquals(listOf("c1", "c2"), composers.map { it.id })
        assertEquals("Beethoven", composers.first().name)
        assertEquals("beethoven, ludwig van", composers.first().sortName)
        assertEquals("artwork/beethoven.jpg", composers.first().artworkPath)
        assertEquals("2026-01-01T00:00:00Z", composers.first().createdAt)
        assertNull(composers.last().artworkPath)
    }

    @Test
    fun usesOnlyDesktopNormalizedComposerObjects() {
        val composers = libraryComposersFrom(
            tracks = listOf(
                row("""{"composers":[{"id":"w-n","name":"W/N"}],"raw_composer_names":"W/N"}"""),
                row("""{"composer":"Vivaldi / Handel","raw_composer_names":"Tchaikovsky, Stravinsky"}"""),
            ),
            artworkPaths = emptyMap(),
        )

        assertEquals(listOf("w-n"), composers.map { it.id })
        assertEquals(listOf("W/N"), composers.map { it.name })
    }

    @Test
    fun ignoresMalformedOrIncompleteComposerEntries() {
        val composers = libraryComposersFrom(
            tracks = listOf(row("""{"composers":[{"name":""}]}"""), row("not json")),
            artworkPaths = emptyMap(),
        )

        assertEquals(emptyList<LibraryComposer>(), composers)
    }

    @Test
    fun keepsOneComposerWhenStructuredAndRawMetadataNameAgree() {
        val composers = libraryComposersFrom(
            tracks = listOf(row("""{"composers":[{"id":"c1","name":"Bach"}],"raw_composer_names":"Bach"}""")),
            artworkPaths = emptyMap(),
        )

        assertEquals(listOf("c1"), composers.map { it.id })
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
