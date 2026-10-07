package me.misa198.airmedy.ui.navigation

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.awaitCancellation
import me.misa198.airmedy.lyrics.RomanizationUiState
import kotlin.math.roundToInt
import kotlin.math.abs

internal data class PlayerLyricLine(
    val primary: String,
    val secondary: String? = null,
    val timestampSeconds: Float? = null,
    val words: List<PlayerLyricWord>? = null,
)

internal data class PlayerLyricWord(val text: String, val startSeconds: Float, val endSeconds: Float)

private val TimestampedLyricLine = Regex("^\\[(\\d+):([0-5]\\d(?:\\.\\d{1,3})?)\\](.*)$")
private val InlineTimingTag = Regex("<\\d+:[^<>]*>")
private val WordTimingTag = Regex("^<(\\d+):([0-5]\\d(?:\\.\\d{1,3})?)>$")
private val BilingualSeparator = Regex("\\s*\\^\\s*|\\s*/\\s*")
private const val ForwardSeekAnimatedApproachRows = 3
private const val LyricsMotionDurationMs = 320
private const val LyricsPlaybackTickMs = 200

internal enum class LyricsSeekDirection { Backward, Forward }

internal fun lyricsSeekDirection(targetIndex: Int, firstVisibleIndex: Int): LyricsSeekDirection =
    if (targetIndex < firstVisibleIndex) LyricsSeekDirection.Backward else LyricsSeekDirection.Forward

internal fun parsePlayerLyrics(content: String): List<PlayerLyricLine> {
    val lines = content.lineSequence().mapNotNull { rawLine ->
        val match = TimestampedLyricLine.matchEntire(rawLine)
        val timestamp = match?.let { it.groupValues[1].toFloat() * 60f + it.groupValues[2].toFloat() }
        val text = match?.groupValues?.get(3) ?: rawLine
        if (timestamp != null || text.trim().isNotEmpty()) PlayerLyricLine(text, timestampSeconds = timestamp) else null
    }.toList()
    return lines.mapIndexed { index, line ->
        parsePlayerLyricText(line.primary, line.timestampSeconds, lines.getOrNull(index + 1)?.timestampSeconds)
    }
}

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

internal fun syncedLyricBlurRadius(distance: Int) = when (distance) {
    0 -> 0.dp
    1 -> 0.35.dp
    2 -> 1.25.dp
    else -> 2.dp
}

internal fun syncedLyricScale(distance: Int, focusMode: Boolean, enhanced: Boolean): Float =
    if (focusMode && distance == 0 && !enhanced) 1.04f else 1f

private fun parsePlayerLyricText(text: String, timestampSeconds: Float?, nextTimestampSeconds: Float?): PlayerLyricLine {
    val parts = BilingualSeparator.split(text, limit = 2)
    val primary = parts.first()
    val secondary = parts.getOrNull(1)?.let(::cleanPlayerLyricText)?.takeIf(String::isNotEmpty)
    return PlayerLyricLine(
        primary = cleanPlayerLyricText(primary),
        secondary = secondary,
        timestampSeconds = timestampSeconds,
        words = timestampSeconds?.let { parsePlayerLyricWords(primary, it, nextTimestampSeconds) },
    )
}

private fun cleanPlayerLyricText(text: String): String = text.replace(InlineTimingTag, "").trim()

private fun parsePlayerLyricWords(text: String, startSeconds: Float, nextTimestampSeconds: Float?): List<PlayerLyricWord>? {
    val markers = InlineTimingTag.findAll(text).toList()
    if (markers.isEmpty()) return null
    val words = mutableListOf<PlayerLyricWord>()
    var start = startSeconds
    var cursor = 0
    for (marker in markers) {
        val match = WordTimingTag.matchEntire(marker.value) ?: return null
        val end = match.groupValues[1].toFloat() * 60f + match.groupValues[2].toFloat()
        if (end < start) return null
        val segment = text.substring(cursor, marker.range.first)
        if (segment.isNotBlank()) words += PlayerLyricWord(segment, start, end)
        else if (words.isNotEmpty()) words[words.lastIndex] = words.last().copy(text = words.last().text + segment)
        start = end
        cursor = marker.range.last + 1
    }
    val tail = text.substring(cursor)
    if (tail.isNotBlank()) {
        // ponytail: missing final-word ends use at most 1s; explicit tags avoid this heuristic.
        val end = minOf(start + 1f, nextTimestampSeconds ?: Float.POSITIVE_INFINITY)
        if (end < start) return null
        words += PlayerLyricWord(tail, start, end)
    }
    if (words.isEmpty()) return null
    words[0] = words.first().copy(text = words.first().text.trimStart())
    words[words.lastIndex] = words.last().copy(text = words.last().text.trimEnd())
    return words
}

@Composable
internal fun FullScreenPlayerLyricsPanel(
    trackId: String,
    lyrics: String?,
    loading: Boolean = false,
    visible: Boolean = true,
    romanization: RomanizationUiState = RomanizationUiState(),
    romanizationAllowed: Boolean = false,
    glowEnabled: Boolean = true,
    onRomanizationInput: (List<String>, Boolean) -> Unit = { _, _ -> },
    onRomanizationToggle: () -> Unit = {},
    currentPositionMs: Long,
    isPlaying: Boolean = false,
    pendingSeekPositionMs: Long? = null,
    seekRequestId: Long = 0L,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsedLines = remember(lyrics) { lyrics?.let(::parsePlayerLyrics).orEmpty() }
    val syncedLines = remember(parsedLines) { parsedLines.filter { it.timestampSeconds != null } }
    val primary = remember(parsedLines, syncedLines) { (syncedLines.ifEmpty { parsedLines }).map { it.primary } }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnInput by rememberUpdatedState(onRomanizationInput)
    LaunchedEffect(trackId, primary, visible, loading, lifecycleOwner) {
        if (visible && !loading) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                currentOnInput(primary, true)
                try {
                    awaitCancellation()
                } finally {
                    currentOnInput(emptyList(), false)
                }
            }
        }
    }
    val current = romanization.input == primary && !loading
    val secondary = if (romanizationAllowed && current && romanization.enabled) romanization.secondary else emptyList()
    val showToggle = romanizationAllowed && current && romanization.supported
    Box(modifier = modifier.padding(top = 8.dp)) {
        when {
            loading -> LyricsLoadingState(Modifier.fillMaxSize())
            lyrics.isNullOrBlank() -> LyricsEmptyState(Modifier.fillMaxSize())
            syncedLines.isNotEmpty() -> SyncedLyricsList(
                trackId,
                syncedLines,
                currentPositionMs,
                isPlaying,
                pendingSeekPositionMs,
                seekRequestId,
                onSeek,
                Modifier.fillMaxSize(),
                secondary,
                showToggle,
                glowEnabled,
            )
            else -> PlainLyricsList(parsedLines, Modifier.fillMaxSize(), secondary, showToggle)
        }
        if (showToggle) {
            RomanizationToggle(
                state = romanization,
                onClick = onRomanizationToggle,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
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
internal fun rememberLyricsDisplayPosition(
    trackId: String,
    currentPositionMs: Long,
    pendingSeekPositionMs: Long?,
    isPlaying: Boolean,
): Long {
    val lyricClock = remember(trackId) { Animatable(currentPositionMs.toFloat()) }
    LaunchedEffect(trackId, currentPositionMs, pendingSeekPositionMs, isPlaying) {
        val position = displayedLyricsPositionMs(currentPositionMs, pendingSeekPositionMs).toFloat()
        lyricClock.snapTo(position)
        if (isPlaying && pendingSeekPositionMs == null) {
            lyricClock.animateTo(position + LyricsPlaybackTickMs, tween(LyricsPlaybackTickMs, easing = LinearEasing))
        }
    }
    val clockPositionMs = lyricClock.value.toLong()
    return pendingSeekPositionMs ?: if (isPlaying && abs(clockPositionMs - currentPositionMs) <= LyricsPlaybackTickMs) {
        clockPositionMs
    } else {
        currentPositionMs
    }
}

@Composable
private fun SyncedLyricsList(
    trackId: String,
    lines: List<PlayerLyricLine>,
    currentPositionMs: Long,
    isPlaying: Boolean,
    pendingSeekPositionMs: Long?,
    seekRequestId: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
    secondary: List<String?>,
    showToggle: Boolean,
    glowEnabled: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val motionEnabled = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
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
    val displayedPositionMs = rememberLyricsDisplayPosition(trackId, currentPositionMs, pendingSeekPositionMs, isPlaying)
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
                animationSpec = tween(LyricsMotionDurationMs, easing = FastOutSlowInEasing),
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
                            animationSpec = tween(LyricsMotionDurationMs, easing = FastOutSlowInEasing),
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
                        animationSpec = tween(LyricsMotionDurationMs, easing = FastOutSlowInEasing),
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
    LaunchedEffect(secondary) {
        // Let row/text measurements settle without resetting browse or tap-to-seek state.
        withFrameNanos { }
        withFrameNanos { }
        if (hasPositionedInitialLine && !isBrowsing && selectedLineIndex == null && activeIndex >= 0 &&
            listState.layoutInfo.visibleItemsInfo.any { it.index == activeIndex }
        ) positionInitialLine(activeIndex)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = if (showToggle) 72.dp else 0.dp),
        userScrollEnabled = true,
        modifier = modifier.testTag("synced_lyrics_list"),
    ) {
        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
            SyncedLyricRow(
                line = line,
                secondary = secondary.getOrNull(index) ?: line.secondary,
                distance = if (activeIndex >= 0) kotlin.math.abs(index - activeIndex) else Int.MAX_VALUE,
                karaokePositionSeconds = if (!isBrowsing && index == activeIndex) displayedPositionMs / 1_000f else null,
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
                motionEnabled = motionEnabled && glowEnabled,
            )
        }
    }
}

@Composable
private fun SyncedLyricRow(
    line: PlayerLyricLine,
    secondary: String?,
    distance: Int,
    karaokePositionSeconds: Float?,
    onClick: () -> Unit,
    onRowHeightChanged: (Int) -> Unit,
    onTrailingLineHeightChanged: (Int) -> Unit,
    focusMode: Boolean,
    motionEnabled: Boolean,
) {
    val colors = LocalAirmedyColors.current
    // The pointer coroutine remains alive across playback-position and track
    // recompositions. Read the newest callback when a tap finishes so it
    // cannot dispatch through the callback captured for an earlier track.
    val latestOnClick = rememberUpdatedState(onClick)
    val enhanced = line.words != null
    val targetOpacity = if (!focusMode) {
        1f
    } else when (distance) {
        0 -> 1f
        1 -> 0.25f
        2 -> 0.15f
        else -> 0.10f
    }
    val targetBlur = if (focusMode) syncedLyricBlurRadius(distance) else 0.dp
    val animatedBlur by animateDpAsState(targetBlur, tween(LyricsMotionDurationMs, easing = FastOutSlowInEasing), label = "synced-lyric-blur")
    val scale by animateFloatAsState(syncedLyricScale(distance, focusMode, enhanced), tween(LyricsMotionDurationMs, easing = FastOutSlowInEasing), label = "synced-lyric-scale")
    val activeOffsetPx = with(LocalDensity.current) { 4.dp.toPx() }
    val lyricTapSlopPx = with(LocalDensity.current) { 20.dp.toPx() }
    val animatedTranslationY by animateFloatAsState(
        if (!enhanced && focusMode && distance == 0) -activeOffsetPx else 0f,
        if (enhanced) snap() else tween(LyricsMotionDurationMs, easing = FastOutSlowInEasing),
        label = "synced-lyric-offset",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Reserve room for active-line scaling without changing wrapping
            // only when the active state changes.
            .padding(top = 10.dp, bottom = 10.dp, end = 16.dp)
            // Transform after text layout so the active line grows subtly
            // without changing its wrapping or displacing adjacent lyrics.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = animatedTranslationY
                transformOrigin = TransformOrigin(0f, 0.5f)
                clip = false
            }
            // Blur at the original size, then scale the result. An outer blur
            // layer would crop the enlarged text to its offscreen buffer.
            .blur(animatedBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
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
        KaraokeLyricText(
            line = line,
            positionSeconds = karaokePositionSeconds,
            color = colors.onPrimary,
            targetOpacity = targetOpacity,
            motionEnabled = motionEnabled,
            onTextLayout = { layout ->
                if (secondary == null) onTrailingLineHeightChanged((layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(layout.lineCount - 1)).roundToInt())
            },
        )
        secondary?.let {
            Text(
                text = it,
                color = colors.foregroundSubtle.copy(alpha = syncedLyricOpacity(targetOpacity)),
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
private fun syncedLyricOpacity(targetOpacity: Float): Float {
    val opacity by animateFloatAsState(
        targetOpacity,
        tween(LyricsMotionDurationMs, easing = FastOutSlowInEasing),
        label = "synced-lyric-opacity",
    )
    return opacity
}

internal data class KaraokeLinePresentation(
    val positionSeconds: Float,
    val sungOpacity: Float,
    val unsungOpacity: Float,
)

@Composable
internal fun rememberKaraokeLinePresentation(positionSeconds: Float?, targetOpacity: Float): KaraokeLinePresentation {
    val position = remember { Animatable(positionSeconds ?: 0f) }
    LaunchedEffect(positionSeconds) {
        if (positionSeconds != null) {
            // The list clock already advances between playback samples. Keep
            // the last position only for the outgoing row's opacity fade.
            position.snapTo(positionSeconds)
        }
    }
    return KaraokeLinePresentation(
        positionSeconds = positionSeconds ?: position.value,
        sungOpacity = syncedLyricOpacity(targetOpacity),
        unsungOpacity = syncedLyricOpacity(if (positionSeconds != null) targetOpacity * .35f else targetOpacity),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KaraokeLyricText(
    line: PlayerLyricLine,
    positionSeconds: Float?,
    color: androidx.compose.ui.graphics.Color,
    targetOpacity: Float,
    motionEnabled: Boolean,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit,
) {
    val words = line.words
    if (words == null) {
        Text(
            text = line.primary,
            color = color.copy(alpha = syncedLyricOpacity(targetOpacity)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            onTextLayout = onTextLayout,
        )
        return
    }
    val presentation = rememberKaraokeLinePresentation(positionSeconds, targetOpacity)
    FlowRow {
        words.forEachIndexed { index, word ->
            KaraokeWord(word, presentation, color, index, positionSeconds != null && motionEnabled, onTextLayout)
        }
    }
}

@Composable
private fun KaraokeWord(
    word: PlayerLyricWord,
    presentation: KaraokeLinePresentation,
    color: androidx.compose.ui.graphics.Color,
    index: Int,
    motionEnabled: Boolean,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit,
) {
    val progress = karaokeWordProgress(word, presentation.positionSeconds)
    val glow = if (motionEnabled) karaokeWordGlow(word, presentation.positionSeconds) else 0f
    var textLayout by remember(word.text) { mutableStateOf<TextLayoutResult?>(null) }
    val fragments = remember(textLayout) { textLayout?.let(::karaokeWordFragments) }
    Box(Modifier.testTag("karaoke_word_$index")) {
        if (glow > 0f && progress > 0f) {
            Text(
                text = word.text,
                color = color.copy(alpha = glow * .72f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .testTag("karaoke_glow_$index")
                    .blur(with(LocalDensity.current) { MaterialTheme.typography.headlineSmall.fontSize.toDp() * .20f }, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .karaokeWordClip(fragments, progress, bright = true),
            )
        }
        Text(
            text = word.text,
            color = color.copy(alpha = presentation.unsungOpacity),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            onTextLayout = { layout -> textLayout = layout; onTextLayout(layout) },
            // Keep the two regions disjoint so translucent text never doubles in brightness.
            modifier = Modifier.karaokeWordClip(fragments, progress, bright = false),
        )
        Text(
            text = word.text,
            color = color.copy(alpha = presentation.sungOpacity),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.karaokeWordClip(fragments, progress, bright = true),
        )
    }
}

private data class KaraokeWordFragment(
    val left: Float,
    val width: Float,
    val top: Float,
    val bottom: Float,
    val offset: Float,
    val total: Float,
)

private fun karaokeWordFragments(layout: TextLayoutResult): List<KaraokeWordFragment> {
    val widths = (0 until layout.lineCount).map { (layout.getLineRight(it) - layout.getLineLeft(it)).coerceAtLeast(0f) }
    val total = widths.sum()
    var offset = 0f
    return widths.mapIndexed { index, width ->
        val fragment = KaraokeWordFragment(
            left = layout.getLineLeft(index), width = width,
            top = layout.getLineTop(index), bottom = layout.getLineBottom(index),
            offset = offset, total = total,
        )
        offset += width
        fragment
    }
}

private fun Modifier.karaokeWordClip(fragments: List<KaraokeWordFragment>?, progress: Float, bright: Boolean): Modifier =
    drawWithContent {
        if (fragments == null || fragments.isEmpty() || fragments.first().total <= 0f) {
            val split = size.width * progress
            if (bright) clipRect(right = split) { this@drawWithContent.drawContent() }
            else clipRect(left = split) { this@drawWithContent.drawContent() }
        } else {
            for (fragment in fragments) {
                val split = fragment.left + karaokeFragmentFill(progress, fragment.offset, fragment.width, fragment.total)
                if (bright) {
                    clipRect(left = fragment.left, right = split, top = fragment.top, bottom = fragment.bottom) {
                        this@drawWithContent.drawContent()
                    }
                } else {
                    clipRect(left = split, right = fragment.left + fragment.width, top = fragment.top, bottom = fragment.bottom) {
                        this@drawWithContent.drawContent()
                    }
                }
            }
        }
    }

internal fun karaokeFragmentFill(progress: Float, offset: Float, width: Float, total: Float): Float =
    (progress * total - offset).coerceIn(0f, width)

internal fun karaokeWordProgress(word: PlayerLyricWord, positionSeconds: Float): Float = when {
    word.endSeconds == word.startSeconds -> if (positionSeconds >= word.startSeconds) 1f else 0f
    else -> ((positionSeconds - word.startSeconds) / (word.endSeconds - word.startSeconds)).coerceIn(0f, 1f)
}

private fun karaokeSmooth(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun karaokeWordGlow(word: PlayerLyricWord, positionSeconds: Float): Float {
    val duration = word.endSeconds - word.startSeconds
    if (duration <= 0f) return 0f
    return karaokeSmooth((positionSeconds - word.startSeconds) / minOf(.18f, duration)) *
        (1f - karaokeSmooth((positionSeconds - word.endSeconds) / .5f))
}

@Composable
private fun PlainLyricsList(lines: List<PlayerLyricLine>, modifier: Modifier, secondary: List<String?>, showToggle: Boolean) {
    val colors = LocalAirmedyColors.current
    LazyColumn(modifier = modifier.testTag("plain_lyrics_list"), contentPadding = PaddingValues(bottom = if (showToggle) 72.dp else 0.dp)) {
        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(text = line.primary, color = colors.onPrimary, style = MaterialTheme.typography.bodyLarge)
                (secondary.getOrNull(index) ?: line.secondary)?.let {
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
