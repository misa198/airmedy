package me.misa198.airmedy.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenPlayerSeekTest {
    @Test
    fun `keeps seek preview until playback position reaches selected target`() {
        assertFalse(
            hasConfirmedSeekPosition(
                seekFraction = 0.75f,
                playbackPositionMs = 12_000L,
                durationMs = 60_000L,
            ),
        )

        assertTrue(
            hasConfirmedSeekPosition(
                seekFraction = 0.75f,
                playbackPositionMs = 45_150L,
                durationMs = 60_000L,
            ),
        )
    }
}
