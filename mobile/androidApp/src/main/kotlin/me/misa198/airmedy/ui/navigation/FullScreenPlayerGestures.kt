package me.misa198.airmedy.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private val FullScreenPlayerDragHandleShape = RoundedCornerShape(2.dp)
private const val FullScreenPlayerDragHandleTestTag = "full_screen_player_drag_handle"
internal const val FullScreenPlayerArtworkSwipeTestTag = "full_screen_player_artwork_swipe_target"
internal const val FullScreenPlayerArtworkTestTag = "full_screen_player_artwork"
private val FullScreenPlayerSwipeMaximum = 64.dp
private val FullScreenPlayerSwipeThreshold = 52.dp
private val FullScreenPlayerSwipeVelocityMinimum = 28.dp
private const val FullScreenPlayerSwipeVelocityPxPerMs = 1.2f

internal class FullScreenPlayerSwipeState {
    var dragOffset by mutableStateOf(0f)
    var gestureHorizontalOffset by mutableStateOf(0f)
    var verticalDragOffset by mutableStateOf(0f)
    var isDragging by mutableStateOf(false)
    var dragStartedAtMs by mutableStateOf(0L)
}

/** Requires a deliberate, predominantly horizontal gesture before changing tracks. */
internal fun shouldDispatchFullScreenSwipe(
    horizontalDistancePx: Float,
    verticalDistancePx: Float,
    thresholdPx: Float,
    velocityPxPerMs: Float,
    velocityThresholdPxPerMs: Float,
    velocityMinimumPx: Float,
): Boolean {
    val horizontal = horizontalDistancePx.absoluteValue
    val vertical = verticalDistancePx.absoluteValue
    if (horizontal < vertical * 1.25f) return false
    return horizontal >= thresholdPx ||
        (horizontal >= velocityMinimumPx && velocityPxPerMs.absoluteValue >= velocityThresholdPxPerMs)
}

@Composable
internal fun FullScreenPlayerSwipeTarget(
    testTag: String,
    swipeState: FullScreenPlayerSwipeState,
    displayedOffset: Float,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    modifier: Modifier = Modifier,
    movesContent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val latestOnPrevious by rememberUpdatedState(onPrevious)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestCanNavigatePrevious by rememberUpdatedState(canNavigatePrevious)
    val latestCanNavigateNext by rememberUpdatedState(canNavigateNext)
    val maximumOffsetPx = with(density) { FullScreenPlayerSwipeMaximum.toPx() }
    val thresholdPx = with(density) { FullScreenPlayerSwipeThreshold.toPx() }
    val velocityMinimumPx = with(density) { FullScreenPlayerSwipeVelocityMinimum.toPx() }
    Box(
        modifier = modifier.fillMaxWidth().semantics { this.testTag = testTag }
            .pointerInput(maximumOffsetPx, thresholdPx, velocityMinimumPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        swipeState.dragOffset = 0f
                        swipeState.gestureHorizontalOffset = 0f
                        swipeState.verticalDragOffset = 0f
                        swipeState.dragStartedAtMs = android.os.SystemClock.uptimeMillis()
                        swipeState.isDragging = true
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        swipeState.gestureHorizontalOffset += dragAmount
                        swipeState.dragOffset = swipeState.gestureHorizontalOffset.coerceIn(-maximumOffsetPx, maximumOffsetPx)
                        change.consume()
                    },
                    onDragCancel = { swipeState.reset() },
                    onDragEnd = {
                        val durationMs = (android.os.SystemClock.uptimeMillis() - swipeState.dragStartedAtMs).coerceAtLeast(1L)
                        val direction = swipeState.gestureHorizontalOffset.compareTo(0f)
                        val dispatch = shouldDispatchFullScreenSwipe(
                            swipeState.gestureHorizontalOffset,
                            swipeState.verticalDragOffset,
                            thresholdPx,
                            swipeState.gestureHorizontalOffset / durationMs,
                            FullScreenPlayerSwipeVelocityPxPerMs,
                            velocityMinimumPx,
                        ) && canDispatchQueueSwipe(direction, latestCanNavigatePrevious, latestCanNavigateNext)
                        swipeState.reset()
                        if (dispatch) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (direction < 0) latestOnNext() else latestOnPrevious()
                        }
                    },
                )
            },
    ) {
        Box(if (movesContent) Modifier.graphicsLayer { translationX = displayedOffset } else Modifier) { content() }
    }
}

private fun FullScreenPlayerSwipeState.reset() {
    isDragging = false
    dragOffset = 0f
    gestureHorizontalOffset = 0f
    verticalDragOffset = 0f
}

@Composable
internal fun FullScreenPlayerDragHandle() {
    val colors = LocalAirmedyColors.current
    Box(Modifier.fillMaxWidth().height(4.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.width(48.dp).height(4.dp).semantics { testTag = FullScreenPlayerDragHandleTestTag }
                .clip(FullScreenPlayerDragHandleShape).background(colors.foregroundSubtle),
        )
    }
}
