package me.misa198.airmedy.ui.components

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import kotlin.math.sqrt

/** A double-triangle previous/next glyph with the shared transport cross-scale. */
@Composable
fun AnimatedSkipSymbol(
    forward: Boolean,
    isPressed: Boolean,
    tint: Color,
    size: Dp,
    touchTargetSize: Dp,
    modifier: Modifier = Modifier,
) {
    val glyphScale by animateFloatAsState(
        targetValue = if (isPressed) PressedGlyphScale else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) 170 else 220,
            easing = FastOutSlowInEasing,
        ),
        label = "skip-symbol-press-scale",
    )
    val releaseProgress = remember { androidx.compose.animation.core.Animatable(1f) }
    var hasPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            hasPressed = true
            releaseProgress.snapTo(0f)
        } else if (hasPressed) {
            releaseProgress.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier = modifier.size(touchTargetSize)) {
        val radius = size.toPx() / 2f
        if (isPressed) {
            drawCircle(tint.copy(alpha = 0.11f), radius * 1.34f, center)
        } else if (releaseProgress.value < 1f) {
            drawCircle(
                tint.copy(alpha = 0.14f * (1f - releaseProgress.value)),
                radius * (1.34f + 0.38f * releaseProgress.value),
                center,
            )
        }
        if (isPressed) {
            drawSkipGlyph(forward, glyphScale, 0f, size.toPx(), tint)
        } else {
            val progress = releaseProgress.value
            val travel = size.toPx() * ReleaseTravelRatio
            // The outgoing glyph remains at the resting centre while the
            // replacement travels within the final footprint and expands into
            // place, so no part of the animation overshoots the icon's real
            // resting geometry.
            val direction = if (forward) 1f else -1f
            val outgoingAlpha = 1f - (progress / OutgoingFadeDuration).coerceIn(0f, 1f)
            val incomingProgress = ((progress - IncomingDelay) / (1f - IncomingDelay)).coerceIn(0f, 1f)
            drawSkipGlyph(
                forward,
                scale = glyphScale + progress * (1f - glyphScale),
                translationX = 0f,
                iconSize = size.toPx(),
                color = tint.copy(alpha = tint.alpha * outgoingAlpha),
            )
            clipRect(
                left = center.x - size.toPx() / 2f,
                top = center.y - size.toPx() / 2f,
                right = center.x + size.toPx() / 2f,
                bottom = center.y + size.toPx() / 2f,
            ) {
                drawSkipGlyph(
                    forward,
                    scale = TransitionMinimumScale + incomingProgress * (1f - TransitionMinimumScale),
                    translationX = -direction * travel * (1f - incomingProgress),
                    iconSize = size.toPx(),
                    color = tint.copy(alpha = tint.alpha * incomingProgress),
                )
            }
        }
    }
}

private fun DrawScope.drawSkipGlyph(
    forward: Boolean,
    scale: Float,
    translationX: Float,
    iconSize: Float,
    color: Color,
) {
    drawPath(skipGlyphPath(forward, scale, translationX, iconSize), color)
}

private fun DrawScope.skipGlyphPath(
    forward: Boolean,
    scale: Float,
    translationX: Float,
    iconSize: Float,
): Path {
    val iconLeft = center.x - iconSize / 2f
    val iconTop = center.y - iconSize / 2f
    fun x(value: Float): Float {
        val unscaled = iconLeft + if (forward) iconSize * value / 24f else iconSize * (24f - value) / 24f
        return center.x + (unscaled - center.x) * scale + translationX
    }
    fun y(value: Float): Float {
        val unscaled = iconTop + iconSize * value / 24f
        return center.y + (unscaled - center.y) * scale
    }
    fun triangle(left: Float, scale: Float): Path {
        val centreX = left + SkipTriangleWidth / 2f
        fun scaledX(value: Float) = centreX + (value - centreX) * scale
        return roundedPath(
            listOf(
                Offset(x(scaledX(left)), y(SkipTriangleTop)),
                Offset(x(scaledX(left + SkipTriangleWidth)), y(SkipTriangleCentre)),
                Offset(x(scaledX(left)), y(SkipTriangleBottom)),
            ),
            iconSize * 0.095f * scale,
            sharpCorners = emptySet(),
        )
    }
    // Both glyphs are equilateral. Their sharp inner tips touch the adjacent
    // base exactly, while the outward corners stay softly rounded.
    return Path().apply {
        addPath(triangle(SkipTriangleStart, 1f))
        addPath(triangle(SkipTriangleStart + SkipTriangleWidth, 1f))
    }
}

private const val PressedGlyphScale = 0.68f
private const val TransitionMinimumScale = 0.48f
private const val ReleaseTravelRatio = 0.34f
private const val OutgoingFadeDuration = 0.76f
private const val IncomingDelay = 0.28f
private const val SkipTriangleTop = 5.5f
private const val SkipTriangleBottom = 18.5f
private const val SkipTriangleCentre = 12f
private const val SkipTriangleWidth = 11.26f
private const val SkipTriangleStart = 1.5f

private fun roundedPath(
    points: List<Offset>,
    radius: Float,
    sharpCorners: Set<Int> = emptySet(),
): Path {
    fun toward(from: Offset, to: Offset): Offset {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val distance = sqrt(dx * dx + dy * dy)
        val amount = radius.coerceAtMost(distance / 2f)
        return if (distance == 0f) from else Offset(from.x + dx * amount / distance, from.y + dy * amount / distance)
    }
    val starts = points.indices.map { index ->
        if (index in sharpCorners) points[index] else toward(points[index], points[(index - 1 + points.size) % points.size])
    }
    val ends = points.indices.map { index ->
        if (index in sharpCorners) points[index] else toward(points[index], points[(index + 1) % points.size])
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
