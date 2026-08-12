package me.misa198.airmedy.player

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModelsTest {
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
