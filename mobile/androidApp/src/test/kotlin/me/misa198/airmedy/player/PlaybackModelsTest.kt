package me.misa198.airmedy.player

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json

class PlaybackModelsTest {
    @Test
    fun `equalizer preset catalog matches desktop constraints`() {
        assertEquals(31, EqualizerPresets.size)
        assertEquals("flat", EqualizerPresets.first().key)
        assertEquals(listOf(8f, 5f, -5.5f, -8f, -3f, 4f, 9f, 11f, 11f, 11f), presetFor("rock").gainsDb)
        EqualizerPresets.forEach { preset ->
            assertEquals(10, preset.gainsDb.size)
            assertTrue(preset.gainsDb.all { it in -12f..12f && it * 2f == kotlin.math.round(it * 2f) })
        }
    }

    @Test
    fun `equalizer gain is clamped to half decibel steps`() {
        assertEquals(-12f, normalizeEqGain(-13f))
        assertEquals(1.5f, normalizeEqGain(1.26f))
        assertEquals(0f, normalizeEqGain(-0.1f))
        assertEquals(12f, normalizeEqGain(99f))
    }

    @Test
    fun `equalizer keeps edits for each preset`() {
        val editedRock = presetFor("rock").gainsDb.toMutableList().also { it[0] = 2.5f }
        val settings = EqualizerSettings(presetKey = "rock", editedGainsDb = mapOf("rock" to editedRock))

        assertEquals(2.5f, settings.gainsDb[0])
        assertEquals(presetFor("flat").gainsDb, settings.copy(presetKey = "flat").gainsDb)
        assertEquals(editedRock, settings.copy(presetKey = "rock").gainsDb)
    }

    @Test
    fun `equalizer user profiles persist and become selectable`() {
        val profile = EqualizerProfile("user_test", "My EQ", List(10) { 0f }, isDefault = false)
        val settings = EqualizerSettings(presetKey = profile.key, userProfiles = parseUserProfiles(encodeUserProfiles(listOf(profile))))

        assertEquals(profile, settings.selectedProfile)
        assertEquals(List(10) { 0f }, settings.gainsDb)
        assertFalse(settings.selectedProfile.isDefault)
    }

    @Test
    fun `playback session persists queue and paused position`() {
        val session = PlaybackSession(
            queue = PlaybackQueueSnapshot(originalTrackIds = listOf("track-1"), activeTrackIds = listOf("track-1"), currentIndex = 0),
            positionMs = 42_000L,
        )

        assertEquals(session, decodePlaybackSession(encodePlaybackSession(session)))
    }

    @Test
    fun `queue-only session from an earlier app version remains restorable`() {
        val legacyQueue = PlaybackQueueSnapshot(
            originalTrackIds = listOf("track-1"),
            activeTrackIds = listOf("track-1"),
            currentIndex = 0,
        )

        assertEquals(PlaybackSession(queue = legacyQueue), decodePlaybackSession(Json.encodeToString(legacyQueue)))
    }

    @Test
    fun `restoration drops tracks removed from the synced library`() {
        val filtered = queueForAvailableTracks(
            PlaybackQueueSnapshot(
                originalTrackIds = listOf("kept", "deleted"),
                activeTrackIds = listOf("deleted", "kept"),
                currentIndex = 0,
            ),
            availableTrackIds = setOf("kept"),
        )

        assertEquals(listOf("kept"), filtered.originalTrackIds)
        assertEquals(listOf("kept"), filtered.activeTrackIds)
    }

    @Test
    fun `media queue selection follows the active queue`() {
        assertEquals(1L, activeQueueItemId(PlaybackQueueSnapshot(activeTrackIds = listOf("one", "two"), currentIndex = 1)))
        assertEquals(-1L, activeQueueItemId(PlaybackQueueSnapshot(activeTrackIds = listOf("one"), currentIndex = -1)))
    }

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
    fun `audio focus changes preserve pause duck and restore behavior`() {
        assertEquals(AudioFocusChangeAction.Pause, audioFocusChangeAction(AudioManager.AUDIOFOCUS_LOSS))
        assertEquals(AudioFocusChangeAction.PauseAndResumeOnGain, audioFocusChangeAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertEquals(AudioFocusChangeAction.Duck, audioFocusChangeAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertEquals(AudioFocusChangeAction.Restore, audioFocusChangeAction(AudioManager.AUDIOFOCUS_GAIN))
    }

    @Test
    fun `disconnected AAudio output recreates the stream`() {
        assertTrue(audioOutputDisconnectRequiresRecovery(isOutputDisconnected = true))
        assertFalse(audioOutputDisconnectRequiresRecovery(isOutputDisconnected = false))
    }

    @Test
    fun `resume after natural queue completion restarts the queue`() {
        assertTrue(shouldRestartQueueOnResume(pausedPositionMs = 120_000L, durationMs = 120_000L, hasDecoder = false))
        assertFalse(shouldRestartQueueOnResume(pausedPositionMs = 119_999L, durationMs = 120_000L, hasDecoder = false))
        assertFalse(shouldRestartQueueOnResume(pausedPositionMs = 120_000L, durationMs = 120_000L, hasDecoder = true))
    }

    @Test
    fun `manual queue exhaustion rewinds while natural completion retains the end position`() {
        assertEquals(0L, stoppedCurrentPosition(PlaybackEndReason.SKIPPED, 120_000L))
        assertEquals(120_000L, stoppedCurrentPosition(PlaybackEndReason.COMPLETED, 120_000L))
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
