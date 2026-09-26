package me.misa198.airmedy.ui.navigation

import androidx.compose.ui.unit.dp
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
    fun keepsBlurTargetsContinuousWhenTheActiveLineChanges() {
        assertEquals(0.dp, syncedLyricBlurRadius(0))
        assertEquals(0.35.dp, syncedLyricBlurRadius(1))
        assertEquals(1.25.dp, syncedLyricBlurRadius(2))
    }

    @Test
    fun doesNotScaleTheActiveEnhancedLyric() {
        assertEquals(1f, syncedLyricScale(distance = 0, focusMode = true, enhanced = true))
        assertEquals(1.04f, syncedLyricScale(distance = 0, focusMode = true, enhanced = false))
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
    fun parsesEnhancedLrcWordsAndCleansTimingTags() {
        val lines = parsePlayerLyrics("[00:01.62]Người <00:01.79>hỏi <00:02.05>anh <00:02.30>rằng\n[00:02.58]")

        assertEquals("Người hỏi anh rằng", lines[0].primary)
        assertEquals(
            listOf(
                PlayerLyricWord("Người ", 1.62f, 1.79f),
                PlayerLyricWord("hỏi ", 1.79f, 2.05f),
                PlayerLyricWord("anh ", 2.05f, 2.30f),
                PlayerLyricWord("rằng", 2.30f, 2.58f),
            ),
            lines[0].words,
        )
        assertEquals("", lines[1].primary)
    }

    @Test
    fun fallsBackToLineLyricsForInvalidEnhancedTiming() {
        val line = parsePlayerLyrics("[00:01]one <00:bad>two").single()

        assertEquals("one two", line.primary)
        assertEquals(null, line.words)
    }

    @Test
    fun calculatesKaraokeProgressIncludingInstantWords() {
        assertEquals(0.5f, karaokeWordProgress(PlayerLyricWord("one", 1f, 2f), 1.5f))
        assertEquals(1f, karaokeWordProgress(PlayerLyricWord("one", 1f, 1f), 1f))
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
