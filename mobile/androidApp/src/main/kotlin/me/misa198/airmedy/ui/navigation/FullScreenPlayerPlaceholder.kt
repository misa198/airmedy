package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun FullScreenPlayerPlaceholder(
    visible: Boolean,
    dragProgress: Float,
    isDragging: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragOffset = remember { Animatable(0f) }
    val expansionProgress = remember { Animatable(0f) }
    val closeThresholdPx = with(density) { 96.dp.toPx() }
    val contentDescription = stringResource(R.string.full_screen_player_placeholder)

    LaunchedEffect(isDragging, dragProgress, visible) {
        if (isDragging) {
            expansionProgress.snapTo(dragProgress)
        } else {
            if (visible) {
                // A closing drag may still be settling when the player is opened
                // again. Never carry that old panel offset into the next open.
                dragOffset.snapTo(0f)
            }
            expansionProgress.animateTo(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (visible) 520 else 400,
                    easing = FastOutSlowInEasing,
                ),
            )
            if (!visible) {
                delay(400)
                dragOffset.snapTo(0f)
            }
        }
    }

    if (expansionProgress.value > 0f) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
        ) {
            val panelHeightPx = with(density) { maxHeight.toPx() }
            val panelOffsetPx = panelHeightPx * (1f - expansionProgress.value)
            val panelAlpha = (expansionProgress.value * 8f).coerceIn(0f, 1f)
            Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (panelOffsetPx + dragOffset.value).roundToInt()) }
                .alpha(panelAlpha)
                .background(colors.background)
                .semantics { this.contentDescription = contentDescription }
                .pointerInput(closeThresholdPx) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffset.animateTo(0f, spring())
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (dragOffset.value >= closeThresholdPx) {
                                    onDismiss()
                                } else {
                                    dragOffset.animateTo(0f, spring())
                                }
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contentDescription,
                color = colors.textMain,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        }
    }
}
