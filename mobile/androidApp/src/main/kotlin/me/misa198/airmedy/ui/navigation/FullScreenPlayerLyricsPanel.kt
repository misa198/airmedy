package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.MaterialSymbols
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

internal fun parsePlayerLyrics(content: String): List<PlayerLyricLine> = content.lineSequence()
    .mapNotNull { rawLine ->
        val match = TimestampedLyricLine.matchEntire(rawLine)
        val timestamp = match?.let { it.groupValues[1].toFloat() * 60f + it.groupValues[2].toFloat() }
        val text = match?.groupValues?.get(3) ?: rawLine
        text.trim().takeIf(String::isNotEmpty)?.let { parsePlayerLyricText(it, timestamp) }
    }
    .toList()

internal fun hasSyncedPlayerLyrics(content: String?): Boolean = content != null && parsePlayerLyrics(content).any { it.timestampSeconds != null }

private fun parsePlayerLyricText(text: String, timestampSeconds: Float?): PlayerLyricLine {
    val parts = BilingualSeparator.split(text, limit = 2)
    val secondary = parts.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty)
    return PlayerLyricLine(primary = parts.first().trim(), secondary = secondary, timestampSeconds = timestampSeconds)
}

@Composable
internal fun FullScreenPlayerLyricsPanel(
    lyrics: String?,
    currentPositionMs: Long,
    syncedLyricsEnabled: Boolean,
    onSyncedLyricsEnabledChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val parsedLines = remember(lyrics) { lyrics?.let(::parsePlayerLyrics).orEmpty() }
    val syncedLines = remember(parsedLines) { parsedLines.filter { it.timestampSeconds != null } }
    val supportsSyncedLyrics = syncedLines.isNotEmpty()
    val showSyncedLyrics = supportsSyncedLyrics && syncedLyricsEnabled

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.player_lyrics),
                color = colors.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (supportsSyncedLyrics) {
                PlayerModeButton(
                    symbol = MaterialSymbols.Timer,
                    label = stringResource(if (syncedLyricsEnabled) R.string.player_synced_lyrics_on else R.string.player_synced_lyrics_off),
                    active = syncedLyricsEnabled,
                    onClick = { onSyncedLyricsEnabledChange(!syncedLyricsEnabled) },
                )
            } else {
                // Keep the Lyrics title aligned with Queue even when this
                // track has no synchronized lyric mode to toggle.
                Spacer(Modifier.width(72.dp).height(48.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            lyrics.isNullOrBlank() -> LyricsEmptyState(Modifier.fillMaxSize())
            showSyncedLyrics -> SyncedLyricsList(syncedLines, currentPositionMs, onSeek, Modifier.fillMaxSize())
            else -> PlainLyricsList(parsedLines, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SyncedLyricsList(
    lines: List<PlayerLyricLine>,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val rowHeights = remember(lines) { mutableStateMapOf<Int, Int>() }
    val trailingLineHeights = remember(lines) { mutableStateMapOf<Int, Int>() }
    val rowBottomPaddingPx = with(LocalDensity.current) { 10.dp.roundToPx() }
    var hasPositionedInitialLine by remember(lines) { mutableStateOf(false) }
    var previousActiveIndex by remember(lines) { mutableStateOf<Int?>(null) }
    var isBrowsing by remember(lines) { mutableStateOf(false) }
    var isAutoScrolling by remember { mutableStateOf(false) }
    val activeIndex = remember(lines, currentPositionMs) {
        lines.indexOfLast { (it.timestampSeconds ?: Float.MAX_VALUE) <= currentPositionMs / 1_000f }
    }
    LaunchedEffect(listState.isScrollInProgress, isAutoScrolling) {
        if (listState.isScrollInProgress && !isAutoScrolling) isBrowsing = true
    }
    LaunchedEffect(lines, activeIndex, isBrowsing) {
        if (activeIndex < 0 || isBrowsing) return@LaunchedEffect
        val previousIndex = (activeIndex - 1).coerceAtLeast(0)
        suspend fun previousLineOffset(): Int = if (previousIndex < activeIndex) {
            snapshotFlow { rowHeights[previousIndex] to trailingLineHeights[previousIndex] }
                .first { (rowHeight, trailingLineHeight) -> rowHeight != null && trailingLineHeight != null }
                .let { (rowHeight, trailingLineHeight) ->
                    (rowHeight!! - trailingLineHeight!! - rowBottomPaddingPx).coerceAtLeast(0)
                }
        } else {
            0
        }

        suspend fun positionInitialLine() {
            listState.scrollToItem(previousIndex)
            listState.scrollToItem(previousIndex, previousLineOffset())
        }

        suspend fun animateToActiveLine() {
            isAutoScrolling = true
            try {
                val offset = previousLineOffset()
                val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == previousIndex }
                if (target != null) {
                    listState.animateScrollBy(
                        (target.offset + offset).toFloat(),
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                    )
                } else {
                    listState.animateScrollToItem(previousIndex)
                    listState.animateScrollToItem(previousIndex, offset)
                }
            } finally {
                isAutoScrolling = false
            }
        }

        if (!hasPositionedInitialLine) {
            positionInitialLine()
            hasPositionedInitialLine = true
        } else {
            val previousActiveLineInViewport = previousActiveIndex
                ?.let { index -> listState.layoutInfo.visibleItemsInfo.any { it.index == index } }
                ?: false
            if (previousActiveLineInViewport) animateToActiveLine()
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
                    hasPositionedInitialLine = false
                    isBrowsing = false
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
            .clickable(
                onClick = onClick,
                interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                indication = null,
            )
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
