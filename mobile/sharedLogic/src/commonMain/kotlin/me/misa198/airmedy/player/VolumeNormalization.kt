package me.misa198.airmedy.player

import kotlin.math.min

enum class NormalizationMode { Track, Album }

data class NormalizationSettings(
    val enabled: Boolean = false,
    val mode: NormalizationMode = NormalizationMode.Track,
    val targetLufs: Float = -14f,
    val preventClip: Boolean = true,
)

data class TrackAnalysis(val loudnessLufs: Float, val truePeak: Float)

/** Pure desktop-compatible loudness gain calculation. */
fun normalizationGainDb(
    settings: NormalizationSettings,
    analysis: TrackAnalysis?,
    albumAnalyses: List<TrackAnalysis> = emptyList(),
    continuousAlbum: Boolean = false,
): Float {
    if (!settings.enabled || analysis == null) return 0f
    val sourceLufs = if (settings.mode == NormalizationMode.Album && continuousAlbum && albumAnalyses.isNotEmpty()) {
        albumAnalyses.map { it.loudnessLufs }.average().toFloat()
    } else analysis.loudnessLufs
    val gain = settings.targetLufs - sourceLufs
    return if (settings.preventClip) min(gain, -analysis.truePeak) else gain
}
