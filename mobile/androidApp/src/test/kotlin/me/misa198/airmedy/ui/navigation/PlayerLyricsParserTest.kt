package me.misa198.airmedy.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLyricsParserTest {
    @Test
    fun entersLyricsBrowseModeOnlyForUserDragging() {
        assertTrue(shouldEnterLyricsBrowseMode(isUserDragging = true))
        assertFalse(shouldEnterLyricsBrowseMode(isUserDragging = false))
        assertFalse(shouldEnterLyricsBrowseMode(isUserDragging = true, isFollowingSelectedLine = true))
    }

    @Test
    fun derivesLyricsAnimationDirectionFromTheSeekTarget() {
        assertEquals(LyricsSeekDirection.Backward, lyricsSeekDirection(targetIndex = 4, firstVisibleIndex = 12))
        assertEquals(LyricsSeekDirection.Forward, lyricsSeekDirection(targetIndex = 18, firstVisibleIndex = 12))
    }

    @Test
    fun treatsSmallFingerDriftOnALyricAsATap() {
        assertTrue(shouldSeekFromLyricTap(dragDistancePx = 12f, tapSlopPx = 20f))
        assertFalse(shouldSeekFromLyricTap(dragDistancePx = 24f, tapSlopPx = 20f))
    }

    @Test
    fun resetsLyricsForRepeatOrReplayAtTrackStart() {
        assertTrue(shouldResetLyricsForReplay(previousPositionMs = 84_000L, currentPositionMs = 0L))
        assertFalse(shouldResetLyricsForReplay(previousPositionMs = 84_000L, currentPositionMs = 4_000L))
    }

    @Test
    fun displaysThePendingSliderSeekUntilPlaybackConfirmsIt() {
        assertEquals(72_000L, displayedLyricsPositionMs(playbackPositionMs = 12_000L, pendingSeekPositionMs = 72_000L))
        assertEquals(12_000L, displayedLyricsPositionMs(playbackPositionMs = 12_000L, pendingSeekPositionMs = null))
    }

    @Test
    fun followsActiveLineAgainAfterReturningFromBackground() {
        assertTrue(
            shouldFollowLyricsActiveLine(
                previousActiveLineInViewport = false,
                returnedToForeground = true,
            ),
        )
        assertFalse(
            shouldFollowLyricsActiveLine(
                previousActiveLineInViewport = false,
                returnedToForeground = false,
            ),
        )
    }

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
