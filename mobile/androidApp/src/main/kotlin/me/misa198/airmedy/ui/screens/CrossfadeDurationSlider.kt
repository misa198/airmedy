package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import kotlin.math.roundToInt

/** Settings-specific slider: theme-primary fill and a visible draggable thumb. */
@Composable
internal fun CrossfadeDurationSlider(
    seconds: Int,
    enabled: Boolean,
    onSecondsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val value = seconds.coerceIn(1, 12).toFloat()
    val range = 1f..12f
    val fraction = (value - range.start) / (range.endInclusive - range.start)
    fun updateAt(x: Float, width: Float) {
        if (enabled && width > 0f) {
            val selectedSeconds = range.start +
                (range.endInclusive - range.start) * (x / width).coerceIn(0f, 1f)
            onSecondsChanged(selectedSeconds.roundToInt().coerceIn(1, 12))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .height(48.dp)
            .testTag(CrossfadeDurationSliderTrackTag)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, 0)
                if (enabled) setProgress { requested ->
                    onSecondsChanged(requested.toInt().coerceIn(1, 12))
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateAt(down.position.x, size.width.toFloat())
                    horizontalDrag(down.id) { change ->
                        change.consume()
                        updateAt(change.position.x, size.width.toFloat())
                    }
                }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                drawCrossfadeTrack(
                    fraction = fraction,
                    trackColor = colors.buttonSecondary,
                    fillColor = colors.primary,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.playback_crossfade_minimum),
                modifier = Modifier.testTag(CrossfadeDurationSliderMinimumTag),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
            Text(
                text = stringResource(R.string.playback_crossfade_maximum),
                modifier = Modifier.testTag(CrossfadeDurationSliderMaximumTag),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )
        }
    }
}

internal const val CrossfadeDurationSliderTrackTag = "crossfade-duration-slider-track"
internal const val CrossfadeDurationSliderMinimumTag = "crossfade-duration-slider-minimum"
internal const val CrossfadeDurationSliderMaximumTag = "crossfade-duration-slider-maximum"

private fun DrawScope.drawCrossfadeTrack(fraction: Float, trackColor: androidx.compose.ui.graphics.Color, fillColor: androidx.compose.ui.graphics.Color) {
    val trackHeight = 6.dp.toPx()
    val centerY = 24.dp.toPx()
    val radius = trackHeight / 2f
    drawRoundRect(trackColor, Offset(0f, centerY - radius), Size(size.width, trackHeight), CornerRadius(radius, radius))
    val thumbX = (size.width * fraction).coerceIn(0f, size.width)
    drawRoundRect(fillColor, Offset(0f, centerY - radius), Size(thumbX, trackHeight), CornerRadius(radius, radius))
    drawCircle(fillColor, radius = 10.dp.toPx(), center = Offset(thumbX, centerY))
    // The 12 integer settings are visible as a fixed scale below the track.
    repeat(12) { index ->
        val tickX = size.width * index / 11f
        drawCircle(trackColor, radius = 2.dp.toPx(), center = Offset(tickX, 42.dp.toPx()))
    }
}
