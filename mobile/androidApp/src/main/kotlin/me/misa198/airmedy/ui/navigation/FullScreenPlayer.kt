package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.roundToInt
import me.misa198.airmedy.R
import me.misa198.airmedy.player.ArtworkCrossfadeTransition
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.player.RepeatMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.metadataObject
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
private val FullScreenPlayerCompactArtworkSize = 80.dp
private val FullScreenPlayerCompactGap = 24.dp
// Reserve the player chrome, metadata, and a usable controls area on short screens.
private val FullScreenPlayerArtworkVerticalReserve = 448.dp
@Composable
internal fun FullScreenPlayer(
    visible: Boolean,
    dragProgress: Float,
    isDragging: Boolean,
    openingFromMiniPlayerSwipe: Boolean,
    playbackState: PlaybackState,
    queue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    queueTracks: List<LibraryTrack> = emptyList(),
    lyrics: String? = null,
    lyricsLoading: Boolean = false,
    artworkCrossfade: ArtworkCrossfadeTransition? = null,
    blendArtworkDuringCrossfade: Boolean = true,
    showQualityBadge: Boolean = true,
    volume: Float,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueTrackSelected: (String) -> Unit = {},
    onQueueReordered: (List<String>) -> Unit = {},
    onQueueTrackRemoved: (String) -> Unit = {},
    onShuffleChange: (Boolean) -> Unit = {},
    onRepeatModeChange: (RepeatMode) -> Unit = {},
    isFavorite: Boolean = false,
    onFavoriteToggle: (String, Boolean) -> Unit = { _, _ -> },
    onTrackPlayNext: (String) -> Unit = {},
    onTrackAddToQueue: (String) -> Unit = {},
    moodRadioEligibleTrackIds: Set<String> = emptySet(),
    onStartMoodRadio: (String) -> Unit = {},
    moodRadioActive: Boolean = false,
    onTrackGoToAlbum: (String) -> Unit = {},
    onTrackGoToArtist: (String) -> Unit = {},
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
    onCloseFullscreenThen: ((() -> Unit) -> Unit) = { action -> action() },
    onOpenMediaOutputSwitcher: () -> Unit,
    onDismiss: () -> Unit,
    onDismissAnimationFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val item = playbackState.fullScreenItemOrNull() ?: return
    val colors = LocalAirmedyColors.current
    // The fullscreen panel moves independently from the persistent chrome. Its
    // blur source must therefore be isolated: sharing the shell source lets its
    // dark backdrop be sampled by the mini player while this panel is closing.
    val fullScreenHazeState = rememberHazeState()
    val glassHazeState = fullScreenHazeState.takeIf { hazeState != null }
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val expansionProgress = remember { Animatable(0f) }
    val closeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
    val playerDescription = stringResource(R.string.full_screen_player)
    var hasBeenVisible by remember { mutableStateOf(visible) }

    LaunchedEffect(isDragging, dragProgress, visible, openingFromMiniPlayerSwipe) {
        if (isDragging) {
            expansionProgress.snapTo(dragProgress)
        } else {
            if (visible) {
                hasBeenVisible = true
                dragOffset.snapTo(0f)
            }
            expansionProgress.animateTo(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (visible && openingFromMiniPlayerSwipe) 760 else if (visible) 520 else 400,
                    easing = FastOutSlowInEasing,
                ),
            )
            if (!visible) {
                dragOffset.snapTo(0f)
                if (hasBeenVisible) {
                    hasBeenVisible = false
                    onDismissAnimationFinished()
                }
            }
        }
    }

    if (expansionProgress.value <= 0f) return
    val artwork = rememberFullscreenArtwork(item.artworkPath)
    val activeArtworkCrossfade = artworkCrossfade.takeIf { blendArtworkDuringCrossfade }
    val incomingArtwork = rememberFullscreenArtwork(activeArtworkCrossfade?.toArtworkPath, keepPrevious = false)
    val crossfadeProgress = rememberArtworkCrossfadeProgress(artworkCrossfade)
    // The service announces the visual transition before it promotes the
    // playback item. Freeze the currently visible cover then, so decoding the
    // incoming cover never replaces it with a blank layer or a full-opacity
    // new cover before the blend can render.
    val outgoingArtwork = remember(activeArtworkCrossfade?.id) {
        activeArtworkCrossfade?.let { artwork }
    }
    val isArtworkCrossfading = activeArtworkCrossfade != null &&
        outgoingArtwork != null && incomingArtwork != null
    val contextTrack = queueTracks.firstOrNull { it.id == item.trackId }
    val metadataDurationMs = (contextTrack?.metadataObject()?.get("duration") as? JsonPrimitive)
        ?.contentOrNull
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
        ?.times(1_000L)
    val currentPositionMs = playbackState.positionMsOrZero()
    val durationMs = playbackState.durationMsOrZero()
    val displayedDurationMs = durationMs.takeIf { it > 0L } ?: metadataDurationMs
    val isPreparing = playbackState is PlaybackState.Preparing
    val isPlaying = playbackState is PlaybackState.Playing
    val canNavigatePrevious = queue.canNavigatePrevious()
    val canNavigateNext = queue.canNavigateNext()
    var isTrackContextMenuExpanded by remember(item.trackId) { mutableStateOf(false) }
    var selectedPanel by remember { mutableStateOf<FullScreenPlayerPanel?>(null) }
    val isPanelOpen = selectedPanel != null
    var isQueueReordering by remember { mutableStateOf(false) }
    // The reorderable library reports the end of a normal drag, but a panel
    // switch or fullscreen dismissal can interrupt that gesture first.
    LaunchedEffect(selectedPanel, visible) {
        if (!visible || selectedPanel != FullScreenPlayerPanel.Queue) {
            isQueueReordering = false
        }
    }
    val artworkScale by animateFloatAsState(
        targetValue = if (playbackState is PlaybackState.Paused && !isPanelOpen) 0.75f else 1f,
        animationSpec = tween(if (isPanelOpen) 320 else 500, easing = FastOutSlowInEasing),
        label = "full-screen-artwork-scale",
    )
    val artworkTransformOrigin by animateFloatAsState(
        targetValue = if (isPanelOpen) 0f else 0.5f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "full-screen-artwork-transform-origin",
    )
    var lyricsSeekPositionMs by remember(item.trackId) { mutableStateOf<Long?>(null) }
    var lyricsSeekRequestId by remember(item.trackId) { mutableLongStateOf(0L) }
    val horizontalSwipeState = remember { FullScreenPlayerSwipeState() }
    val displayedHorizontalSwipeOffset by animateFloatAsState(
        targetValue = if (horizontalSwipeState.isDragging) horizontalSwipeState.dragOffset else 0f,
        animationSpec = spring(),
        label = "full-screen-player-swipe",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The player column has 20dp padding on each side. On short screens,
        // cap the square cover by height so the controls retain their space.
        val expandedArtworkSize = minOf(
            maxWidth - 40.dp,
            (maxHeight - FullScreenPlayerArtworkVerticalReserve).coerceAtLeast(0.dp),
        )
        val compactMetadataWidth = maxWidth - 20.dp
        val queuePanelWidth = maxWidth
        val artworkSize by animateDpAsState(
            targetValue = if (isPanelOpen) FullScreenPlayerCompactArtworkSize else expandedArtworkSize,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "full-screen-artwork-size",
        )
        val artworkHorizontalOffset by animateDpAsState(
            targetValue = if (isPanelOpen) 0.dp else ((maxWidth - 40.dp) - expandedArtworkSize) / 2,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "full-screen-artwork-horizontal-offset",
        )
        // Keep the top block's expanded footprint reserved while a panel is open.
        // During a queue reorder, it temporarily grows through the controls area
        // so the list can use the complete safe fullscreen height.
        val restingTopBlockHeight = expandedArtworkSize + 96.dp
        val topBlockHeight by animateDpAsState(
            targetValue = if (isQueueReordering) maxHeight else restingTopBlockHeight,
            animationSpec = tween(QueueReorderTransitionDurationMs, easing = FastOutSlowInEasing),
            label = "full-screen-queue-reorder-height",
        )
        val panelHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        val panelOffsetPx = panelHeightPx * (1f - expansionProgress.value)
        Box(
            modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, (panelOffsetPx + dragOffset.value).roundToInt()) }
            .alpha((expansionProgress.value * 8f).coerceIn(0f, 1f))
            .semantics { contentDescription = playerDescription }
            .pointerInput(closeThresholdPx) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0f || dragOffset.value > 0f) {
                            coroutineScope.launch {
                                dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                            }
                        }
                    },
                    onDragCancel = { coroutineScope.launch { dragOffset.animateTo(0f, spring()) } },
                    onDragEnd = {
                        coroutineScope.launch {
                            if (dragOffset.value >= closeThresholdPx) onDismiss()
                            else dragOffset.animateTo(0f, spring())
                        }
                    },
                )
            },
    ) {
            FullScreenPlayerBackground(
                artwork = artwork,
                outgoingArtwork = outgoingArtwork,
                incomingArtwork = incomingArtwork,
                crossfadeProgress = crossfadeProgress,
                isArtworkCrossfading = isArtworkCrossfading,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (glassHazeState == null) Modifier else Modifier.hazeSource(glassHazeState)),
            )
            Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            ) {
            Spacer(Modifier.height(8.dp))
            FullScreenPlayerDragHandle()
            Spacer(Modifier.height(20.dp))
            FullScreenPlayerSwipeTarget(
                testTag = FullScreenPlayerArtworkSwipeTestTag,
                swipeState = horizontalSwipeState,
                displayedOffset = displayedHorizontalSwipeOffset,
                onPrevious = onPrevious,
                onNext = onNext,
                canNavigatePrevious = canNavigatePrevious,
                canNavigateNext = canNavigateNext,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topBlockHeight),
                ) {
                    FullScreenPlayerArtwork(
                        artwork = artwork,
                        outgoingArtwork = outgoingArtwork,
                        incomingArtwork = incomingArtwork,
                        crossfadeProgress = crossfadeProgress,
                        isArtworkCrossfading = isArtworkCrossfading,
                        Modifier
                            .size(artworkSize)
                            .offset(x = artworkHorizontalOffset)
                            .semantics { testTag = FullScreenPlayerArtworkTestTag }
                            .then(
                                if (isPanelOpen) {
                                    Modifier.clickable(
                                        onClick = { selectedPanel = null },
                                        role = Role.Button,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .graphicsLayer {
                                scaleX = artworkScale
                                scaleY = artworkScale
                                transformOrigin = TransformOrigin(artworkTransformOrigin, artworkTransformOrigin)
                            },
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isPanelOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = expandedArtworkSize + 36.dp),
                        enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { 10 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -10 },
                    ) {
                        FullScreenPlayerMetadataTransition(
                            item = item,
                            crossfade = artworkCrossfade,
                            displayedHorizontalSwipeOffset = displayedHorizontalSwipeOffset,
                            hazeState = glassHazeState,
                            compact = false,
                            isFavorite = isFavorite,
                            onFavoriteToggle = onFavoriteToggle,
                            contextTrack = contextTrack,
                            contextMenuExpanded = isTrackContextMenuExpanded,
                            onContextMenuOpen = { isTrackContextMenuExpanded = true },
                            onContextMenuDismiss = { isTrackContextMenuExpanded = false },
                            playbackQueue = queue,
                            onTrackPlayNext = onTrackPlayNext,
                            onTrackAddToQueue = onTrackAddToQueue,
                            moodRadioEligibleTrackIds = moodRadioEligibleTrackIds,
                            onStartMoodRadio = onStartMoodRadio,
                            onTrackGoToAlbum = onTrackGoToAlbum,
                            onTrackGoToArtist = onTrackGoToArtist,
                            onTrackContextBottomSheet = onTrackContextBottomSheet,
                            onCloseFullscreenThen = onCloseFullscreenThen,
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPanelOpen,
                        modifier = Modifier
                            // Extend only the compact metadata into the outer
                            // column's right inset, leaving it flush to screen edge.
                            .requiredWidth(compactMetadataWidth)
                            .padding(start = FullScreenPlayerCompactArtworkSize + FullScreenPlayerCompactGap)
                            .height(FullScreenPlayerCompactArtworkSize),
                        enter = fadeIn(tween(200, delayMillis = 120)) + slideInVertically(tween(200, delayMillis = 120)) { 12 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -10 },
                    ) {
                        FullScreenPlayerMetadataTransition(
                            item = item,
                            crossfade = artworkCrossfade,
                            displayedHorizontalSwipeOffset = displayedHorizontalSwipeOffset,
                            hazeState = glassHazeState,
                            compact = true,
                            isFavorite = isFavorite,
                            onFavoriteToggle = onFavoriteToggle,
                            contextTrack = contextTrack,
                            contextMenuExpanded = isTrackContextMenuExpanded,
                            onContextMenuOpen = { isTrackContextMenuExpanded = true },
                            onContextMenuDismiss = { isTrackContextMenuExpanded = false },
                            playbackQueue = queue,
                            onTrackPlayNext = onTrackPlayNext,
                            onTrackAddToQueue = onTrackAddToQueue,
                            moodRadioEligibleTrackIds = moodRadioEligibleTrackIds,
                            onStartMoodRadio = onStartMoodRadio,
                            onTrackGoToAlbum = onTrackGoToAlbum,
                            onTrackGoToArtist = onTrackGoToArtist,
                            onTrackContextBottomSheet = onTrackContextBottomSheet,
                            onCloseFullscreenThen = onCloseFullscreenThen,
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPanelOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = FullScreenPlayerCompactArtworkSize + 8.dp),
                        enter = fadeIn(tween(200, delayMillis = 120)) + slideInVertically(tween(200, delayMillis = 120)) { 12 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -10 },
                    ) {
                        androidx.compose.animation.AnimatedContent(
                            targetState = selectedPanel,
                            transitionSpec = {
                                (fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                                    slideInVertically(tween(200, easing = FastOutSlowInEasing)) { 12 }) togetherWith
                                    (fadeOut(tween(120, easing = FastOutSlowInEasing)) +
                                        slideOutVertically(tween(120, easing = FastOutSlowInEasing)) { -10 })
                            },
                            label = "full-screen-player-panel-content",
                        ) { panel ->
                            if (panel == FullScreenPlayerPanel.Queue) {
                                FullScreenQueuePanel(
                                    queue = queue,
                                    moodRadioActive = moodRadioActive,
                                    tracks = queueTracks,
                                    currentTrackId = item.trackId,
                                    isPlaying = isPlaying,
                                    onTrackSelected = onQueueTrackSelected,
                                    onTrackRemoved = onQueueTrackRemoved,
                                    onTrackPlayNext = onTrackPlayNext,
                                    onReorder = onQueueReordered,
                                    onReorderDragStateChange = { isQueueReordering = it },
                                    onShuffleChange = onShuffleChange,
                                    onRepeatModeChange = onRepeatModeChange,
                                    onFavoriteChange = onFavoriteToggle,
                                    onTrackGoToAlbum = onTrackGoToAlbum,
                                    onTrackGoToArtist = onTrackGoToArtist,
                                    onTrackContextBottomSheet = onTrackContextBottomSheet,
                                    onCloseFullscreenThen = onCloseFullscreenThen,
                                    hazeState = glassHazeState,
                                    // Queue owns its row insets so its list and header can
                                    // reach both screen edges instead of inheriting the player
                                    // column's horizontal padding.
                                    modifier = Modifier
                                        .requiredWidth(queuePanelWidth)
                                        .fillMaxSize(),
                                )
                            } else {
                                FullScreenPlayerLyricsPanel(
                                    trackId = item.trackId,
                                    lyrics = lyrics,
                                    loading = lyricsLoading,
                                    currentPositionMs = currentPositionMs,
                                    pendingSeekPositionMs = lyricsSeekPositionMs,
                                    seekRequestId = lyricsSeekRequestId,
                                    onSeek = onSeek,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
            FullScreenPlayerControls(
                trackId = item.trackId,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                displayedDurationMs = displayedDurationMs,
                isPreparing = isPreparing,
                isPlaying = isPlaying,
                canNavigatePrevious = canNavigatePrevious,
                canNavigateNext = canNavigateNext,
                volume = volume,
                queue = queue,
                contextTrack = contextTrack,
                showQualityBadge = showQualityBadge,
                selectedPanel = selectedPanel,
                isQueueReordering = isQueueReordering,
                onPanelSelected = { selectedPanel = it },
                onSeekRequested = { positionMs ->
                    lyricsSeekPositionMs = positionMs
                    lyricsSeekRequestId += 1
                    onSeek(positionMs)
                },
                onSeekConfirmed = { lyricsSeekPositionMs = null },
                onVolumeChange = onVolumeChange,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onOpenMediaOutputSwitcher = onOpenMediaOutputSwitcher,
                modifier = Modifier.weight(1f),
            )
            }
        }
    }
}

private fun PlaybackState.fullScreenItemOrNull(): PlaybackItem? = when (this) {
    PlaybackState.Idle, is PlaybackState.Failed -> null
    is PlaybackState.Preparing -> item
    is PlaybackState.Playing -> item
    is PlaybackState.Paused -> item
}

private fun PlaybackState.positionMsOrZero(): Long = when (this) {
    is PlaybackState.Playing -> positionMs
    is PlaybackState.Paused -> positionMs
    else -> 0L
}

private fun PlaybackState.durationMsOrZero(): Long = when (this) {
    is PlaybackState.Playing -> durationMs
    is PlaybackState.Paused -> durationMs
    else -> 0L
}
