package me.misa198.airmedy.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
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

internal fun fullScreenSwipeExclusionBounds(bounds: Rect, minimumTouchSize: Size): Rect {
    val horizontalInset = ((minimumTouchSize.width - bounds.width) / 2).coerceAtLeast(0f)
    val verticalInset = ((minimumTouchSize.height - bounds.height) / 2).coerceAtLeast(0f)
    return Rect(
        bounds.left - horizontalInset, bounds.top - verticalInset,
        bounds.right + horizontalInset, bounds.bottom + verticalInset,
    )
}

// Scoped to Square Main; controls in other layouts need no registration.
internal class FullScreenSwipeExclusions(private val minimumTouchSize: Size) {
    val controls = mutableMapOf<Any, LayoutCoordinates>()
    fun contains(positionInRoot: Offset): Boolean = controls.values.any {
        it.isAttached && fullScreenSwipeExclusionBounds(it.boundsInRoot(), minimumTouchSize).contains(positionInRoot)
    }
}

private val LocalFullScreenSwipeExclusions = staticCompositionLocalOf<FullScreenSwipeExclusions?> { null }

@Composable
internal fun Modifier.excludeFullScreenSwipe(): Modifier {
    val exclusions = LocalFullScreenSwipeExclusions.current ?: return this
    val key = remember { Any() }
    DisposableEffect(exclusions, key) { onDispose { exclusions.controls.remove(key) } }
    return onGloballyPositioned { exclusions.controls[key] = it }
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
    enabled: Boolean = true,
    exclusions: FullScreenSwipeExclusions? = null,
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
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    DisposableEffect(swipeState, enabled) { onDispose { swipeState.reset() } }
    CompositionLocalProvider(LocalFullScreenSwipeExclusions provides exclusions) {
        Box(
            modifier = modifier.fillMaxWidth()
                .onSizeChanged { targetSize = it }
                .onGloballyPositioned { coordinates = it }
                .semantics { this.testTag = testTag }
                .pointerInput(maximumOffsetPx, thresholdPx, velocityMinimumPx, enabled, exclusions, targetSize) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val rootPosition = coordinates?.takeIf { it.isAttached }?.localToRoot(down.position)
                        if (exclusions != null && (rootPosition == null || exclusions.contains(rootPosition))) {
                            return@awaitEachGesture
                        }
                        swipeState.reset()
                        try {
                            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                                swipeState.isDragging = true
                                swipeState.dragStartedAtMs = android.os.SystemClock.uptimeMillis()
                                swipeState.gestureHorizontalOffset = overSlop
                                swipeState.verticalDragOffset = change.position.y - down.position.y
                                swipeState.dragOffset = overSlop.coerceIn(-maximumOffsetPx, maximumOffsetPx)
                                change.consume()
                            } ?: return@awaitEachGesture
                            val completed = horizontalDrag(start.id) { change ->
                                swipeState.gestureHorizontalOffset += change.position.x - change.previousPosition.x
                                swipeState.verticalDragOffset = change.position.y - down.position.y
                                swipeState.dragOffset = swipeState.gestureHorizontalOffset.coerceIn(-maximumOffsetPx, maximumOffsetPx)
                                change.consume()
                            }
                            val durationMs = (android.os.SystemClock.uptimeMillis() - swipeState.dragStartedAtMs).coerceAtLeast(1L)
                            val direction = swipeState.gestureHorizontalOffset.compareTo(0f)
                            val dispatch = completed && shouldDispatchFullScreenSwipe(
                                swipeState.gestureHorizontalOffset,
                                swipeState.verticalDragOffset,
                                thresholdPx,
                                swipeState.gestureHorizontalOffset / durationMs,
                                FullScreenPlayerSwipeVelocityPxPerMs,
                                velocityMinimumPx,
                            ) && canDispatchQueueSwipe(direction, latestCanNavigatePrevious, latestCanNavigateNext)
                            if (dispatch) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (direction < 0) latestOnNext() else latestOnPrevious()
                            }
                        } finally {
                            swipeState.reset()
                        }
                    }
                },
        ) {
            Box(if (movesContent) Modifier.graphicsLayer { translationX = displayedOffset } else Modifier) { content() }
        }
    }
}

internal fun FullScreenPlayerSwipeState.reset() {
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
