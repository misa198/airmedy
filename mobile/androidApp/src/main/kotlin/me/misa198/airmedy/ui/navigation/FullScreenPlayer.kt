package me.misa198.airmedy.ui.navigation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.absoluteValue
import kotlin.math.abs
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.ArtworkCrossfadeTransition
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.player.RepeatMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.AirmedyIconButton
import me.misa198.airmedy.ui.components.AnimatedPlayPauseSymbol
import me.misa198.airmedy.ui.components.AnimatedSkipSymbol
import me.misa198.airmedy.ui.components.AirmedyIconButtonVariant
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.AirmedyMarqueeText
import me.misa198.airmedy.ui.components.AirmedyTrackSlider
import me.misa198.airmedy.ui.components.rememberArtworkThumbnail
import me.misa198.airmedy.ui.components.sliderFilledTrackColor
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private val FullScreenArtworkShape = RoundedCornerShape(16.dp)
private val FullScreenPlayerDragHandleShape = RoundedCornerShape(2.dp)
private const val FullScreenPlayerDragHandleTestTag = "full_screen_player_drag_handle"
private const val FullScreenPlayerArtworkSwipeTestTag = "full_screen_player_artwork_swipe_target"
private const val FullScreenPlayerArtworkTestTag = "full_screen_player_artwork"
private const val FullScreenPlayerMetadataSwipeTestTag = "full_screen_player_metadata_swipe_target"
private val FullScreenPlayerSwipeMaximum = 40.dp
private val FullScreenPlayerSwipeThreshold = 32.dp
private const val FullScreenPlayerSwipeVelocityPxPerMs = 1.2f
private val FullScreenPlayerCompactArtworkSize = 80.dp
private val FullScreenPlayerCompactGap = 24.dp
private const val SeekConfirmationToleranceMs = 250L

/**
 * The service publishes playback position asynchronously after a seek. Retain
 * the locally selected position until that publication reaches the target so
 * the slider never briefly redraws at the old playback position.
 */
internal fun hasConfirmedSeekPosition(
    seekFraction: Float,
    playbackPositionMs: Long,
    durationMs: Long,
): Boolean {
    if (durationMs <= 0L) return false
    val targetPositionMs = (durationMs * seekFraction.coerceIn(0f, 1f)).toLong()
    return abs(playbackPositionMs - targetPositionMs) <= SeekConfirmationToleranceMs
}

private enum class FullScreenPlayerPanel {
    Lyrics,
    Queue,
}

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
    artworkCrossfade: ArtworkCrossfadeTransition? = null,
    blendArtworkDuringCrossfade: Boolean = true,
    volume: Float,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueTrackSelected: (String) -> Unit = {},
    onQueueReordered: (List<String>) -> Unit = {},
    onShuffleChange: (Boolean) -> Unit = {},
    onRepeatModeChange: (RepeatMode) -> Unit = {},
    onOpenMediaOutputSwitcher: () -> Unit,
    onDismiss: () -> Unit,
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
    val seekLabel = stringResource(R.string.player_seek)
    val volumeLabel = stringResource(R.string.player_volume)

    LaunchedEffect(isDragging, dragProgress, visible, openingFromMiniPlayerSwipe) {
        if (isDragging) {
            expansionProgress.snapTo(dragProgress)
        } else {
            if (visible) dragOffset.snapTo(0f)
            expansionProgress.animateTo(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (visible && openingFromMiniPlayerSwipe) 760 else if (visible) 520 else 400,
                    easing = FastOutSlowInEasing,
                ),
            )
            if (!visible) dragOffset.snapTo(0f)
        }
    }

    if (expansionProgress.value <= 0f) return
    val artwork = rememberFullscreenArtwork(item.artworkPath)
    val activeArtworkCrossfade = artworkCrossfade.takeIf { blendArtworkDuringCrossfade }
    val incomingArtwork = rememberFullscreenArtwork(activeArtworkCrossfade?.toArtworkPath, keepPrevious = false)
    val crossfadeProgress = rememberArtworkCrossfadeProgress(artworkCrossfade)
    // Freeze the cover already on screen at transition start. Never render a
    // placeholder layer during a crossfade: slow/background decode races can
    // otherwise surface a one-frame flash before the incoming bitmap arrives.
    val outgoingArtwork = remember(activeArtworkCrossfade?.id) {
        activeArtworkCrossfade?.let { artwork }
    }
    val isArtworkCrossfading = activeArtworkCrossfade != null &&
        outgoingArtwork != null && incomingArtwork != null
    val currentPositionMs = playbackState.positionMsOrZero()
    val durationMs = playbackState.durationMsOrZero()
    val isPreparing = playbackState is PlaybackState.Preparing
    val isPlaying = playbackState is PlaybackState.Playing
    val canNavigatePrevious = queue.canNavigatePrevious()
    val canNavigateNext = queue.canNavigateNext()
    var selectedPanel by remember { mutableStateOf<FullScreenPlayerPanel?>(null) }
    val isPanelOpen = selectedPanel != null
    val lyricsButtonBackground by animateColorAsState(
        targetValue = if (selectedPanel == FullScreenPlayerPanel.Lyrics) {
            colors.foregroundSubtle
        } else {
            colors.foregroundSubtle.copy(alpha = 0f)
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "full-screen-lyrics-button-background",
    )
    val queueButtonBackground by animateColorAsState(
        targetValue = if (selectedPanel == FullScreenPlayerPanel.Queue) {
            colors.foregroundSubtle
        } else {
            colors.foregroundSubtle.copy(alpha = 0f)
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "full-screen-queue-button-background",
    )
    val lyricsButtonIconColor by animateColorAsState(
        targetValue = if (selectedPanel == FullScreenPlayerPanel.Lyrics) {
            colors.playerBackdrop.copy(alpha = 0.72f)
        } else {
            colors.foregroundSubtle
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "full-screen-lyrics-button-icon",
    )
    val queueButtonIconColor by animateColorAsState(
        targetValue = if (selectedPanel == FullScreenPlayerPanel.Queue) {
            colors.playerBackdrop.copy(alpha = 0.72f)
        } else {
            colors.foregroundSubtle
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "full-screen-queue-button-icon",
    )
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
    var pendingSeekFraction by remember(item.trackId) { mutableStateOf<Float?>(null) }
    var isAwaitingSeekConfirmation by remember(item.trackId) { mutableStateOf(false) }
    var lyricsSeekPositionMs by remember(item.trackId) { mutableStateOf<Long?>(null) }
    var lyricsSeekRequestId by remember(item.trackId) { mutableLongStateOf(0L) }
    var isSeekSliderInteracting by remember { mutableStateOf(false) }
    var isVolumeSliderInteracting by remember { mutableStateOf(false) }
    val seekSupportingOffset by animateDpAsState(
        targetValue = if (isSeekSliderInteracting) 4.dp else 0.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "full-screen-seek-supporting-offset",
    )
    val seekTimeLabelColor by animateColorAsState(
        targetValue = sliderFilledTrackColor(colors, isSeekSliderInteracting),
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "full-screen-seek-time-label-colour",
    )
    val volumeIconOffset by animateDpAsState(
        targetValue = if (isVolumeSliderInteracting) 4.dp else 0.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "full-screen-volume-icon-offset",
    )
    val volumeIconColor by animateColorAsState(
        targetValue = sliderFilledTrackColor(colors, isVolumeSliderInteracting),
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "full-screen-volume-icon-colour",
    )
    LaunchedEffect(currentPositionMs, durationMs, isAwaitingSeekConfirmation) {
        val seekFraction = pendingSeekFraction
        if (
            isAwaitingSeekConfirmation &&
            seekFraction != null &&
            hasConfirmedSeekPosition(seekFraction, currentPositionMs, durationMs)
        ) {
            pendingSeekFraction = null
            isAwaitingSeekConfirmation = false
            lyricsSeekPositionMs = null
        }
    }
    val horizontalSwipeState = remember { FullScreenPlayerSwipeState() }
    val displayedHorizontalSwipeOffset by animateFloatAsState(
        targetValue = if (horizontalSwipeState.isDragging) horizontalSwipeState.dragOffset else 0f,
        animationSpec = spring(),
        label = "full-screen-player-swipe",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The player column has 20dp padding on each side, so the full-screen
        // artwork must use its content width rather than this outer constraint.
        val expandedArtworkSize = maxWidth - 40.dp
        val compactMetadataWidth = maxWidth - 20.dp
        val artworkSize by animateDpAsState(
            targetValue = if (isPanelOpen) FullScreenPlayerCompactArtworkSize else expandedArtworkSize,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "full-screen-artwork-size",
        )
        // Keep the top block's expanded footprint reserved while a panel is open.
        // Otherwise the player controls below would climb into the newly freed space.
        val topBlockHeight = expandedArtworkSize + 96.dp
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
                    Artwork(
                        artwork = artwork,
                        outgoingArtwork = outgoingArtwork,
                        incomingArtwork = incomingArtwork,
                        crossfadeProgress = crossfadeProgress,
                        isArtworkCrossfading = isArtworkCrossfading,
                        Modifier
                            .size(artworkSize)
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
                                    tracks = queueTracks,
                                    currentTrackId = item.trackId,
                                    isPlaying = isPlaying,
                                    onTrackSelected = onQueueTrackSelected,
                                    onReorder = onQueueReordered,
                                    onShuffleChange = onShuffleChange,
                                    onRepeatModeChange = onRepeatModeChange,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                FullScreenPlayerLyricsPanel(
                                    trackId = item.trackId,
                                    lyrics = lyrics,
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column {
                    AirmedyTrackSlider(
                        value = pendingSeekFraction ?: if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f,
                        onValueChange = { pendingSeekFraction = it },
                        onValueChangeFinished = {
                            pendingSeekFraction?.let { fraction ->
                                val targetPositionMs = (durationMs * fraction).toLong()
                                lyricsSeekPositionMs = targetPositionMs
                                lyricsSeekRequestId += 1
                                onSeek(targetPositionMs)
                            }
                            isAwaitingSeekConfirmation = pendingSeekFraction != null
                        },
                        enabled = durationMs > 0 && !isPreparing,
                        onInteractionChange = { isSeekSliderInteracting = it },
                        trackHeight = 6.dp,
                        hazeState = glassHazeState,
                        modifier = Modifier.semantics { contentDescription = seekLabel },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            formatPlaybackTime(
                                pendingSeekFraction?.let { (durationMs * it).toLong() } ?: currentPositionMs,
                            ),
                            color = seekTimeLabelColor,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.offset(x = -seekSupportingOffset, y = (-8).dp + seekSupportingOffset),
                        )
                        Text(
                            formatPlaybackTime(durationMs),
                            color = seekTimeLabelColor,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.offset(x = seekSupportingOffset, y = (-8).dp + seekSupportingOffset),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FullScreenTransportButton(
                        symbol = MaterialSymbols.SkipPrevious,
                        label = stringResource(R.string.player_previous),
                        onClick = onPrevious,
                        iconSize = 36.dp,
                        skipForward = false,
                        enabled = canNavigatePrevious,
                    )
                    FullScreenTransportButton(
                        label = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                        enabled = !isPreparing,
                        iconSize = 48.dp,
                        onClick = onPlayPause,
                        isPlaying = isPlaying,
                    )
                    FullScreenTransportButton(
                        symbol = MaterialSymbols.SkipNext,
                        label = stringResource(R.string.player_next),
                        onClick = onNext,
                        iconSize = 36.dp,
                        skipForward = true,
                        enabled = canNavigateNext,
                    )
                }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MaterialSymbol(
                            symbol = MaterialSymbols.VolumeDown,
                            contentDescription = null,
                            tint = volumeIconColor,
                            size = 20.dp,
                            filled = true,
                            modifier = Modifier.offset(x = -volumeIconOffset),
                        )
                        Spacer(Modifier.width(10.dp))
                        AirmedyTrackSlider(
                            value = volume,
                            onValueChange = onVolumeChange,
                            onInteractionChange = { isVolumeSliderInteracting = it },
                            trackHeight = 6.dp,
                            hazeState = glassHazeState,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = volumeLabel },
                        )
                        Spacer(Modifier.width(10.dp))
                        MaterialSymbol(
                            symbol = MaterialSymbols.VolumeUp,
                            contentDescription = null,
                            tint = volumeIconColor,
                            size = 20.dp,
                            filled = true,
                            modifier = Modifier.offset(x = volumeIconOffset),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FullScreenControlSlot {
                            FullScreenTransportButton(
                                MaterialSymbols.Chat,
                                stringResource(R.string.player_lyrics),
                                {
                                    selectedPanel = if (selectedPanel == FullScreenPlayerPanel.Lyrics) {
                                        null
                                    } else {
                                        FullScreenPlayerPanel.Lyrics
                                    }
                                },
                                iconSize = 24.dp,
                                tint = lyricsButtonIconColor,
                                containerColor = lyricsButtonBackground,
                                filled = false,
                            )
                        }
                        FullScreenControlSlot {
                            FullScreenTransportButton(
                                MaterialSymbols.Airplay,
                                stringResource(R.string.player_cast),
                                onOpenMediaOutputSwitcher,
                                iconSize = 24.dp,
                                tint = colors.foregroundSubtle,
                                filled = false,
                            )
                        }
                        FullScreenControlSlot {
                            FullScreenTransportButton(
                                MaterialSymbols.QueueMusic,
                                stringResource(R.string.player_queue),
                                {
                                    selectedPanel = if (selectedPanel == FullScreenPlayerPanel.Queue) {
                                        null
                                    } else {
                                        FullScreenPlayerPanel.Queue
                                    }
                                },
                                iconSize = 24.dp,
                                tint = queueButtonIconColor,
                                containerColor = queueButtonBackground,
                                filled = false,
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun FullScreenPlayerMetadataTransition(
    item: PlaybackItem,
    crossfade: ArtworkCrossfadeTransition?,
    displayedHorizontalSwipeOffset: Float,
    hazeState: HazeState?,
    compact: Boolean,
) {
    // The player state switches to the incoming source as native crossfade
    // starts. Mirror that moment in metadata instead of waiting for midpoint.
    androidx.compose.animation.AnimatedContent(
        targetState = item,
        transitionSpec = {
            if (crossfade != null) {
                (slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { it / 4 } +
                    fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                    (slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -it / 4 } +
                        fadeOut(tween(160, easing = FastOutSlowInEasing)))
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
        label = "full-screen-player-metadata-crossfade",
    ) { animatedItem ->
        FullScreenPlayerMetadata(
            item = animatedItem,
            displayedHorizontalSwipeOffset = displayedHorizontalSwipeOffset,
            hazeState = hazeState,
            compact = compact,
        )
    }
}

@Composable
private fun FullScreenPlayerMetadata(
    item: PlaybackItem,
    displayedHorizontalSwipeOffset: Float,
    hazeState: HazeState?,
    compact: Boolean,
) {
    val colors = LocalAirmedyColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
                .graphicsLayer { translationX = displayedHorizontalSwipeOffset }
                .semantics { testTag = FullScreenPlayerMetadataSwipeTestTag },
        ) {
            AirmedyMarqueeText(
                text = item.title,
                color = colors.onPrimary,
                style = if (compact) {
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                },
            )
            AirmedyMarqueeText(
                text = item.artist,
                color = colors.foregroundSubtle,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.width(4.dp))
        if (!compact) {
            AirmedyIconButton(
                symbol = MaterialSymbols.FavoriteBorder,
                label = stringResource(R.string.player_heart),
                onClick = {},
                variant = AirmedyIconButtonVariant.Glass,
                tint = colors.onPrimary,
                glassColor = Color.White.copy(alpha = 0.06f),
                hazeState = hazeState,
                circleSize = 36.dp,
                iconSize = 20.dp,
            )
        }
        AirmedyIconButton(
            symbol = MaterialSymbols.MoreVert,
            label = stringResource(R.string.player_more),
            onClick = {},
            variant = AirmedyIconButtonVariant.Glass,
            tint = colors.onPrimary,
            glassColor = Color.White.copy(alpha = 0.06f),
            hazeState = hazeState,
            circleSize = if (compact) 32.dp else 36.dp,
            iconSize = if (compact) 18.dp else 20.dp,
        )
    }
}

@Composable
private fun FullScreenPlayerPanelPlaceholder(
    panel: FullScreenPlayerPanel,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val label = stringResource(
        if (panel == FullScreenPlayerPanel.Lyrics) R.string.player_lyrics else R.string.player_queue,
    )
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = colors.foregroundSubtle,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}


private class FullScreenPlayerSwipeState {
    var dragOffset by mutableStateOf(0f)
    var isDragging by mutableStateOf(false)
    var dragStartedAtMs by mutableStateOf(0L)
}

@Composable
private fun FullScreenPlayerSwipeTarget(
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
    val maximumOffsetPx = with(density) { FullScreenPlayerSwipeMaximum.toPx() }
    val thresholdPx = with(density) { FullScreenPlayerSwipeThreshold.toPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.testTag = testTag }
            .pointerInput(maximumOffsetPx, thresholdPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        swipeState.dragOffset = displayedOffset
                        swipeState.dragStartedAtMs = android.os.SystemClock.uptimeMillis()
                        swipeState.isDragging = true
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        swipeState.dragOffset = (swipeState.dragOffset + dragAmount)
                            .coerceIn(-maximumOffsetPx, maximumOffsetPx)
                    },
                    onDragCancel = { swipeState.isDragging = false },
                    onDragEnd = {
                        val durationMs = (android.os.SystemClock.uptimeMillis() - swipeState.dragStartedAtMs)
                            .coerceAtLeast(1L)
                        val velocityPxPerMs = swipeState.dragOffset / durationMs
                        val shouldChangeTrack = swipeState.dragOffset.absoluteValue >= thresholdPx ||
                            velocityPxPerMs.absoluteValue >= FullScreenPlayerSwipeVelocityPxPerMs
                        val swipeDirection = swipeState.dragOffset.compareTo(0f)
                        swipeState.isDragging = false
                        swipeState.dragOffset = 0f

                        if (
                            shouldChangeTrack &&
                            canDispatchQueueSwipe(swipeDirection, canNavigatePrevious, canNavigateNext)
                        ) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (swipeDirection < 0) onNext() else onPrevious()
                        }
                    },
                )
            },
    ) {
        Box(
            modifier = if (movesContent) {
                Modifier.graphicsLayer { translationX = displayedOffset }
            } else {
                Modifier
            },
        ) { content() }
    }
}

@Composable
private fun FullScreenPlayerDragHandle() {
    val colors = LocalAirmedyColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(4.dp)
                .semantics { testTag = FullScreenPlayerDragHandleTestTag }
                .clip(FullScreenPlayerDragHandleShape)
                .background(colors.foregroundSubtle),
        )
    }
}

@Composable
private fun FullScreenPlayerBackground(
    artwork: FullScreenArtwork?,
    outgoingArtwork: FullScreenArtwork?,
    incomingArtwork: FullScreenArtwork?,
    crossfadeProgress: Float,
    isArtworkCrossfading: Boolean,
    modifier: Modifier,
) {
    val colors = LocalAirmedyColors.current
    val dominantColor by animateColorAsState(
        targetValue = artwork?.dominant ?: colors.playerBackdrop,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "full-screen-background-colour",
    )
    Box(modifier.background(colors.playerBackdrop)) {
        if (isArtworkCrossfading) {
            val outgoingAlpha = equalPowerOutgoing(crossfadeProgress)
            val incomingAlpha = equalPowerIncoming(crossfadeProgress)
            PlayerBackgroundGradient(outgoingArtwork?.dominant ?: colors.playerBackdrop, outgoingAlpha)
            PlayerBackgroundGradient(incomingArtwork?.dominant ?: colors.playerBackdrop, incomingAlpha)
        } else {
            PlayerBackgroundGradient(dominantColor, 1f)
        }
        Box(Modifier.fillMaxSize().background(colors.playerBackdrop.copy(alpha = 0.36f)))
    }
}

@Composable
private fun PlayerBackgroundGradient(dominant: Color, alpha: Float) {
    val colors = LocalAirmedyColors.current
    Box(
        Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(
                Brush.verticalGradient(
                    listOf(dominant.copy(alpha = 0.72f), colors.playerBackdrop.copy(alpha = 0.12f)),
                ),
            ),
    )
}

@Composable
private fun Artwork(
    artwork: FullScreenArtwork?,
    outgoingArtwork: FullScreenArtwork?,
    incomingArtwork: FullScreenArtwork?,
    crossfadeProgress: Float,
    isArtworkCrossfading: Boolean,
    modifier: Modifier,
) {
    val colors = LocalAirmedyColors.current
    Box(modifier.clip(FullScreenArtworkShape).background(colors.glassElevated), contentAlignment = Alignment.Center) {
        if (isArtworkCrossfading) {
            ArtworkLayer(outgoingArtwork, equalPowerOutgoing(crossfadeProgress))
            ArtworkLayer(incomingArtwork, equalPowerIncoming(crossfadeProgress))
        } else {
            ArtworkLayer(artwork, 1f)
        }
    }
}

@Composable
private fun ArtworkLayer(artwork: FullScreenArtwork?, alpha: Float) {
    val colors = LocalAirmedyColors.current
    if (artwork != null) {
        Image(artwork.image, null, Modifier.fillMaxSize().alpha(alpha), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxSize().alpha(alpha), contentAlignment = Alignment.Center) {
            MaterialSymbol(symbol = MaterialSymbols.MusicNote, contentDescription = null, tint = colors.textMuted, size = 64.dp)
        }
    }
}

@Composable
private fun RowScope.FullScreenControlSlot(content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun FullScreenTransportButton(
    symbol: String? = null,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 32.dp,
    tint: Color? = null,
    containerColor: Color? = null,
    filled: Boolean = true,
    isPlaying: Boolean? = null,
    skipForward: Boolean? = null,
) {
    val colors = LocalAirmedyColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(64.dp)
            .then(
                if (containerColor == null) {
                    Modifier
                } else {
                    Modifier
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(containerColor)
                },
            )
            .semantics { contentDescription = label }
            .clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Keep the transport glyph's settled tint while Preparing. The control
        // remains disabled, but changing its tint during the hand-off creates
        // a perceptible flash between consecutive tracks.
        val iconTint = tint ?: if (enabled || isPlaying != null) colors.onPrimary else colors.textMuted
        if (isPlaying != null) {
            AnimatedPlayPauseSymbol(
                isPlaying = isPlaying,
                isPreparing = !enabled,
                isPressed = isPressed,
                tint = iconTint,
                size = iconSize,
                touchTargetSize = 64.dp,
            )
        } else if (skipForward != null) {
            AnimatedSkipSymbol(
                forward = skipForward,
                isPressed = isPressed,
                tint = iconTint,
                size = iconSize,
                touchTargetSize = 64.dp,
            )
        } else {
            MaterialSymbol(
                symbol = requireNotNull(symbol),
                contentDescription = null,
                tint = iconTint,
                size = iconSize,
                filled = filled,
            )
        }
    }
}

private data class FullScreenArtwork(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val dominant: Color,
)

@Composable
private fun rememberArtworkCrossfadeProgress(crossfade: ArtworkCrossfadeTransition?): Float {
    // Key the initial value to the transition ID. LaunchedEffect runs after a
    // composition; retaining the previous completed value (1f) for that first
    // frame briefly displayed the incoming cover at full opacity.
    var progress by remember(crossfade?.id) {
        mutableFloatStateOf(if (crossfade == null) 1f else 0f)
    }
    LaunchedEffect(crossfade?.id) {
        if (crossfade == null) {
            return@LaunchedEffect
        }
        val durationNanos = crossfade.durationMs.coerceAtLeast(1L) * 1_000_000L
        var startedAtNanos = 0L
        while (progress < 1f) {
            withFrameNanos { frameNanos ->
                if (startedAtNanos == 0L) startedAtNanos = frameNanos
                progress = ((frameNanos - startedAtNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
            }
        }
    }
    return progress
}

private fun equalPowerOutgoing(progress: Float): Float =
    kotlin.math.cos(progress.coerceIn(0f, 1f) * Math.PI.toFloat() / 2f)

private fun equalPowerIncoming(progress: Float): Float =
    kotlin.math.sin(progress.coerceIn(0f, 1f) * Math.PI.toFloat() / 2f)

@Composable
private fun rememberFullscreenArtwork(artworkPath: String?, keepPrevious: Boolean = true): FullScreenArtwork? {
    val context = LocalContext.current
    var artwork by remember { mutableStateOf<FullScreenArtwork?>(null) }
    LaunchedEffect(artworkPath) {
        if (!keepPrevious) artwork = null
        if (artworkPath.isNullOrBlank()) {
            artwork = null
            return@LaunchedEffect
        }
        artwork = withContext(Dispatchers.IO) {
            val file = File(if (File(artworkPath).isAbsolute) artworkPath else File(context.filesDir, artworkPath).path)
            if (!file.isFile) return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1080 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
                val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: return@runCatching null
                FullScreenArtwork(bitmap.asImageBitmap(), dominantColor(bitmap))
            }.getOrNull()
        }
    }
    return artwork
}

private fun dominantColor(bitmap: Bitmap): Color {
    val sample = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
    val pixels = IntArray(24 * 24)
    sample.getPixels(pixels, 0, 24, 0, 0, 24, 24)
    val opaque = pixels.filter { android.graphics.Color.alpha(it) > 32 }
    if (opaque.isEmpty()) return Color.Transparent
    return Color(
        opaque.sumOf { android.graphics.Color.red(it) } / opaque.size,
        opaque.sumOf { android.graphics.Color.green(it) } / opaque.size,
        opaque.sumOf { android.graphics.Color.blue(it) } / opaque.size,
    )
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

private fun formatPlaybackTime(timeMs: Long): String {
    val seconds = (timeMs.coerceAtLeast(0L) / 1000).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
