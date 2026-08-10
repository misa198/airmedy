package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.RepeatMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.AirmedyPlayingIndicator
import me.misa198.airmedy.ui.components.rememberArtworkThumbnail
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** Queue-specific state and drag handling are isolated from the player shell. */
@Composable
internal fun FullScreenQueuePanel(
    queue: PlaybackQueueSnapshot,
    tracks: List<LibraryTrack>,
    currentTrackId: String,
    isPlaying: Boolean,
    onTrackSelected: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatModeChange: (RepeatMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val listState = rememberLazyListState()
    val tracksById = remember(tracks) { tracks.associateBy(LibraryTrack::id) }
    var orderedIds by remember(queue.activeTrackIds) { mutableStateOf(queue.activeTrackIds) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var draggedOffset by remember { mutableStateOf(0f) }
    var hasPositionedInitialTrack by remember { mutableStateOf(false) }
    var previousCurrentTrackId by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val reorderThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    val repeatLabel = stringResource(
        when (queue.repeatMode) {
            RepeatMode.Off -> R.string.player_repeat_off
            RepeatMode.All -> R.string.player_repeat_all
            RepeatMode.One -> R.string.player_repeat_one
        },
    )

    LaunchedEffect(currentTrackId, orderedIds) {
        val targetIndex = orderedIds.indexOf(currentTrackId)
        targetIndex
            .takeIf { it >= 0 }
            ?.let { index ->
                if (!hasPositionedInitialTrack) {
                    listState.scrollToItem(index)
                    hasPositionedInitialTrack = true
                } else {
                    val previousIndex = previousCurrentTrackId?.let(orderedIds::indexOf)
                    val isFollowingCurrentTrack = previousIndex != null &&
                        listState.layoutInfo.visibleItemsInfo.any { it.index == previousIndex }
                    if (isFollowingCurrentTrack) listState.animateScrollToItem(index)
                }
                previousCurrentTrackId = currentTrackId
            }
    }

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.player_queue),
                color = colors.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            QueueModeButton(MaterialSymbols.Shuffle, stringResource(if (queue.shuffle) R.string.player_shuffle_on else R.string.player_shuffle), queue.shuffle) {
                onShuffleChange(!queue.shuffle)
            }
            Spacer(Modifier.width(8.dp))
            QueueModeButton(if (queue.repeatMode == RepeatMode.One) MaterialSymbols.RepeatOne else MaterialSymbols.Repeat, repeatLabel, queue.repeatMode != RepeatMode.Off) {
                onRepeatModeChange(queue.repeatMode.next())
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(orderedIds, key = { it }, contentType = { "queue-track" }) { trackId ->
                val index = orderedIds.indexOf(trackId)
                FullScreenQueueTrackRow(
                    track = tracksById[trackId], trackId = trackId,
                    isCurrent = trackId == currentTrackId,
                    isPlaying = isPlaying,
                    dragOffset = if (draggedIndex == index) draggedOffset else 0f,
                    onClick = { onTrackSelected(trackId) },
                    onDragStart = {
                        draggedIndex = orderedIds.indexOf(trackId)
                        draggedOffset = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { dragAmount ->
                        val currentIndex = draggedIndex ?: return@FullScreenQueueTrackRow
                        draggedOffset += dragAmount
                        if (abs(draggedOffset) >= reorderThresholdPx) {
                            val targetIndex = (currentIndex + if (draggedOffset > 0) 1 else -1).coerceIn(0, orderedIds.lastIndex)
                            if (targetIndex != currentIndex) {
                                orderedIds = orderedIds.toMutableList().apply { add(targetIndex, removeAt(currentIndex)) }
                                draggedIndex = targetIndex
                                draggedOffset -= if (draggedOffset > 0) reorderThresholdPx else -reorderThresholdPx
                            }
                        }
                    },
                    onDragEnd = {
                        if (draggedIndex != null) onReorder(orderedIds)
                        draggedIndex = null
                        draggedOffset = 0f
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueModeButton(symbol: String, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAirmedyColors.current
    val backgroundColor by animateColorAsState(if (active) colors.foregroundSubtle else Color.White.copy(alpha = 0.06f), tween(220, easing = FastOutSlowInEasing), label = "queue-mode-background")
    val iconColor by animateColorAsState(if (active) colors.playerBackdrop.copy(alpha = 0.72f) else colors.onPrimary, tween(220, easing = FastOutSlowInEasing), label = "queue-mode-icon")
    Box(Modifier.width(72.dp).height(48.dp).semantics { contentDescription = label; selected = active }.clickable(onClick = onClick, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null), contentAlignment = Alignment.Center) {
        Box(Modifier.width(72.dp).height(36.dp).clip(CircleShape).background(backgroundColor).border(1.dp, colors.borderGlass, CircleShape), contentAlignment = Alignment.Center) {
            MaterialSymbol(symbol = symbol, contentDescription = null, tint = iconColor, size = 22.dp)
        }
    }
}

@Composable
private fun FullScreenQueueTrackRow(track: LibraryTrack?, trackId: String, isCurrent: Boolean, isPlaying: Boolean, dragOffset: Float, onClick: () -> Unit, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val colors = LocalAirmedyColors.current
    val artwork = rememberArtworkThumbnail(track?.artworkPath)
    val title = track?.title ?: stringResource(R.string.player_queue_unknown_track)
    val artist = track?.artists.orEmpty()
    val menuLabel = stringResource(R.string.player_more)
    val currentLabel = stringResource(R.string.player_queue_current)
    Row(
        Modifier.fillMaxWidth().height(56.dp).offset { IntOffset(0, dragOffset.roundToInt()) }.clip(RoundedCornerShape(12.dp))
            .semantics { if (isCurrent) contentDescription = currentLabel }
            .clickable(onClick = onClick, interactionSource = remember { MutableInteractionSource() }, indication = null),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(colors.glassElevated), contentAlignment = Alignment.Center) {
            if (artwork != null) Image(artwork, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else MaterialSymbol(MaterialSymbols.MusicNote, tint = colors.textMuted, size = 20.dp)
            if (isCurrent) {
                Box(Modifier.fillMaxSize().background(colors.playerBackdrop.copy(alpha = 0.64f)), contentAlignment = Alignment.Center) {
                    AirmedyPlayingIndicator(isPlaying = isPlaying)
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Text(text = title, color = colors.onPrimary, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = artist, color = colors.foregroundSubtle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(48.dp).semantics { contentDescription = menuLabel }.pointerInput(trackId) {
            detectDragGesturesAfterLongPress(onDragStart = { onDragStart() }, onDragCancel = onDragEnd, onDragEnd = onDragEnd) { change, amount ->
                change.consume(); onDrag(amount.y)
            }
        }, contentAlignment = Alignment.CenterEnd) {
            MaterialSymbol(MaterialSymbols.Menu, tint = colors.textMuted, size = 24.dp)
        }
    }
}

private fun RepeatMode.next(): RepeatMode = when (this) {
    RepeatMode.Off -> RepeatMode.All
    RepeatMode.All -> RepeatMode.One
    RepeatMode.One -> RepeatMode.Off
}
