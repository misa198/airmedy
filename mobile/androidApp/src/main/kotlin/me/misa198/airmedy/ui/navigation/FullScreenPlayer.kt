package me.misa198.airmedy.ui.navigation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
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
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.ui.components.AirmedyIconButton
import me.misa198.airmedy.ui.components.AirmedyIconButtonVariant
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.AirmedyMarqueeText
import me.misa198.airmedy.ui.components.AirmedyTrackSlider
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

@Composable
internal fun FullScreenPlayer(
    visible: Boolean,
    dragProgress: Float,
    isDragging: Boolean,
    openingFromMiniPlayerSwipe: Boolean,
    playbackState: PlaybackState,
    volume: Float,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
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
    val currentPositionMs = playbackState.positionMsOrZero()
    val durationMs = playbackState.durationMsOrZero()
    val isPreparing = playbackState is PlaybackState.Preparing
    val isPlaying = playbackState is PlaybackState.Playing
    val artworkScale by animateFloatAsState(
        targetValue = if (playbackState is PlaybackState.Paused) 0.75f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "full-screen-artwork-scale",
    )
    var pendingSeekFraction by remember(item.trackId) { mutableStateOf<Float?>(null) }
    val horizontalSwipeState = remember { FullScreenPlayerSwipeState() }
    val displayedHorizontalSwipeOffset by animateFloatAsState(
        targetValue = if (horizontalSwipeState.isDragging) horizontalSwipeState.dragOffset else 0f,
        animationSpec = spring(),
        label = "full-screen-player-swipe",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (glassHazeState == null) Modifier else Modifier.hazeSource(glassHazeState)),
            )
            Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
            Spacer(Modifier.height(12.dp))
            FullScreenPlayerDragHandle()
            Spacer(Modifier.height(20.dp))
            FullScreenPlayerSwipeTarget(
                testTag = FullScreenPlayerArtworkSwipeTestTag,
                swipeState = horizontalSwipeState,
                displayedOffset = displayedHorizontalSwipeOffset,
                onPrevious = onPrevious,
                onNext = onNext,
            ) {
                Column {
                    Artwork(
                        artwork,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .semantics { testTag = FullScreenPlayerArtworkTestTag }
                            .graphicsLayer {
                                scaleX = artworkScale
                                scaleY = artworkScale
                            },
                    )
                    Spacer(Modifier.height(36.dp))
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
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            AirmedyMarqueeText(
                                text = item.artist,
                                color = colors.foregroundSubtle,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        AirmedyIconButton(
                            symbol = MaterialSymbols.FavoriteBorder,
                            label = stringResource(R.string.player_heart),
                            onClick = {},
                            variant = AirmedyIconButtonVariant.Glass,
                            tint = colors.onPrimary,
                            glassColor = Color.White.copy(alpha = 0.06f),
                            hazeState = glassHazeState,
                            circleSize = 36.dp,
                            iconSize = 20.dp,
                        )
                        AirmedyIconButton(
                            symbol = MaterialSymbols.MoreVert,
                            label = stringResource(R.string.player_more),
                            onClick = {},
                            variant = AirmedyIconButtonVariant.Glass,
                            tint = colors.onPrimary,
                            glassColor = Color.White.copy(alpha = 0.06f),
                            hazeState = glassHazeState,
                            circleSize = 36.dp,
                            iconSize = 20.dp,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column {
                    AirmedyTrackSlider(
                        value = pendingSeekFraction ?: if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f,
                        onValueChange = { pendingSeekFraction = it },
                        onValueChangeFinished = {
                            pendingSeekFraction?.let { fraction -> onSeek((durationMs * fraction).toLong()) }
                            pendingSeekFraction = null
                        },
                        enabled = durationMs > 0 && !isPreparing,
                        trackHeight = 6.dp,
                        modifier = Modifier.semantics { contentDescription = seekLabel },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatPlaybackTime(currentPositionMs), color = colors.foregroundSubtle, style = MaterialTheme.typography.labelSmall)
                        Text(formatPlaybackTime(durationMs), color = colors.foregroundSubtle, style = MaterialTheme.typography.labelSmall)
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
                    )
                    FullScreenTransportButton(
                        symbol = if (isPlaying) MaterialSymbols.Pause else MaterialSymbols.PlayArrow,
                        label = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                        enabled = !isPreparing,
                        iconSize = 48.dp,
                        onClick = onPlayPause,
                    )
                    FullScreenTransportButton(
                        symbol = MaterialSymbols.SkipNext,
                        label = stringResource(R.string.player_next),
                        onClick = onNext,
                        iconSize = 36.dp,
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
                            tint = colors.textMuted,
                            size = 20.dp,
                            filled = true
                        )
                        Spacer(Modifier.width(10.dp))
                        AirmedyTrackSlider(
                            value = volume,
                            onValueChange = onVolumeChange,
                            trackHeight = 6.dp,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = volumeLabel },
                        )
                        Spacer(Modifier.width(10.dp))
                        MaterialSymbol(
                            symbol = MaterialSymbols.VolumeUp,
                            contentDescription = null,
                            tint = colors.textMuted,
                            size = 20.dp,
                            filled = true
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FullScreenControlSlot {
                            FullScreenTransportButton(
                                MaterialSymbols.Chat,
                                stringResource(R.string.player_lyrics),
                                {},
                                iconSize = 24.dp,
                                tint = colors.foregroundSubtle,
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
                                {},
                                iconSize = 24.dp,
                                tint = colors.foregroundSubtle,
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
            .clipToBounds()
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

                        if (shouldChangeTrack && swipeDirection != 0) {
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
private fun FullScreenPlayerBackground(artwork: FullScreenArtwork?, modifier: Modifier) {
    val colors = LocalAirmedyColors.current
    val dominantColor by animateColorAsState(
        targetValue = artwork?.dominant ?: colors.playerBackdrop,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "full-screen-background-colour",
    )
    Box(modifier.background(colors.playerBackdrop)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(dominantColor.copy(alpha = 0.72f), colors.playerBackdrop.copy(alpha = 0.12f)),
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(colors.playerBackdrop.copy(alpha = 0.36f)))
    }
}

@Composable
private fun Artwork(artwork: FullScreenArtwork?, modifier: Modifier) {
    val colors = LocalAirmedyColors.current
    Box(modifier.clip(FullScreenArtworkShape).background(colors.glassElevated), contentAlignment = Alignment.Center) {
        if (artwork != null) Image(artwork.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else MaterialSymbol(symbol = MaterialSymbols.MusicNote, contentDescription = null, tint = colors.textMuted, size = 64.dp)
    }
}

@Composable
private fun RowScope.FullScreenControlSlot(content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun FullScreenTransportButton(
    symbol: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 32.dp,
    tint: Color? = null,
    filled: Boolean = true,
) {
    val colors = LocalAirmedyColors.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(64.dp)) {
        MaterialSymbol(
            symbol = symbol,
            contentDescription = label,
            tint = tint ?: if (enabled) colors.onPrimary else colors.textMuted,
            size = iconSize,
            filled = filled,
        )
    }
}

private data class FullScreenArtwork(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val dominant: Color,
)

@Composable
private fun rememberFullscreenArtwork(artworkPath: String?): FullScreenArtwork? {
    val context = LocalContext.current
    var artwork by remember { mutableStateOf<FullScreenArtwork?>(null) }
    LaunchedEffect(artworkPath) {
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
