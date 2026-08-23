package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

internal data class PlayerLyricLine(
    val primary: String,
    val secondary: String? = null,
    val timestampSeconds: Float? = null,
)

private val TimestampedLyricLine = Regex("^\\[(\\d+):(\\d+(?:\\.\\d+)?)\\](.*)$")
private val BilingualSeparator = Regex("\\s*\\^\\s*|\\s*/\\s*")
private const val ForwardSeekAnimatedApproachRows = 3

internal enum class LyricsSeekDirection { Backward, Forward }

internal fun lyricsSeekDirection(targetIndex: Int, firstVisibleIndex: Int): LyricsSeekDirection =
    if (targetIndex < firstVisibleIndex) LyricsSeekDirection.Backward else LyricsSeekDirection.Forward

internal fun parsePlayerLyrics(content: String): List<PlayerLyricLine> = content.lineSequence()
    .mapNotNull { rawLine ->
        val match = TimestampedLyricLine.matchEntire(rawLine)
        val timestamp = match?.let { it.groupValues[1].toFloat() * 60f + it.groupValues[2].toFloat() }
        val text = match?.groupValues?.get(3) ?: rawLine
        text.trim().takeIf(String::isNotEmpty)?.let { parsePlayerLyricText(it, timestamp) }
    }
    .toList()

internal fun hasSyncedPlayerLyrics(content: String?): Boolean = content != null && parsePlayerLyrics(content).any { it.timestampSeconds != null }

/** Resume automatic following once playback reaches the tapped lyric or passes it. */
internal fun shouldResumeLyricsAutoScroll(
    selectedLineIndex: Int?,
    activeIndex: Int,
    activeIndexWhenLineSelected: Int?,
    selectedLineAnimationComplete: Boolean,
): Boolean = selectedLineAnimationComplete && selectedLineIndex != null &&
    activeIndex >= selectedLineIndex && activeIndex != activeIndexWhenLineSelected

/** The active line may advance beyond the visible viewport while the app is backgrounded. */
internal fun shouldFollowLyricsActiveLine(previousActiveLineInViewport: Boolean, returnedToForeground: Boolean): Boolean =
    previousActiveLineInViewport || returnedToForeground

/** A repeat/replay restarts the same track near zero without changing its track ID. */
internal fun shouldResetLyricsForReplay(previousPositionMs: Long, currentPositionMs: Long): Boolean =
    previousPositionMs > 1_000L && currentPositionMs <= 1_000L

/** Prefer a slider's requested position until playback confirms the seek. */
internal fun displayedLyricsPositionMs(playbackPositionMs: Long, pendingSeekPositionMs: Long?): Long =
    pendingSeekPositionMs ?: playbackPositionMs

/** Programmatic lyric positioning must not be interpreted as manual browsing. */
internal fun shouldEnterLyricsBrowseMode(isUserDragging: Boolean, isFollowingSelectedLine: Boolean = false): Boolean =
    isUserDragging && !isFollowingSelectedLine

/** Small finger drift on a lyric row is still a seek, not a manual browse. */
internal fun shouldSeekFromLyricTap(dragDistancePx: Float, tapSlopPx: Float): Boolean = dragDistancePx <= tapSlopPx

private fun parsePlayerLyricText(text: String, timestampSeconds: Float?): PlayerLyricLine {
    val parts = BilingualSeparator.split(text, limit = 2)
    val secondary = parts.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty)
    return PlayerLyricLine(primary = parts.first().trim(), secondary = secondary, timestampSeconds = timestampSeconds)
}

@Composable
internal fun FullScreenPlayerLyricsPanel(
    trackId: String,
    lyrics: String?,
    loading: Boolean = false,
    currentPositionMs: Long,
    pendingSeekPositionMs: Long? = null,
    seekRequestId: Long = 0L,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsedLines = remember(lyrics) { lyrics?.let(::parsePlayerLyrics).orEmpty() }
    val syncedLines = remember(parsedLines) { parsedLines.filter { it.timestampSeconds != null } }
    Column(modifier = modifier.padding(top = 8.dp)) {
        when {
            loading -> LyricsLoadingState(Modifier.fillMaxSize())
            lyrics.isNullOrBlank() -> LyricsEmptyState(Modifier.fillMaxSize())
            syncedLines.isNotEmpty() -> SyncedLyricsList(
                trackId,
                syncedLines,
                currentPositionMs,
                pendingSeekPositionMs,
                seekRequestId,
                onSeek,
                Modifier.fillMaxSize(),
            )
            else -> PlainLyricsList(parsedLines, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun LyricsLoadingState(modifier: Modifier) {
    val colors = LocalAirmedyColors.current
    val transition = rememberInfiniteTransition(label = "lyrics-loading")
    val shimmerOffset by transition.animateFloat(
        initialValue = -220f,
        targetValue = 500f,
        animationSpec = infiniteRepeatable(tween(1_700, easing = LinearEasing), RepeatMode.Restart),
        label = "lyrics-loading-shimmer",
    )
    val shimmer = Brush.linearGradient(
        colors = listOf(
            colors.foregroundSubtle.copy(alpha = .08f),
            colors.foregroundSubtle.copy(alpha = .16f),
            colors.foregroundSubtle.copy(alpha = .08f),
        ),
        start = Offset(shimmerOffset - 220f, 0f),
        end = Offset(shimmerOffset, 0f),
    )
    Column(modifier = modifier.padding(top = 24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
        listOf(280.dp, 108.dp, 238.dp, 84.dp, 264.dp).forEach { width ->
            Box(
                Modifier
                    .height(20.dp)
                    .width(width)
                    .background(shimmer, RoundedCornerShape(8.dp))
                    .testTag("lyrics_loading_skeleton"),
            )
        }
    }
}

@Composable
private fun SyncedLyricsList(
    trackId: String,
    lines: List<PlayerLyricLine>,
    currentPositionMs: Long,
    pendingSeekPositionMs: Long?,
    seekRequestId: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val rowHeights = remember(lines) { mutableStateMapOf<Int, Int>() }
    val trailingLineHeights = remember(lines) { mutableStateMapOf<Int, Int>() }
    val rowBottomPaddingPx = with(LocalDensity.current) { 10.dp.roundToPx() }
    val backwardSeekApproachPx = with(LocalDensity.current) { 72.dp.roundToPx() }
    var hasPositionedInitialLine by remember(lines) { mutableStateOf(false) }
    var previousActiveIndex by remember(lines) { mutableStateOf<Int?>(null) }
    var isBrowsing by remember(lines) { mutableStateOf(false) }
    var selectedLineIndex by remember(lines) { mutableStateOf<Int?>(null) }
    var activeIndexWhenLineSelected by remember(lines) { mutableStateOf<Int?>(null) }
    var selectedLineAnimationComplete by remember(lines) { mutableStateOf(false) }
    var isFollowingSelectedLine by remember(lines) { mutableStateOf(false) }
    var returnedToForeground by remember(lines) { mutableStateOf(false) }
    var previousPositionMs by remember(trackId) { mutableLongStateOf(currentPositionMs) }
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    val displayedPositionMs = displayedLyricsPositionMs(currentPositionMs, pendingSeekPositionMs)
    val activeIndex = remember(lines, displayedPositionMs) {
        lines.indexOfLast { (it.timestampSeconds ?: Float.MAX_VALUE) <= displayedPositionMs / 1_000f }
    }
    DisposableEffect(lifecycleOwner, lines) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // A StateFlow collector receives only the latest position after a stopped
                // activity resumes. Re-centre it even when the old active row is off-screen.
                returnedToForeground = true
                isBrowsing = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    suspend fun resetToStart() {
        isBrowsing = false
        selectedLineIndex = null
        activeIndexWhenLineSelected = null
        selectedLineAnimationComplete = false
        hasPositionedInitialLine = false
        previousActiveIndex = null
        listState.scrollToItem(0)
    }
    LaunchedEffect(trackId) {
        resetToStart()
    }
    LaunchedEffect(trackId, currentPositionMs) {
        if (shouldResetLyricsForReplay(previousPositionMs, currentPositionMs)) resetToStart()
        previousPositionMs = currentPositionMs
    }
    LaunchedEffect(isUserDragging, isFollowingSelectedLine) {
        if (shouldEnterLyricsBrowseMode(isUserDragging, isFollowingSelectedLine)) isBrowsing = true
    }
    suspend fun previousLineOffset(activeLineIndex: Int): Int {
        val previousIndex = (activeLineIndex - 1).coerceAtLeast(0)
        return if (previousIndex < activeLineIndex) {
            snapshotFlow { rowHeights[previousIndex] to trailingLineHeights[previousIndex] }
                .first { (rowHeight, trailingLineHeight) -> rowHeight != null && trailingLineHeight != null }
                .let { (rowHeight, trailingLineHeight) ->
                    (rowHeight!! - trailingLineHeight!! - rowBottomPaddingPx).coerceAtLeast(0)
                }
        } else {
            0
        }
    }

    suspend fun positionInitialLine(activeLineIndex: Int) {
        val previousIndex = (activeLineIndex - 1).coerceAtLeast(0)
        listState.scrollToItem(previousIndex)
        listState.scrollToItem(previousIndex, previousLineOffset(activeLineIndex))
    }

    suspend fun animateToActiveLine(activeLineIndex: Int) {
        val previousIndex = (activeLineIndex - 1).coerceAtLeast(0)
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == previousIndex }
        if (target != null) {
            val offset = previousLineOffset(activeLineIndex)
            listState.animateScrollBy(
                (target.offset + offset).toFloat(),
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            )
        } else {
            // Do not animate through a long remote list: it makes a fast seek
            // feel sluggish. Jump just before the target so its measured height
            // is available, then perform one final alignment animation. This
            // avoids a visible second correction for wrapped lyric lines.
            val firstVisibleIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: previousIndex
            when (lyricsSeekDirection(previousIndex, firstVisibleIndex)) {
                LyricsSeekDirection.Forward -> {
                    listState.scrollToItem((previousIndex - ForwardSeekAnimatedApproachRows).coerceAtLeast(0))
                    val alignedTarget = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == previousIndex }
                    if (alignedTarget != null) {
                        listState.animateScrollBy(
                            (alignedTarget.offset + previousLineOffset(activeLineIndex)).toFloat(),
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                        )
                    } else {
                        listState.scrollToItem(previousIndex, previousLineOffset(activeLineIndex))
                    }
                }
                LyricsSeekDirection.Backward -> {
                    // Measure the remote row, then begin above its focus slot so
                    // the final movement visibly follows the backward seek.
                    listState.scrollToItem(previousIndex)
                    val focusOffset = previousLineOffset(activeLineIndex)
                    listState.scrollToItem(previousIndex, focusOffset + backwardSeekApproachPx)
                    listState.animateScrollBy(
                        -backwardSeekApproachPx.toFloat(),
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                    )
                }
            }
        }
    }

    LaunchedEffect(seekRequestId) {
        if (seekRequestId > 0L && activeIndex >= 0) {
            // Handle every slider seek as its own positioning command. Do not
            // depend on the regular active-line follower: it intentionally
            // respects browse mode after a previous scroll.
            isBrowsing = false
            selectedLineIndex = null
            animateToActiveLine(activeIndex)
            hasPositionedInitialLine = true
            previousActiveIndex = activeIndex
            returnedToForeground = false
        }
    }

    // Keep the tapped line in view and move it into the active slot before
    // seeking. This avoids the abrupt focus jump that otherwise occurs after
    // a listener has manually browsed away from the current lyric.
    LaunchedEffect(selectedLineIndex) {
        val selectedIndex = selectedLineIndex ?: return@LaunchedEffect
        isFollowingSelectedLine = true
        try {
            animateToActiveLine(selectedIndex)
            hasPositionedInitialLine = true
            previousActiveIndex = selectedIndex
            selectedLineAnimationComplete = true
            if (selectedIndex == activeIndexWhenLineSelected) selectedLineIndex = null
        } finally {
            isFollowingSelectedLine = false
        }
    }
    LaunchedEffect(activeIndex, selectedLineIndex, activeIndexWhenLineSelected, selectedLineAnimationComplete) {
        if (shouldResumeLyricsAutoScroll(selectedLineIndex, activeIndex, activeIndexWhenLineSelected, selectedLineAnimationComplete)) {
            selectedLineIndex = null
        }
    }
    LaunchedEffect(lines, activeIndex, isBrowsing, selectedLineIndex, returnedToForeground) {
        if (activeIndex < 0 || isBrowsing || selectedLineIndex != null) return@LaunchedEffect
        if (!hasPositionedInitialLine) {
            positionInitialLine(activeIndex)
            hasPositionedInitialLine = true
            returnedToForeground = false
        } else {
            val previousActiveLineInViewport = previousActiveIndex
                ?.let { index -> listState.layoutInfo.visibleItemsInfo.any { it.index == index } }
                ?: false
            if (shouldFollowLyricsActiveLine(previousActiveLineInViewport, returnedToForeground)) {
                if (returnedToForeground) positionInitialLine(activeIndex) else animateToActiveLine(activeIndex)
                returnedToForeground = false
            }
        }
        previousActiveIndex = activeIndex
    }
    LazyColumn(
        state = listState,
        userScrollEnabled = true,
        modifier = modifier.testTag("synced_lyrics_list"),
    ) {
        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
            SyncedLyricRow(
                line = line,
                distance = if (activeIndex >= 0) kotlin.math.abs(index - activeIndex) else Int.MAX_VALUE,
                onClick = {
                    isBrowsing = false
                    activeIndexWhenLineSelected = activeIndex
                    selectedLineAnimationComplete = false
                    isFollowingSelectedLine = true
                    selectedLineIndex = index
                    onSeek((line.timestampSeconds!! * 1_000).toLong())
                },
                onRowHeightChanged = { rowHeights[index] = it },
                onTrailingLineHeightChanged = { trailingLineHeights[index] = it },
                focusMode = !isBrowsing,
                blurEnabled = !isBrowsing && !listState.isScrollInProgress,
            )
        }
    }
}

@Composable
private fun SyncedLyricRow(
    line: PlayerLyricLine,
    distance: Int,
    onClick: () -> Unit,
    onRowHeightChanged: (Int) -> Unit,
    onTrailingLineHeightChanged: (Int) -> Unit,
    focusMode: Boolean,
    blurEnabled: Boolean,
) {
    val colors = LocalAirmedyColors.current
    // The pointer coroutine remains alive across playback-position and track
    // recompositions. Read the newest callback when a tap finishes so it
    // cannot dispatch through the callback captured for an earlier track.
    val latestOnClick = rememberUpdatedState(onClick)
    val targetOpacity = if (!focusMode) {
        1f
    } else when (distance) {
        0 -> 1f
        1 -> 0.25f
        2 -> 0.15f
        else -> 0.10f
    }
    val targetBlur = when (distance) {
        0 -> 0.dp
        1 -> 0.35.dp
        2 -> 1.25.dp
        else -> 2.dp
    }
    val opacity by animateFloatAsState(targetOpacity, tween(300, easing = FastOutSlowInEasing), label = "synced-lyric-opacity")
    // An incoming active line removes blur immediately to avoid clipping its
    // scale animation. An outgoing line fades blur in smoothly instead.
    val animatedBlur by animateDpAsState(targetBlur, tween(300, easing = FastOutSlowInEasing), label = "synced-lyric-blur")
    val blur = if (distance == 0) 0.dp else animatedBlur
    val scale by animateFloatAsState(if (focusMode && distance == 0) 1.04f else 1f, tween(300, easing = FastOutSlowInEasing), label = "synced-lyric-scale")
    val activeOffsetPx = with(LocalDensity.current) { 4.dp.toPx() }
    val lyricTapSlopPx = with(LocalDensity.current) { 20.dp.toPx() }
    val animatedTranslationY by animateFloatAsState(
        if (focusMode && distance == 0) -activeOffsetPx else 0f,
        tween(300, easing = FastOutSlowInEasing),
        label = "synced-lyric-offset",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Reserve room for active-line scaling without changing wrapping
            // only when the active state changes.
            .padding(top = 10.dp, bottom = 10.dp, end = 16.dp)
            .then(
                if (blurEnabled) Modifier.blur(blur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                else Modifier,
            )
            // Transform after text layout so the active line grows subtly
            // without changing its wrapping or displacing adjacent lyrics.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = animatedTranslationY
                transformOrigin = TransformOrigin(0f, 0.5f)
                clip = false
            }
            .onSizeChanged { onRowHeightChanged(it.height) }
            .pointerInput(line.timestampSeconds) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPosition = down.position
                    var dragDistancePx = 0f
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        dragDistancePx = maxOf(dragDistancePx, (change.position - startPosition).getDistance())
                        pressed = change.pressed
                    }
                    if (shouldSeekFromLyricTap(dragDistancePx, lyricTapSlopPx)) latestOnClick.value()
                }
            }
            .semantics {
                role = Role.Button
                onClick { latestOnClick.value(); true }
            }
            .testTag("synced_lyric_${line.timestampSeconds}"),
    ) {
        Text(
            text = line.primary,
            color = colors.onPrimary.copy(alpha = opacity),
            style = MaterialTheme.typography.headlineSmall,
            // Keep glyph metrics stable when the line becomes active; changing
            // weight here would re-wrap the same text during scale animation.
            fontWeight = FontWeight.Bold,
            onTextLayout = { layout ->
                if (line.secondary == null) onTrailingLineHeightChanged((layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(layout.lineCount - 1)).roundToInt())
            },
        )
        line.secondary?.let {
            Text(
                text = it,
                color = colors.foregroundSubtle.copy(alpha = opacity),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
                onTextLayout = { layout ->
                    onTrailingLineHeightChanged((layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(layout.lineCount - 1)).roundToInt())
                },
            )
        }
    }
}

@Composable
private fun PlainLyricsList(lines: List<PlayerLyricLine>, modifier: Modifier) {
    val colors = LocalAirmedyColors.current
    LazyColumn(modifier = modifier.testTag("plain_lyrics_list")) {
        itemsIndexed(lines, key = { index, _ -> index }) { _, line ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(text = line.primary, color = colors.onPrimary, style = MaterialTheme.typography.bodyLarge)
                line.secondary?.let {
                    Text(text = it, color = colors.foregroundSubtle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun LyricsEmptyState(modifier: Modifier) {
    val colors = LocalAirmedyColors.current
    Text(
        text = stringResource(R.string.player_lyrics_not_available),
        color = colors.foregroundSubtle,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.padding(top = 24.dp),
    )
}
