package me.misa198.airmedy.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLyricsParserTest {
    @Test
    fun resumesAutoScrollWhenPlaybackSkipsPastATappedCloselyTimedLine() {
        assertTrue(
            shouldResumeLyricsAutoScroll(
                selectedLineIndex = 2,
                activeIndex = 3,
                activeIndexWhenLineSelected = 1,
                selectedLineAnimationComplete = true,
            ),
        )
    }

    @Test
    fun parsesTimedBilingualLines() {
        val lines = parsePlayerLyrics("[01:02.50]Primary ^ Translation\n[01:04.00]Next")

        assertEquals(2, lines.size)
        assertEquals(62.5f, lines[0].timestampSeconds)
        assertEquals("Primary", lines[0].primary)
        assertEquals("Translation", lines[0].secondary)
        assertTrue(hasSyncedPlayerLyrics("[01:02.50]Primary"))
    }

    @Test
    fun treatsUntimedLyricsAsPlainAndSupportsSlashBilingualText() {
        val lines = parsePlayerLyrics("Primary / Translation\nSecond line")

        assertFalse(hasSyncedPlayerLyrics("Primary / Translation"))
        assertEquals("Primary", lines[0].primary)
        assertEquals("Translation", lines[0].secondary)
        assertEquals(null, lines[0].timestampSeconds)
    }
}
