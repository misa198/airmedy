package me.misa198.airmedy.ui.components

import android.animation.ValueAnimator
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Loops overflowing text with a trailing copy and a pause at the start of each cycle. */
@Composable
fun AirmedyMarqueeText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    key(text) {
        Text(
            text = text,
            modifier = modifier.fillMaxWidth().basicMarquee(
                iterations = if (ValueAnimator.areAnimatorsEnabled()) Int.MAX_VALUE else 0,
                repeatDelayMillis = 3_000,
                initialDelayMillis = 3_000,
                spacing = MarqueeSpacing(32.dp),
                velocity = 20.dp,
            ),
            color = color,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}
