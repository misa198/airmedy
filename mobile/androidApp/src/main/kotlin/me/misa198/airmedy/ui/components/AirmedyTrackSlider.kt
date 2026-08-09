package me.misa198.airmedy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/**
 * Shared full-width control track for playback position and volume.
 *
 * This intentionally does not use Material Slider: fullscreen playback needs a
 * glass track with a white value fill and no Material-style circular thumb.
 */
@Composable
fun AirmedyTrackSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    trackHeight: Dp = 3.dp,
) {
    val colors = LocalAirmedyColors.current
    val safeValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val rangeSize = valueRange.endInclusive - valueRange.start
    val fraction = if (rangeSize > 0f) {
        ((safeValue - valueRange.start) / rangeSize).coerceIn(0f, 1f)
    } else {
        0f
    }
    val updateValueFromPosition: (Float, Float) -> Unit = { x, width ->
        if (enabled && width > 0f) {
            onValueChange(
                (valueRange.start + rangeSize * (x / width).coerceIn(0f, 1f))
                    .coerceIn(valueRange.start, valueRange.endInclusive),
            )
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
                        onValueChange(requestedValue.coerceIn(valueRange.start, valueRange.endInclusive))
                        true
                    }
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateValueFromPosition(down.position.x, size.width.toFloat())
                    horizontalDrag(down.id) { change ->
                        change.consume()
                        updateValueFromPosition(change.position.x, size.width.toFloat())
                    }
                    onValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight),
        ) {
            drawTrack(if (enabled) colors.sliderTrack else colors.textMuted.copy(alpha = 0.45f), 1f)
            if (enabled) {
                drawTrack(colors.onPrimary, fraction)
            }
        }
    }
}

private fun DrawScope.drawTrack(color: androidx.compose.ui.graphics.Color, fraction: Float) {
    val width = size.width * fraction
    if (width <= 0f) return
    val radius = size.height / 2f
    drawRoundRect(
        color = color,
        size = Size(width, size.height),
        cornerRadius = CornerRadius(radius, radius),
    )
}
