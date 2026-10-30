package me.misa198.airmedy.ui.navigation

import me.misa198.airmedy.lyrics.RomanizationUiState

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import me.misa198.airmedy.BuildConfig
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.LayoutDirection
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
    romanization: RomanizationUiState = RomanizationUiState(),
    romanizationAllowed: Boolean = false,
    lyricsGlowEnabled: Boolean = false,
    onRomanizationInput: (List<String>, Boolean) -> Unit = { _, _ -> },
    onRomanizationToggle: () -> Unit = {},
    artworkCrossfade: ArtworkCrossfadeTransition? = null,
    blendArtworkDuringCrossfade: Boolean = true,
    showQualityBadge: Boolean = true,
    keepScreenOnForLyrics: Boolean = false,
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
    foldingFeatures: List<PlayerFoldingFeature> = emptyList(),
) {
    val item = playbackState.fullScreenItemOrNull() ?: return
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
    var selectedPanel by rememberSaveable { mutableStateOf<FullScreenPlayerPanel?>(null) }
    val view = LocalView.current
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
    var lyricsSeekPositionMs by rememberSaveable(item.trackId) { mutableStateOf<Long?>(null) }
    var lyricsSeekRequestId by rememberSaveable(item.trackId) { mutableLongStateOf(0L) }
    val seekState = rememberPlayerSeekState(item.trackId)
    LaunchedEffect(currentPositionMs, durationMs) {
        val fraction = seekState.pendingFraction.value
        if (seekState.awaitingConfirmation.value && fraction != null && hasConfirmedSeekPosition(fraction, currentPositionMs, durationMs)) {
            seekState.pendingFraction.value = null
            seekState.awaitingConfirmation.value = false
            lyricsSeekPositionMs = null
        }
    }
    val horizontalSwipeState = remember { FullScreenPlayerSwipeState() }
    val displayedHorizontalSwipeOffset by animateFloatAsState(
        targetValue = if (horizontalSwipeState.isDragging) horizontalSwipeState.dragOffset else 0f,
        animationSpec = spring(),
        label = "full-screen-player-swipe",
    )

    val layoutState = rememberSaveableStateHolder()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val safeWidth = maxWidth - with(density) {
            (insets.getLeft(density, LayoutDirection.Ltr) + insets.getRight(density, LayoutDirection.Ltr)).toDp()
        }
        val safeHeight = maxHeight - with(density) {
            (insets.getTop(density) + insets.getBottom(density)).toDp()
        }
        val layoutMode = resolvePlayerLayout(safeWidth.value, safeHeight.value)
        val displayedPanel = selectedPanel ?: FullScreenPlayerPanel.Lyrics.takeIf { layoutMode == PlayerLayoutMode.Wide }
        val canCommitReorder = rememberUpdatedState(
            visible && displayedPanel == FullScreenPlayerPanel.Queue,
        )
        LaunchedEffect(layoutMode, maxWidth, maxHeight) {
            isTrackContextMenuExpanded = false
            isQueueReordering = false
            horizontalSwipeState.reset()
            if (!seekState.awaitingConfirmation.value) seekState.pendingFraction.value = null
            dragOffset.snapTo(0f)
        }
        if (keepScreenOnForLyrics && visible && displayedPanel == FullScreenPlayerPanel.Lyrics) {
            DisposableEffect(view) {
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = false }
            }
        }
        val panelHeightPx = with(density) { maxHeight.toPx() }
        val panelOffsetPx = panelHeightPx * (1f - expansionProgress.value)
        Box(
            modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, (panelOffsetPx + dragOffset.value).roundToInt()) }
            .alpha((expansionProgress.value * 8f).coerceIn(0f, 1f))
            .semantics { contentDescription = playerDescription }
            .pointerInput(closeThresholdPx, layoutMode) {
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
                showArtwork = layoutMode == PlayerLayoutMode.Square && selectedPanel == null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (glassHazeState == null) Modifier else Modifier.hazeSource(glassHazeState)),
            )
            val metadataContent: @Composable (Boolean) -> Unit = { compact ->
                FullScreenPlayerMetadataTransition(
                    item = item,
                    crossfade = artworkCrossfade,
                    displayedHorizontalSwipeOffset = displayedHorizontalSwipeOffset,
                    hazeState = glassHazeState,
                    compact = compact,
                    showMore = layoutMode != PlayerLayoutMode.Square,
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
            val panelContent: @Composable (FullScreenPlayerPanel?, androidx.compose.ui.unit.Dp) -> Unit = { panel, queuePanelWidth ->
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
                        onReorder = { ids -> if (canCommitReorder.value) onQueueReordered(ids) },
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
                    androidx.compose.runtime.key(item.trackId, lyrics) {
                        FullScreenPlayerLyricsPanel(
                            trackId = item.trackId,
                            lyrics = lyrics,
                            loading = lyricsLoading,
                            visible = visible && panel == FullScreenPlayerPanel.Lyrics,
                            romanization = romanization,
                            romanizationAllowed = romanizationAllowed,
                            glowEnabled = lyricsGlowEnabled,
                            onRomanizationInput = onRomanizationInput,
                            onRomanizationToggle = onRomanizationToggle,
                            currentPositionMs = currentPositionMs,
                            isPlaying = isPlaying,
                            pendingSeekPositionMs = lyricsSeekPositionMs,
                            seekRequestId = lyricsSeekRequestId,
                            onSeek = onSeek,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            AdaptivePlayerContent(layoutMode, wide = {
                layoutState.SaveableStateProvider("wide") {
                    WidePlayerLayout(
                        selectedPanel = requireNotNull(displayedPanel),
                        horizontalSwipeState = horizontalSwipeState,
                        displayedHorizontalSwipeOffset = displayedHorizontalSwipeOffset,
                        canNavigatePrevious = canNavigatePrevious,
                        canNavigateNext = canNavigateNext,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        swipeEnabled = visible,
                        artwork = { artworkModifier ->
                            FullScreenPlayerArtwork(
                                artwork = artwork, outgoingArtwork = outgoingArtwork,
                                incomingArtwork = incomingArtwork, crossfadeProgress = crossfadeProgress,
                                isArtworkCrossfading = isArtworkCrossfading, modifier = artworkModifier,
                            )
                        },
                        metadata = { metadataContent(false) },
                        controls = {
                            FullScreenPlayerControls(
                                trackId = item.trackId, seekState = seekState,
                                currentPositionMs = currentPositionMs, durationMs = durationMs,
                                displayedDurationMs = displayedDurationMs,
                                isPreparing = isPreparing, isPlaying = isPlaying,
                                canNavigatePrevious = canNavigatePrevious, canNavigateNext = canNavigateNext,
                                volume = volume,
                                compactControls = usesCompactPlayerControls(safeWidth.value, safeHeight.value),
                                queue = queue, contextTrack = contextTrack, showQualityBadge = showQualityBadge,
                                selectedPanel = displayedPanel, isQueueReordering = false,
                                onPanelSelected = { if (it != null) selectedPanel = it },
                                onSeekRequested = { positionMs ->
                                    lyricsSeekPositionMs = positionMs
                                    lyricsSeekRequestId += 1
                                    onSeek(positionMs)
                                },
                                onSeekConfirmed = { lyricsSeekPositionMs = null },
                                onVolumeChange = onVolumeChange,
                                onPrevious = onPrevious, onPlayPause = onPlayPause, onNext = onNext,
                                onOpenMediaOutputSwitcher = onOpenMediaOutputSwitcher,
                            )
                        },
                        panelContent = { width -> panelContent(displayedPanel, width) },
                    )
                }
            }, square = {
                layoutState.SaveableStateProvider("square") {
                    val minimumTouchSize = with(density) { LocalViewConfiguration.current.minimumTouchTargetSize.toSize() }
                    val swipeExclusions = remember(minimumTouchSize) { FullScreenSwipeExclusions(minimumTouchSize) }
                    FullScreenPlayerSwipeTarget(
                        testTag = "square_player_swipe_target",
                        swipeState = horizontalSwipeState,
                        displayedOffset = displayedHorizontalSwipeOffset,
                        onPrevious = onPrevious, onNext = onNext,
                        canNavigatePrevious = canNavigatePrevious, canNavigateNext = canNavigateNext,
                        enabled = visible && selectedPanel == null,
                        exclusions = swipeExclusions,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        SquarePlayerLayout(
                            selectedPanel = selectedPanel,
                            metadata = { metadataContent(false) },
                            controls = {
                                FullScreenSeekControls(
                                    item.trackId, seekState, currentPositionMs, durationMs, displayedDurationMs,
                                    isPreparing, contextTrack, showQualityBadge,
                                    onSeekRequested = { positionMs ->
                                        lyricsSeekPositionMs = positionMs
                                        lyricsSeekRequestId += 1
                                        onSeek(positionMs)
                                    },
                                    onSeekConfirmed = { lyricsSeekPositionMs = null },
                                )
                                FullScreenPlaybackButtons(isPreparing, isPlaying, canNavigatePrevious, canNavigateNext, onPrevious, onPlayPause, onNext)
                            },
                            panelContent = { width -> panelContent(selectedPanel, width) },
                            footer = {
                                FullScreenPanelButtons(
                                    selectedPanel, queue, { selectedPanel = it },
                                )
                            },
                        )
                    }
                }
            }) {
                layoutState.SaveableStateProvider("phone") {
                    PhonePlayerLayout(
                        isPanelOpen = isPanelOpen,
                        isQueueReordering = isQueueReordering,
                        artworkScale = artworkScale,
                        artworkTransformOrigin = artworkTransformOrigin,
                        horizontalSwipeState = horizontalSwipeState,
                        displayedHorizontalSwipeOffset = displayedHorizontalSwipeOffset,
                        canNavigatePrevious = canNavigatePrevious,
                        canNavigateNext = canNavigateNext,
                        onPrevious = onPrevious, onNext = onNext,
                        onClosePanel = { selectedPanel = null },
                        artwork = { artworkModifier ->
                            FullScreenPlayerArtwork(
                                artwork = artwork, outgoingArtwork = outgoingArtwork,
                                incomingArtwork = incomingArtwork, crossfadeProgress = crossfadeProgress,
                                isArtworkCrossfading = isArtworkCrossfading, modifier = artworkModifier,
                            )
                        },
                        metadata = metadataContent,
                        panelContent = { queuePanelWidth ->
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
                                panelContent(panel, queuePanelWidth)
                            }
                        },
                        controls = { controlsModifier ->
                            FullScreenPlayerControls(
                                seekState = seekState,
                                trackId = item.trackId,
                                currentPositionMs = currentPositionMs,
                                durationMs = durationMs,
                                displayedDurationMs = displayedDurationMs,
                                isPreparing = isPreparing,
                                isPlaying = isPlaying,
                                canNavigatePrevious = canNavigatePrevious,
                                canNavigateNext = canNavigateNext,
                                volume = volume,
                                compactControls = usesCompactPlayerControls(safeWidth.value, safeHeight.value),
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
                                modifier = controlsModifier,
                            )
                        },
                    )
                }
            }
            if (BuildConfig.FLAVOR == "dev") {
                val colors = LocalAirmedyColors.current
                Text(
                    text = layoutMode.name,
                    color = colors.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)
                        .background(colors.playerBackdrop, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("player_layout_mode_badge"),
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
