package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** Theme-aware one-dB slider for the desktop-compatible target LUFS range. */
@Composable
internal fun LufsTargetSlider(targetLufs: Float, enabled: Boolean, onTargetChanged: (Float) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAirmedyColors.current
    val range = -30f..-5f
    val value = targetLufs.coerceIn(range.start, range.endInclusive)
    fun updateAt(x: Float, width: Float) {
        if (enabled && width > 0f) onTargetChanged((range.start + (range.endInclusive - range.start) * (x / width).coerceIn(0f, 1f)).roundToInt().toFloat())
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(48.dp).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, 0)
                if (enabled) setProgress { requested -> onTargetChanged(requested.roundToInt().toFloat().coerceIn(range.start, range.endInclusive)); true }
            }.pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateAt(down.position.x, size.width.toFloat())
                    horizontalDrag(down.id) { change -> change.consume(); updateAt(change.position.x, size.width.toFloat()) }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                val fraction = (value - range.start) / (range.endInclusive - range.start)
                val height = 6.dp.toPx(); val centerY = 24.dp.toPx(); val radius = height / 2f
                drawRoundRect(colors.buttonSecondary, Offset(0f, centerY - radius), Size(size.width, height), CornerRadius(radius, radius))
                val thumbX = size.width * fraction
                drawRoundRect(colors.primary, Offset(0f, centerY - radius), Size(thumbX, height), CornerRadius(radius, radius))
                drawCircle(colors.primary, radius = 10.dp.toPx(), center = Offset(thumbX, centerY))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.playback_normalization_target_minimum), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            Text(stringResource(R.string.playback_normalization_target_maximum), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
    }
}
