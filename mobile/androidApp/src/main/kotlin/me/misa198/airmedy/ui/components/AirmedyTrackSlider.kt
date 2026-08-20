package me.misa198.airmedy.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.ui.theme.AirmedyColors
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/**
 * Shared full-width control track for playback position and volume.
 *
 * This intentionally does not use Material Slider: fullscreen playback needs a
 * stable translucent track with a white value fill and no Material-style
 * circular thumb. The track deliberately does not blur the artwork behind it:
 * rendering Haze within a 6dp-high clipped shape produces visible sampling
 * artifacts on the unfilled portion on some Android GPUs.
 */
@Composable
fun AirmedyTrackSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    onInteractionChange: (Boolean) -> Unit = {},
    trackHeight: Dp = 3.dp,
    activeTrackColor: Color? = null,
    inactiveTrackColor: Color? = null,
    trackAlignment: Alignment = Alignment.Center,
) {
    val colors = LocalAirmedyColors.current
    var isInteracting by remember { mutableStateOf(false) }
    var dragPreviewValue by remember { mutableStateOf<Float?>(null) }
    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isInteracting) trackHeight + 3.dp else trackHeight,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "airmedy-track-slider-height",
    )
    val animatedTrackScaleX by animateFloatAsState(
        targetValue = if (isInteracting) 1.03f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "airmedy-track-slider-scale-x",
    )
    val animatedTrackScaleY by animateFloatAsState(
        targetValue = if (isInteracting) 1.10f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "airmedy-track-slider-scale-y",
    )
    val filledTrackColor by animateColorAsState(
        targetValue = activeTrackColor ?: sliderFilledTrackColor(colors, isInteracting),
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "airmedy-track-slider-fill-colour",
    )
    // The pointer-input coroutine is intentionally not restarted when a new
    // track changes only the callback's captured duration. Always dispatch to
    // the most recent lambdas instead.
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished = rememberUpdatedState(onValueChangeFinished)
    val currentOnInteractionChange = rememberUpdatedState(onInteractionChange)
    val safeValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    // pointerInput is intentionally long-lived. Keep its press-time anchor
    // current as playback progress recomposes beneath it.
    val currentSafeValue = rememberUpdatedState(safeValue)
    val displayedValue = dragPreviewValue ?: safeValue
    val animatedDisplayedValue by animateFloatAsState(
        targetValue = displayedValue,
        // Keep the fill locked to the finger during a drag, then gently settle
        // onto Android's discrete system-volume step after release.
        animationSpec = if (isInteracting) snap() else tween(140, easing = FastOutSlowInEasing),
        label = "airmedy-track-slider-value",
    )
    val rangeSize = valueRange.endInclusive - valueRange.start
    val fraction = if (rangeSize > 0f) {
        ((animatedDisplayedValue - valueRange.start) / rangeSize).coerceIn(0f, 1f)
    } else {
        0f
    }
    val updateValueFromDrag: (Float, Float, Float) -> Unit = { startValue, horizontalDistance, width ->
        if (enabled && width > 0f) {
            val nextValue = (startValue + rangeSize * (horizontalDistance / width))
                .coerceIn(valueRange.start, valueRange.endInclusive)
            dragPreviewValue = nextValue
            currentOnValueChange.value(nextValue)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(safeValue, valueRange, 0)
                if (enabled) {
                    setProgress { requestedValue ->
                        currentOnValueChange.value(requestedValue.coerceIn(valueRange.start, valueRange.endInclusive))
                        true
                    }
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dragStartValue = dragPreviewValue ?: currentSafeValue.value
                    isInteracting = true
                    currentOnInteractionChange.value(true)
                    var didDrag = false
                    try {
                        val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                            // Anchor the drag to the current value rather than
                            // the point where the finger first landed.
                            change.consume()
                            didDrag = true
                            updateValueFromDrag(
                                dragStartValue,
                                change.position.x - down.position.x,
                                size.width.toFloat(),
                            )
                        }
                        if (dragStart != null) {
                            horizontalDrag(dragStart.id) { change ->
                                change.consume()
                                updateValueFromDrag(
                                    dragStartValue,
                                    change.position.x - down.position.x,
                                    size.width.toFloat(),
                                )
                            }
                        }
                        if (didDrag) currentOnValueChangeFinished.value?.invoke()
                    } finally {
                        isInteracting = false
                        dragPreviewValue = null
                        currentOnInteractionChange.value(false)
                    }
                }
            },
        contentAlignment = trackAlignment,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedTrackHeight)
                .graphicsLayer {
                    scaleX = animatedTrackScaleX
                    scaleY = animatedTrackScaleY
                }
                .clip(CircleShape)
                .background(inactiveTrackColor ?: colors.sliderInactive)
                .testTag(AirmedyTrackSliderTrackTestTag),
        ) {
            // A disabled control remains discoverable; only its interaction and
            // filled value are muted, never the frosted-glass track itself.
            if (enabled) {
                drawTrack(filledTrackColor, fraction)
            }
        }
    }
}

internal const val AirmedyTrackSliderTrackTestTag = "airmedy-track-slider-track"

/** The fill tint shared by fullscreen slider supporting controls. */
internal fun sliderFilledTrackColor(
    colors: AirmedyColors,
    isInteracting: Boolean,
): Color = if (isInteracting) {
    colors.onPrimary
} else {
    lerp(colors.foregroundSubtle, colors.onPrimary, 0.5f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrack(
    color: androidx.compose.ui.graphics.Color,
    fraction: Float,
) {
    val width = size.width * fraction
    if (width <= 0f) return
    val radius = size.height / 2f
    drawRoundRect(
        color = color,
        size = androidx.compose.ui.geometry.Size(width, size.height),
        cornerRadius = CornerRadius(radius, radius),
    )
}
