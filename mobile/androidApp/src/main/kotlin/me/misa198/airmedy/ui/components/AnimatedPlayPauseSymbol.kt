package me.misa198.airmedy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import kotlin.math.sqrt

/** Separate Play and Pause glyphs that cross-scale inside a tactile halo. */
@Composable
fun AnimatedPlayPauseSymbol(
    isPlaying: Boolean,
    isPreparing: Boolean,
    isPressed: Boolean,
    tint: Color,
    size: Dp,
    touchTargetSize: Dp,
    modifier: Modifier = Modifier,
) {
    // Advancing tracks briefly publishes Preparing before the next Playing
    // state. Retain the last settled glyph through that hand-off so Pause does
    // not flash to Play for a frame.
    var lastSettledIsPlaying by remember { mutableStateOf(isPlaying) }
    LaunchedEffect(isPlaying, isPreparing) {
        if (!isPreparing) lastSettledIsPlaying = isPlaying
    }
    val displayedIsPlaying = if (isPreparing) lastSettledIsPlaying else isPlaying
    val pauseProgress by animateFloatAsState(
        targetValue = if (displayedIsPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "play-pause-cross-scale",
    )
    val glyphScale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1f,
        animationSpec = tween(durationMillis = if (isPressed) 120 else 220, easing = FastOutSlowInEasing),
        label = "play-pause-press-scale",
    )
    val releasePulse = remember { Animatable(1f) }
    var wasPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            wasPressed = true
            releasePulse.snapTo(1f)
        } else if (wasPressed) {
            wasPressed = false
            releasePulse.snapTo(0f)
            releasePulse.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
        }
    }

    Canvas(modifier = modifier.size(touchTargetSize)) {
        val iconRadius = size.toPx() / 2f
        val center = this.center
        if (isPressed) {
            drawCircle(color = tint.copy(alpha = 0.10f), radius = iconRadius * 1.08f, center = center)
        } else if (releasePulse.value < 1f) {
            drawCircle(
                color = tint.copy(alpha = 0.14f * (1f - releasePulse.value)),
                radius = iconRadius * (1.08f + 0.42f * releasePulse.value),
                center = center,
            )
        }
        drawPlayPauseCrossScale(pauseProgress, glyphScale, size.toPx(), tint)
    }
}

private fun DrawScope.drawPlayPauseCrossScale(
    pauseProgress: Float,
    glyphScale: Float,
    iconSizePx: Float,
    color: Color,
) {
    fun pathOf(
        points: List<Pair<Float, Float>>,
        scale: Float,
        cornerRadius: Float,
    ): Path {
        val iconLeft = center.x - iconSizePx / 2f
        val iconTop = center.y - iconSizePx / 2f
        fun x(value: Float) = iconLeft + iconSizePx * (value / IconViewport - 0.5f) * scale + iconSizePx / 2f
        fun y(value: Float) = iconTop + iconSizePx * (value / IconViewport - 0.5f) * scale + iconSizePx / 2f
        return roundedPath(
            points.map { (pointX, pointY) -> Offset(x(pointX), y(pointY)) },
            cornerRadius = cornerRadius * scale,
        )
    }
    val playAlpha = 1f - pauseProgress
    val pauseAlpha = pauseProgress
    if (playAlpha > 0f) {
        drawPath(
            pathOf(
                listOf(6f to 3.5f, 20f to 12f, 6f to 20.5f),
                scale = glyphScale * (1f - pauseProgress * (1f - TransitionMinimumScale)),
                cornerRadius = iconSizePx * 0.10f,
            ),
            color.copy(alpha = color.alpha * playAlpha),
        )
    }
    if (pauseAlpha > 0f) {
        val pauseScale = glyphScale * (TransitionMinimumScale + pauseProgress * (1f - TransitionMinimumScale))
        drawPath(
            pathOf(listOf(5.5f to 4f, 10.2f to 4f, 10.2f to 20f, 5.5f to 20f), pauseScale, iconSizePx * 0.075f),
            color.copy(alpha = color.alpha * pauseAlpha),
        )
        drawPath(
            pathOf(listOf(13.8f to 4f, 18.5f to 4f, 18.5f to 20f, 13.8f to 20f), pauseScale, iconSizePx * 0.075f),
            color.copy(alpha = color.alpha * pauseAlpha),
        )
    }
}

private fun roundedPath(points: List<Offset>, cornerRadius: Float): Path {
    fun pointAlong(from: Offset, toward: Offset): Offset {
        val delta = toward - from
        val length = sqrt(delta.x * delta.x + delta.y * delta.y)
        val distance = cornerRadius.coerceAtMost(length / 2f)
        return if (length == 0f) from else from + delta * (distance / length)
    }
    val starts = points.indices.map { index ->
        pointAlong(points[index], points[(index - 1 + points.size) % points.size])
    }
    val ends = points.indices.map { index ->
        pointAlong(points[index], points[(index + 1) % points.size])
    }
    return Path().apply {
        moveTo(starts.first().x, starts.first().y)
        points.indices.forEach { index ->
            quadraticTo(points[index].x, points[index].y, ends[index].x, ends[index].y)
            val next = (index + 1) % points.size
            lineTo(starts[next].x, starts[next].y)
        }
        close()
    }
}

private const val IconViewport = 24f
private const val TransitionMinimumScale = 0.52f
