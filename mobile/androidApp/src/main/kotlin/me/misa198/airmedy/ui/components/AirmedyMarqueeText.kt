package me.misa198.airmedy.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt

/** A single-line marquee that travels to the end of overflowing text and reverses direction. */
@Composable
fun AirmedyMarqueeText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().clipToBounds()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val availableWidthPx = with(density) { maxWidth.roundToPx() }
        val textWidthPx = remember(text, style, density) {
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = style,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            ).size.width
        }
        val travelDistancePx = (textWidthPx - availableWidthPx).coerceAtLeast(0)
        val targetOffset = if (travelDistancePx > 0) -travelDistancePx.toFloat() else 0f
        val totalDurationMs = if (travelDistancePx > 0) {
            ((travelDistancePx / 20f + 4f) * 1000f).roundToInt().coerceAtLeast(4_000)
        } else {
            4_000
        }
        val pauseStartMs = (totalDurationMs * 0.15f).roundToInt()
        val moveEndMs = (totalDurationMs * 0.45f).roundToInt()
        val pauseEndMs = (totalDurationMs * 0.55f).roundToInt()
        val moveBackMs = (totalDurationMs * 0.85f).roundToInt()

        val transition = rememberInfiniteTransition(label = "airmedy-marquee")
        val translationX by transition.animateFloat(
            initialValue = 0f,
            targetValue = targetOffset,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = totalDurationMs
                    0f at 0 using FastOutSlowInEasing
                    0f at pauseStartMs using FastOutSlowInEasing
                    targetOffset at moveEndMs using FastOutSlowInEasing
                    targetOffset at pauseEndMs using FastOutSlowInEasing
                    0f at moveBackMs using FastOutSlowInEasing
                    0f at totalDurationMs
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "airmedy-marquee-translation",
        )

        Text(
            text = text,
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { this.translationX = translationX },
            color = color,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}
