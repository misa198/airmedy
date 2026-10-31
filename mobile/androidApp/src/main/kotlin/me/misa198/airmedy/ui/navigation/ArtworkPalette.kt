package me.misa198.airmedy.ui.navigation

import androidx.compose.ui.graphics.Color
import kotlin.math.min
import kotlin.math.sqrt

internal data class ArtworkPalette(val vibrant: Color, val dominant: Color, val muted: Color, val backdrop: Color) {
    fun shaderColors(): FloatArray {
        val averageBrightness = brightness(backdrop)
        val backgroundMuted = if (averageBrightness >= 220f && brightness(muted) < 40f) backdrop else muted
        val colors = listOf(vibrant, dominant, backgroundMuted).flatMap { color ->
            val luminance = color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
            val scale = if (luminance > 0.85f) 0.85f / luminance else 1f
            listOf(color.red * scale, color.green * scale, color.blue * scale)
        }.toFloatArray()
        return colors + floatArrayOf(colors[3] * 0.2f, colors[4] * 0.2f, colors[5] * 0.2f)
    }
}

private fun brightness(c: Color): Float = sqrt(0.299f*c.red*c.red + 0.587f*c.green*c.green + 0.114f*c.blue*c.blue) * 255f

/** Desktop palette rules, operating only on the small decoded thumbnail. */
internal fun extractArtworkPalette(argb: IntArray, backdropArgb: IntArray = argb): ArtworkPalette? {
    val pixels = argb.filter { (it ushr 24) >= 128 }.map {
        floatArrayOf(((it shr 16) and 255).toFloat(), ((it shr 8) and 255).toFloat(), (it and 255).toFloat())
    }
    if (pixels.isEmpty()) return null
    fun distance(a: FloatArray, b: FloatArray) = a.indices.sumOf { ((a[it]-b[it])*(a[it]-b[it])).toDouble() }
    fun nearest(p: FloatArray, centers: List<FloatArray>) = centers.indices.minBy { distance(p, centers[it]) }
    val centers = mutableListOf(pixels.first().copyOf())
    repeat(min(3, pixels.size) - 1) {
        centers += pixels.maxBy { p -> distance(p, centers[nearest(p, centers)]) }.copyOf()
    }
    repeat(10) {
        val sums = Array(centers.size) { FloatArray(3) }
        val counts = IntArray(centers.size)
        pixels.forEach { p ->
            val index = nearest(p, centers)
            counts[index]++
            repeat(3) { sums[index][it] += p[it] }
        }
        centers.indices.forEach { i -> if (counts[i] > 0) repeat(3) { centers[i][it] = (sums[i][it] / counts[i]).toInt().toFloat() } }
    }
    val counts = IntArray(centers.size)
    pixels.forEach { counts[nearest(it, centers)]++ }
    val dominant = counts.indices.maxBy { counts[it] }
    val vibrant = counts.indices.filter { counts[it].toFloat() / pixels.size >= 0.004f }.maxByOrNull {
        val c = centers[it]
        (c.max() - c.min()) * min(1f, sqrt(counts[it].toFloat() / pixels.size / 0.02f))
    } ?: dominant
    val backdropPixels = backdropArgb.filter { (it ushr 24) >= 128 }
    val average = FloatArray(3) { channel ->
        if (backdropPixels.isEmpty()) 0f else
            (backdropPixels.sumOf { ((it shr (16 - channel * 8)) and 255).toLong() } / backdropPixels.size).toFloat()
    }
    val muted = counts.indices.filter { it != dominant && it != vibrant && counts[it] * 50 >= pixels.size }.maxByOrNull { counts[it] }
    fun color(c: FloatArray) = Color(c[0] / 255f, c[1] / 255f, c[2] / 255f)
    return ArtworkPalette(color(centers[vibrant]), color(centers[dominant]), color(muted?.let { centers[it] } ?: average), color(average))
}

internal fun blendArtworkPalette(from: FloatArray, to: FloatArray, progress: Float): FloatArray =
    FloatArray(from.size) { from[it] * equalPowerOutgoing(progress) + to[it] * equalPowerIncoming(progress) }

internal fun approachArtworkPalette(from: FloatArray, to: FloatArray, deltaSeconds: Float): FloatArray {
    val weight = 1f - kotlin.math.exp(-deltaSeconds.coerceIn(0f, 0.1f) * 1.5f)
    return FloatArray(from.size) { from[it] + (to[it] - from[it]) * weight }
}

/** Freeze displayed colors once per transition, including when its palette arrives late. */
internal class ArtworkPaletteAnimation {
    private var target: FloatArray? = null
    private var current: FloatArray? = null
    private var transitionId: Long? = null
    private var transitionFrom = target
    private var progress = 1f

    fun update(colors: FloatArray?, outgoing: FloatArray?, id: Long?, crossfadeProgress: Float) {
        if (id != transitionId && id != null) transitionFrom = current?.copyOf() ?: outgoing?.copyOf()
        if (transitionFrom == null) transitionFrom = outgoing?.copyOf() ?: colors?.copyOf()
        transitionId = id
        progress = crossfadeProgress
        if (colors != null) target = colors.copyOf()
    }

    fun frame(deltaSeconds: Float, motionEnabled: Boolean): FloatArray? {
        val colors = target ?: return null
        val displayed = when {
            !motionEnabled -> colors.copyOf()
            transitionId != null -> blendArtworkPalette(transitionFrom ?: colors, colors, progress)
            current == null -> colors.copyOf()
            else -> approachArtworkPalette(current!!, colors, deltaSeconds)
        }
        current = displayed
        return displayed
    }
}
