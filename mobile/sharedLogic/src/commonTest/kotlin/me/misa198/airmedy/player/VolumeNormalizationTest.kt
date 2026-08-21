package me.misa198.airmedy.player

import kotlin.test.Test
import kotlin.test.assertEquals

class VolumeNormalizationTest {
    private val analyzed = TrackAnalysis(loudnessLufs = -20f, truePeak = -1f)

    @Test fun disabledOrMissingAnalysisIsNeutral() {
        assertEquals(0f, normalizationGainDb(NormalizationSettings(), analyzed))
        assertEquals(0f, normalizationGainDb(NormalizationSettings(enabled = true), null))
    }

    @Test fun trackGainTargetsLoudnessAndPreventsClip() {
        assertEquals(1f, normalizationGainDb(NormalizationSettings(enabled = true), analyzed))
        assertEquals(6f, normalizationGainDb(NormalizationSettings(enabled = true, preventClip = false), analyzed))
    }

    @Test fun continuousAlbumUsesAverageOtherwiseTrack() {
        val settings = NormalizationSettings(enabled = true, mode = NormalizationMode.Album, preventClip = false)
        assertEquals(4f, normalizationGainDb(settings, analyzed, listOf(analyzed, TrackAnalysis(-16f, -2f)), true))
        assertEquals(6f, normalizationGainDb(settings, analyzed, listOf(analyzed, TrackAnalysis(-16f, -2f)), false))
    }
}
