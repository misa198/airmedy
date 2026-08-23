package me.misa198.airmedy.lyrics

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

class LrclibCandidateTest {
    @Test
    fun coercesNullLyricsFieldsToEmptyStrings() {
        val candidate = ProviderJson.decodeFromString(
            ListSerializer(LrclibCandidate.serializer()),
            """[{"trackName":"Song","artistName":"Artist","syncedLyrics":null,"plainLyrics":"plain"}]""",
        ).single()

        assertEquals("", candidate.syncedLyrics)
        assertEquals("plain", candidate.plainLyrics)
    }
}
