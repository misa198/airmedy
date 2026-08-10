package me.misa198.airmedy.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.AirmedyMarqueeText
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.ui.components.liquidGlassBackground
import me.misa198.airmedy.ui.components.rememberArtworkThumbnail
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import kotlin.math.absoluteValue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal val MiniPlayerHeight = 56.dp
internal val MiniPlayerNavigationGap = 8.dp
private val MiniPlayerPillRadius = 30.dp
private val MetadataSwipeMaximum = 40.dp
private val MetadataSwipeThreshold = 32.dp
private const val MetadataSwipeVelocityPxPerMs = 1.2f

@Composable
internal fun MiniPlayer(
    playbackState: PlaybackState,
    hazeState: HazeState?,
    compact: Boolean = false,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFullScreenPlayer: () -> Unit,
    onFullScreenPlayerDrag: (Float) -> Unit,
    onFullScreenPlayerDragEnd: (Boolean) -> Unit,
    stableGlassWidth: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val item = playbackState.itemOrNull() ?: return
    val colors = LocalAirmedyColors.current
    val shape = RoundedCornerShape(MiniPlayerPillRadius)
    val isPlaying = playbackState is PlaybackState.Playing
    val isPreparing = playbackState is PlaybackState.Preparing
    val artwork = rememberArtworkThumbnail(item.artworkPath)
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val dragOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val dismissThresholdPx = with(density) { 36.dp.toPx() }
    val fullScreenTransitionDistancePx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val fullScreenCatchUpTravelPx = with(density) { 120.dp.toPx() }
    val maximumOpenPullPx = with(density) { MiniPlayerHeight.toPx() }
    val dismissTargetPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val metadataSwipeMaximumPx = with(density) { MetadataSwipeMaximum.toPx() }
    val metadataSwipeThresholdPx = with(density) { MetadataSwipeThreshold.toPx() }
    var metadataDragOffset by remember { mutableStateOf(0f) }
    var isMetadataDragging by remember { mutableStateOf(false) }
    var metadataDragStartedAtMs by remember { mutableStateOf(0L) }
    val displayedMetadataDragOffset by animateFloatAsState(
        targetValue = if (isMetadataDragging) metadataDragOffset else 0f,
        animationSpec = spring(),
        label = "mini-player-metadata-swipe",
    )
    val displayedDragOffset = dragOffset.value
    val upwardPullProgress = (-displayedDragOffset / maximumOpenPullPx).coerceIn(0f, 1f)
    val miniPlayerAlpha = 1f - upwardPullProgress
    var fullScreenPullPx by remember { mutableStateOf(0f) }
    var miniPlayerTopPx by remember { mutableStateOf(0f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .onGloballyPositioned { miniPlayerTopPx = it.positionInRoot().y }
            .pointerInput(
                dismissThresholdPx,
                fullScreenTransitionDistancePx,
                fullScreenCatchUpTravelPx,
                maximumOpenPullPx,
                dismissTargetPx,
                miniPlayerTopPx,
            ) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // A cancelled gesture may skip its terminal branch. A new pointer
                    // must always start from the mini-player resting state.
                    fullScreenPullPx = 0f
                    var currentDragOffset = 0f
                    coroutineScope.launch { dragOffset.snapTo(0f) }
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var isVerticalDrag = false
                    var isHorizontalDrag = false
                    var isOpeningFullscreen = false
                    var fullScreenCatchUpPx = 0f
                    var wasCancelled = false
                    var change = down

                    while (change.pressed) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) {
                            wasCancelled = true
                            break
                        }

                        val delta = change.positionChange()
                        totalDragX += delta.x
                        totalDragY += delta.y
                        if (!isVerticalDrag && !isHorizontalDrag) {
                            when {
                                abs(totalDragY) > viewConfiguration.touchSlop &&
                                    abs(totalDragY) > abs(totalDragX) -> {
                                    isVerticalDrag = true
                                    isOpeningFullscreen = totalDragY < 0f
                                    if (isOpeningFullscreen) {
                                        val fingerStartY = miniPlayerTopPx + down.position.y
                                        fullScreenCatchUpPx = (fullScreenTransitionDistancePx - fingerStartY)
                                            .coerceAtLeast(0f)
                                    }
                                    currentDragOffset = 0f
                                    coroutineScope.launch { dragOffset.snapTo(0f) }
                                }
                                abs(totalDragX) > viewConfiguration.touchSlop -> {
                                    isHorizontalDrag = true
                                }
                            }
                        }

                        if (isVerticalDrag) {
                            change.consume()
                            if (isOpeningFullscreen) {
                                fullScreenPullPx = (fullScreenPullPx - delta.y)
                                    .coerceIn(0f, fullScreenTransitionDistancePx)
                                currentDragOffset = -fullScreenPullPx.coerceAtMost(maximumOpenPullPx)
                                val targetDragOffset = currentDragOffset
                                coroutineScope.launch { dragOffset.snapTo(targetDragOffset) }
                                val catchUpProgress = (
                                    fullScreenPullPx / fullScreenCatchUpTravelPx
                                ).coerceIn(0f, 1f)
                                val easedCatchUpProgress = 1f -
                                    (1f - catchUpProgress) * (1f - catchUpProgress)
                                val panelDistancePx = fullScreenPullPx +
                                    fullScreenCatchUpPx * easedCatchUpProgress
                                onFullScreenPlayerDrag(
                                    (panelDistancePx / fullScreenTransitionDistancePx)
                                        .coerceIn(0f, 1f),
                                )
                            } else {
                                // A downward dismissal pull must remain reversible.  Keeping
                                // the gesture's initial direction locked avoids turning a
                                // reversal into a fullscreen open, while allowing the user to
                                // pull the pill back to its resting position before release.
                                currentDragOffset = (currentDragOffset + delta.y)
                                    .coerceIn(0f, dismissTargetPx)
                                val targetDragOffset = currentDragOffset
                                coroutineScope.launch { dragOffset.snapTo(targetDragOffset) }
                            }
                        }
                    }

                    when {
                        isVerticalDrag && wasCancelled -> {
                            onFullScreenPlayerDragEnd(false)
                            fullScreenPullPx = 0f
                            coroutineScope.launch {
                                dragOffset.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                            }
                        }
                        isVerticalDrag -> {
                            when {
                                // An upward gesture can be reversed before release. Do not
                                // complete the fullscreen transition when only a sub-slop
                                // upward remainder is left after the reversal.
                                fullScreenPullPx > viewConfiguration.touchSlop -> {
                                    onFullScreenPlayerDragEnd(true)
                                    fullScreenPullPx = 0f
                                    coroutineScope.launch { dragOffset.snapTo(0f) }
                                }
                                currentDragOffset >= dismissThresholdPx -> {
                                    coroutineScope.launch {
                                        dragOffset.animateTo(
                                            dismissTargetPx,
                                            tween(180, easing = FastOutSlowInEasing),
                                        )
                                        onDismiss()
                                    }
                                }
                                else -> {
                                    onFullScreenPlayerDragEnd(false)
                                    fullScreenPullPx = 0f
                                    coroutineScope.launch {
                                        dragOffset.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                                    }
                                }
                            }
                        }
                        !wasCancelled && !isHorizontalDrag && !change.pressed &&
                            abs(totalDragX) <= viewConfiguration.touchSlop &&
                            abs(totalDragY) <= viewConfiguration.touchSlop -> onOpenFullScreenPlayer()
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                onClick { onOpenFullScreenPlayer(); true }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, displayedDragOffset.roundToInt()) }
                .alpha(miniPlayerAlpha)
                .clip(shape)
                .border(1.dp, colors.borderGlass, shape),
        ) {
            Box(
            modifier = if (stableGlassWidth == null) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .align(Alignment.CenterStart)
                    .requiredWidth(stableGlassWidth)
                    .fillMaxHeight()
            }
                .liquidGlassBackground(hazeState, colors),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.glassElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MaterialSymbol(
                    symbol = MaterialSymbols.MusicNote,
                    contentDescription = null,
                    tint = colors.textMuted,
                    size = 22.dp,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(metadataSwipeMaximumPx, metadataSwipeThresholdPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            metadataDragOffset = displayedMetadataDragOffset
                            metadataDragStartedAtMs = android.os.SystemClock.uptimeMillis()
                            isMetadataDragging = true
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            metadataDragOffset = (metadataDragOffset + dragAmount)
                                .coerceIn(-metadataSwipeMaximumPx, metadataSwipeMaximumPx)
                        },
                        onDragCancel = {
                            isMetadataDragging = false
                        },
                        onDragEnd = {
                            val durationMs = (android.os.SystemClock.uptimeMillis() - metadataDragStartedAtMs)
                                .coerceAtLeast(1L)
                            val velocityPxPerMs = metadataDragOffset / durationMs
                            val shouldChangeTrack = metadataDragOffset.absoluteValue >= metadataSwipeThresholdPx ||
                                velocityPxPerMs.absoluteValue >= MetadataSwipeVelocityPxPerMs
                            val swipeDirection = metadataDragOffset.compareTo(0f)
                            isMetadataDragging = false
                            metadataDragOffset = 0f

                            if (shouldChangeTrack && swipeDirection != 0) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (swipeDirection < 0) onNextClick() else onPreviousClick()
                            }
                        },
                    )
                },
            ) {
                Column(
                    modifier = Modifier.offset { IntOffset(displayedMetadataDragOffset.roundToInt(), 0) },
                ) {
                    AirmedyMarqueeText(
                        item.title,
                        colors.textMain,
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    AirmedyMarqueeText(item.artist, colors.textMuted, MaterialTheme.typography.bodySmall)
                }
            }
        }
        AnimatedVisibility(
            visible = !compact,
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(160)),
        ) {
            MiniPlayerControl(
                symbol = MaterialSymbols.SkipPrevious,
                label = stringResource(R.string.player_previous),
                onClick = onPreviousClick,
                horizontalOffset = 16.dp,
            )
        }
        MiniPlayerControl(
            symbol = if (isPlaying) MaterialSymbols.Pause else MaterialSymbols.PlayArrow,
            label = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
            onClick = onPlayPauseClick,
            enabled = !isPreparing,
            horizontalOffset = 8.dp,
        )
        MiniPlayerControl(
            symbol = MaterialSymbols.SkipNext,
            label = stringResource(R.string.player_next),
            onClick = onNextClick,
        )
        }
        }
    }
}

@Composable
private fun MiniPlayerControl(
    symbol: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    horizontalOffset: Dp = 0.dp,
) {
    val colors = LocalAirmedyColors.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .offset(x = horizontalOffset)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol(
            symbol = symbol,
            contentDescription = label,
            tint = if (enabled) colors.textMain else colors.textMuted,
            size = 24.dp,
            filled = true,
        )
    }
}

private fun PlaybackState.itemOrNull(): PlaybackItem? = when (this) {
    PlaybackState.Idle, is PlaybackState.Failed -> null
    is PlaybackState.Preparing -> item
    is PlaybackState.Playing -> item
    is PlaybackState.Paused -> item
}
