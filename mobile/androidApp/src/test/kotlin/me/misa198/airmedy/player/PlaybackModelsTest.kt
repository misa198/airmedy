package me.misa198.airmedy.player

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModelsTest {
    @Test
    fun `crossfade policy only starts inside the valid automatic transition window`() {
        assertTrue(shouldStartCrossfade(4, positionMs = 116_000, durationMs = 120_000, hasPreloadedNext = true))
        assertFalse(shouldStartCrossfade(4, positionMs = 119_700, durationMs = 120_000, hasPreloadedNext = true))
        assertFalse(shouldStartCrossfade(4, positionMs = 10_000, durationMs = 1_999, hasPreloadedNext = true))
        assertFalse(shouldStartCrossfade(0, positionMs = 116_000, durationMs = 120_000, hasPreloadedNext = true))
        assertFalse(shouldStartCrossfade(4, positionMs = 116_000, durationMs = 120_000, hasPreloadedNext = false))
    }

    @Test
    fun `crossfade duration is clamped to the persisted range`() {
        assertEquals(0, clampCrossfadeSeconds(-1))
        assertEquals(4, clampCrossfadeSeconds(4))
        assertEquals(12, clampCrossfadeSeconds(99))
        assertEquals(1, clampEnabledCrossfadeSeconds(-1))
        assertEquals(12, clampEnabledCrossfadeSeconds(99))
    }

    @Test
    fun `artwork crossfade transition preserves effective visual duration`() {
        val transition = ArtworkCrossfadeTransition(
            id = 7,
            fromArtworkPath = "artwork/from.jpg",
            toArtworkPath = "artwork/to.jpg",
            durationMs = 3_800L,
        )

        assertEquals(7L, transition.id)
        assertEquals("artwork/from.jpg", transition.fromArtworkPath)
        assertEquals("artwork/to.jpg", transition.toArtworkPath)
        assertEquals(3_800L, transition.durationMs)
    }

    @Test
    fun `crossfade fade out never extends past the outgoing track`() {
        assertEquals(4_000L, crossfadeDurationMs(4, positionMs = 116_000L, durationMs = 120_000L))
        // The service ticker can enter the crossfade window after its leading edge.
        assertEquals(3_800L, crossfadeDurationMs(4, positionMs = 116_200L, durationMs = 120_000L))
        assertEquals(0L, crossfadeDurationMs(4, positionMs = 120_000L, durationMs = 120_000L))
    }

    @Test
    fun `next preload waits until the outgoing crossfade source has retired`() {
        assertFalse(canPreloadNext(isCrossfading = true))
        assertTrue(canPreloadNext(isCrossfading = false))
    }
    @Test
    fun `audio becoming noisy pauses playback`() {
        assertTrue(audioBecomingNoisyRequiresPause(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        assertFalse(audioBecomingNoisyRequiresPause("me.misa198.airmedy.player.PAUSE"))
        assertFalse(audioBecomingNoisyRequiresPause(null))
    }

    @Test
    fun `disconnected AAudio output recreates the stream`() {
        assertTrue(audioOutputDisconnectRequiresRecovery(isOutputDisconnected = true))
        assertFalse(audioOutputDisconnectRequiresRecovery(isOutputDisconnected = false))
    }

    @Test
    fun `new play actions take precedence over session restoration`() {
        assertTrue(playbackActionReplacesRestoredQueue(PlaybackService.ActionPlay))
        assertTrue(playbackActionReplacesRestoredQueue(PlaybackService.ActionShuffle))
        assertFalse(playbackActionReplacesRestoredQueue(PlaybackService.ActionResume))
        assertFalse(playbackActionReplacesRestoredQueue(null))
    }

    @Test
    fun `request retains a valid queue start index`() {
        val request = PlaybackRequest(listOf("track-1", "track-2"), startIndex = 1)

        assertEquals(listOf("track-1", "track-2"), request.trackIds)
        assertEquals(1, request.startIndex)
    }

    @Test
    fun `request rejects empty queue and invalid start index`() {
        assertThrows(IllegalArgumentException::class.java) { PlaybackRequest(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { PlaybackRequest(listOf("track-1"), startIndex = 1) }
    }

    @Test
    fun `seek position is clamped to the loaded track duration`() {
        assertEquals(0L, clampSeekPosition(-1L, 10_000L))
        assertEquals(5_000L, clampSeekPosition(5_000L, 10_000L))
        assertEquals(10_000L, clampSeekPosition(12_000L, 10_000L))
    }
}
