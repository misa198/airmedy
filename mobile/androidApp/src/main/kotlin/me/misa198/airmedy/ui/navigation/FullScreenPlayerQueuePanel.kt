package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.RepeatMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.AirmedyPlayingIndicator
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import me.misa198.airmedy.ui.components.TrackContextMenu
import me.misa198.airmedy.ui.components.TrackContextMenuActions
import me.misa198.airmedy.ui.components.TrackContextArtist
import me.misa198.airmedy.ui.components.rememberArtworkThumbnail
import me.misa198.airmedy.ui.components.sliderFilledTrackColor
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Queue-specific state and drag handling are isolated from the player shell. */
@Composable
internal fun FullScreenQueuePanel(
    queue: PlaybackQueueSnapshot,
    tracks: List<LibraryTrack>,
    currentTrackId: String,
    isPlaying: Boolean,
    onTrackSelected: (String) -> Unit,
    onTrackRemoved: (String) -> Unit = {},
    onTrackPlayNext: (String) -> Unit = {},
    onReorder: (List<String>) -> Unit,
    onReorderDragStateChange: (Boolean) -> Unit = {},
    onShuffleChange: (Boolean) -> Unit,
    onRepeatModeChange: (RepeatMode) -> Unit,
    onFavoriteChange: (String, Boolean) -> Unit = { _, _ -> },
    onTrackGoToAlbum: (String) -> Unit = {},
    onTrackGoToArtist: (String) -> Unit = {},
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
    onCloseFullscreenThen: ((() -> Unit) -> Unit) = { action -> action() },
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val listState = rememberLazyListState()
    val tracksById = remember(tracks) { tracks.associateBy(LibraryTrack::id) }
    var orderedIds by remember(queue.activeTrackIds) { mutableStateOf(queue.activeTrackIds) }
    // Reorderable's long-press modifier keeps its gesture callbacks for the
    // duration of a drag. Keep state holders here so its stop callback commits
    // the final local order, rather than the order from drag start.
    val latestOrderedIds = rememberUpdatedState(orderedIds)
    val latestOnReorder = rememberUpdatedState(onReorder)
    var hasPositionedInitialTrack by remember { mutableStateOf(false) }
    var previousCurrentTrackId by remember { mutableStateOf<String?>(null) }
    var contextTrackId by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index != to.index) {
            orderedIds = moveQueueTrack(orderedIds, from.index, to.index)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    val repeatLabel = stringResource(
        when (queue.repeatMode) {
            RepeatMode.Off -> R.string.player_repeat_off
            RepeatMode.All -> R.string.player_repeat_all
            RepeatMode.One -> R.string.player_repeat_one
        },
    )

    // Reordering must preserve the listener's viewport. Only playback moving
    // to a different current track is allowed to follow that track in the list.
    LaunchedEffect(currentTrackId) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = QueuePanelRowHorizontalPadding)
                .semantics { testTag = QueuePanelHeaderTestTag },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.player_queue),
                color = colors.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            PlayerModeButton(MaterialSymbols.Shuffle, stringResource(if (queue.shuffle) R.string.player_shuffle_on else R.string.player_shuffle), queue.shuffle) {
                onShuffleChange(!queue.shuffle)
            }
            Spacer(Modifier.width(8.dp))
            PlayerModeButton(if (queue.repeatMode == RepeatMode.One) MaterialSymbols.RepeatOne else MaterialSymbols.Repeat, repeatLabel, queue.repeatMode != RepeatMode.Off) {
                onRepeatModeChange(queue.repeatMode.next())
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(orderedIds, key = { it }, contentType = { "queue-track" }) { trackId ->
                ReorderableItem(reorderableState, key = trackId) { isDragging ->
                    val track = tracksById[trackId]
                    if (track == null) {
                        FullScreenQueueTrackRow(
                            track = null,
                            trackId = trackId,
                            isCurrent = trackId == currentTrackId,
                            isPlaying = isPlaying,
                            isDragged = isDragging,
                            modifier = Modifier,
                            dragHandleModifier = queueDragHandleModifier(
                                haptics,
                                onReorderDragStateChange,
                                onReorder = { commitQueueReorder(latestOrderedIds, latestOnReorder) },
                            ),
                            onClick = { onTrackSelected(trackId) },
                        )
                    } else {
                        TrackContextMenu(
                            track = track,
                            expanded = contextTrackId == trackId,
                            onDismiss = { contextTrackId = null },
                            actions = queueTrackContextMenuActions(isCurrent = trackId == currentTrackId),
                            hazeState = hazeState,
                            playbackQueue = queue,
                            onRemoveFromQueue = { onTrackRemoved(it.id) },
                            onPlayNext = { onTrackPlayNext(it.id) },
                            onFavoriteChange = { contextTrack, favorite -> onFavoriteChange(contextTrack.id, favorite) },
                            onGoToAlbum = { onTrackGoToAlbum(it.albumId) },
                            onGoToArtist = { artist: TrackContextArtist -> onTrackGoToArtist(artist.id) },
                            onBottomSheetRequested = { request ->
                                onCloseFullscreenThen { onTrackContextBottomSheet(request) }
                            },
                            onCloseFullscreenThen = onCloseFullscreenThen,
                        ) {
                            FullScreenQueueTrackRow(
                                track = track,
                                trackId = trackId,
                                isCurrent = trackId == currentTrackId,
                                isPlaying = isPlaying,
                                isDragged = isDragging,
                                modifier = Modifier,
                                dragHandleModifier = queueDragHandleModifier(
                                    haptics,
                                    onReorderDragStateChange,
                                    onReorder = { commitQueueReorder(latestOrderedIds, latestOnReorder) },
                                ),
                                onClick = { onTrackSelected(trackId) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    contextTrackId = trackId
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ReorderableCollectionItemScope.queueDragHandleModifier(
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onReorderDragStateChange: (Boolean) -> Unit,
    onReorder: () -> Unit,
) = Modifier.longPressDraggableHandle(
    onDragStarted = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onReorderDragStateChange(true)
    },
    onDragStopped = {
        onReorderDragStateChange(false)
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onReorder()
    },
)

@Composable
internal fun PlayerModeButton(symbol: String, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAirmedyColors.current
    val backgroundColor by animateColorAsState(
        targetValue = if (active) {
            sliderFilledTrackColor(colors, isInteracting = false)
        } else {
            fullScreenSecondaryControlBackground(colors)
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "queue-mode-background",
    )
    val iconColor by animateColorAsState(if (active) colors.playerBackdrop.copy(alpha = 0.72f) else colors.onPrimary, tween(220, easing = FastOutSlowInEasing), label = "queue-mode-icon")
    Box(Modifier.width(72.dp).height(48.dp).semantics { contentDescription = label; selected = active }.clickable(onClick = onClick, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null), contentAlignment = Alignment.Center) {
        Box(Modifier.width(72.dp).height(36.dp).clip(CircleShape).background(backgroundColor).border(1.dp, colors.borderGlass, CircleShape), contentAlignment = Alignment.Center) {
            MaterialSymbol(symbol = symbol, contentDescription = null, tint = iconColor, size = 22.dp)
        }
    }
}

@Composable
private fun FullScreenQueueTrackRow(
    track: LibraryTrack?,
    trackId: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragged: Boolean,
    modifier: Modifier,
    dragHandleModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LocalAirmedyColors.current
    val artwork = rememberArtworkThumbnail(track?.artworkPath)
    val title = track?.title ?: stringResource(R.string.player_queue_unknown_track)
    val artist = track?.artists.orEmpty()
    val dragHandleLabel = stringResource(R.string.player_queue_drag_handle)
    val currentLabel = stringResource(R.string.player_queue_current)
    val dragHandleWidthPx = with(LocalDensity.current) { QueueDragHandleWidth.toPx() }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val hoverBackground by animateColorAsState(
        targetValue = if (isHovered) {
            fullScreenSecondaryControlBackground(colors)
        } else {
            colors.sliderInactive.copy(alpha = 0f)
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "queue-row-hover-background",
    )
    Box(
        modifier.fillMaxWidth()
            .height(56.dp)
            .then(
                if (isDragged) {
                    // Match the Favorite control's translucent glass rather
                    // than using the opaque elevated surface, with a stronger
                    // opacity so the dragged row remains legible. The
                    // full-width row intentionally has square screen-edge
                    // corners.
                    Modifier
                        .background(queueDraggedRowBackground(colors))
                        .border(1.dp, colors.borderGlass)
                } else {
                    Modifier.background(hoverBackground)
                },
            )
            .semantics { if (isCurrent) contentDescription = currentLabel }
            .semantics { testTag = "$QueuePanelRowTestTag-$trackId" }
            .hoverable(interactionSource)
            .pointerInput(onClick, onLongClick, dragHandleWidthPx) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { position ->
                        if (shouldOpenQueueTrackContextMenu(position.x, size.width, dragHandleWidthPx)) {
                            onLongClick?.invoke()
                        }
                    },
                )
            }
            .semantics {
                role = Role.Button
                onClick { onClick(); true }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = QueuePanelRowHorizontalPadding)
                .semantics { testTag = "$QueuePanelRowContentTestTag-$trackId" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(colors.glassElevated).border(1.dp, colors.borderGlass, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
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
            Box(
                Modifier
                    .size(QueueDragHandleWidth)
                    .then(dragHandleModifier)
                    .semantics { contentDescription = dragHandleLabel },
                contentAlignment = Alignment.CenterEnd,
            ) {
                MaterialSymbol(MaterialSymbols.Menu, tint = colors.sliderInactive, size = 24.dp)
            }
        }
    }
}

// Keep the glyph at the row edge but give its long-press gesture a forgiving
// target, so reordering does not require hitting the icon precisely.
private val QueueDragHandleWidth = 72.dp
private val QueuePanelRowHorizontalPadding = 20.dp
private const val QueuePanelHeaderTestTag = "full_screen_queue_panel_header"
private const val QueuePanelRowTestTag = "full_screen_queue_row"
private const val QueuePanelRowContentTestTag = "full_screen_queue_row_content"

internal fun queueTrackContextMenuActions(isCurrent: Boolean) = TrackContextMenuActions(
    removeFromQueue = !isCurrent,
    addToQueue = false,
)

internal fun shouldOpenQueueTrackContextMenu(
    longPressX: Float,
    rowWidthPx: Int,
    dragHandleWidthPx: Float,
): Boolean = longPressX < rowWidthPx - dragHandleWidthPx

private fun queueDraggedRowBackground(colors: me.misa198.airmedy.ui.theme.AirmedyColors) =
    colors.sliderInactive.copy(alpha = 0.20f)

internal fun moveQueueTrack(trackIds: List<String>, fromIndex: Int, toIndex: Int): List<String> =
    trackIds.toMutableList().apply {
        if (fromIndex in indices && toIndex in indices && fromIndex != toIndex) {
            add(toIndex, removeAt(fromIndex))
        }
    }

/** Commits the latest Compose-backed local order when a reorder drag ends. */
internal fun commitQueueReorder(
    latestOrderedIds: androidx.compose.runtime.State<List<String>>,
    latestOnReorder: androidx.compose.runtime.State<(List<String>) -> Unit>,
) = latestOnReorder.value(latestOrderedIds.value)

private fun RepeatMode.next(): RepeatMode = when (this) {
    RepeatMode.Off -> RepeatMode.All
    RepeatMode.All -> RepeatMode.One
    RepeatMode.One -> RepeatMode.Off
}
