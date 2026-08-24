package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.ArtworkHeroBackdrop
import me.misa198.airmedy.ui.components.DetailHero
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.PlaylistArtwork
import me.misa198.airmedy.ui.components.TrackRow
import me.misa198.airmedy.ui.components.TrackContextMenu
import me.misa198.airmedy.ui.components.TrackContextMenuActions
import me.misa198.airmedy.ui.components.TrackContextArtist
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import me.misa198.airmedy.ui.components.PlaylistContextMenu
import me.misa198.airmedy.ui.components.AirmedyDialog
import me.misa198.airmedy.ui.components.AirmedyPillButtonVariant
import me.misa198.airmedy.ui.components.trackInfoArtworkSize
import me.misa198.airmedy.ui.components.liquidGlassBackground
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val PlaylistTrackDividerTag = "playlist-detail-track-divider"

@Composable
internal fun PlaylistDetailsContent(
    uiState: PlaylistDetailsUiState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    hazeState: HazeState? = null,
    onHeroColorChanged: (Color) -> Unit = {},
    onPlay: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onTrackClick: (String) -> Unit = {},
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    onTrackPlayNext: (String) -> Unit = {},
    onTrackAddToQueue: (String) -> Unit = {},
    onTrackFavoriteToggle: (String, Boolean) -> Unit = { _, _ -> },
    onTrackArtistClick: (TrackContextArtist) -> Unit = {},
    onTrackRemoveFromPlaylist: (String) -> Unit = {},
    onTrackMove: (String, String?, String?) -> Unit = { _, _, _ -> },
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
    onPlaylistPlayNext: (List<String>) -> Unit = {},
    onPlaylistAddToQueue: (List<String>) -> Unit = {},
    onPlaylistUpdate: (String, String, android.net.Uri?, Boolean) -> Unit = { _, _, _, _ -> },
    onPlaylistDelete: (String) -> Unit = {},
) {
    val playlist = uiState.playlist
    if (playlist == null) {
        HeroCard(
            symbol = MaterialSymbols.QueueMusic,
            title = stringResource(R.string.playlist_details_empty_title),
            description = stringResource(R.string.playlist_details_empty_description),
            modifier = modifier.fillMaxSize().padding(contentPadding),
        )
        return
    }
    val colors = LocalAirmedyColors.current
    var contextTrack by remember(playlist.id) { mutableStateOf<String?>(null) }
    var playlistMenuExpanded by remember(playlist.id) { mutableStateOf(false) }
    var showPlaylistEditor by remember(playlist.id) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(playlist.id) { mutableStateOf(false) }
    var isReordering by remember(playlist.id) { mutableStateOf(false) }
    var orderedTrackIds by remember(playlist.id) { mutableStateOf(uiState.tracks.map { it.id }) }
    var draggedTrackId by remember { mutableStateOf<String?>(null) }
    var draggedTrackInitialIndex by remember { mutableStateOf(-1) }
    val latestOrderedTrackIds = rememberUpdatedState(orderedTrackIds)
    val latestOnTrackMove = rememberUpdatedState(onTrackMove)
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index != to.index) {
            orderedTrackIds = movePlaylistTrack(orderedTrackIds, playlistTrackIndex(from.index), playlistTrackIndex(to.index))
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    val context = LocalContext.current
    val count = pluralStringResource(R.plurals.playlist_details_track_count, uiState.tracks.size, uiState.tracks.size)
    val totalDurationSeconds = remember(uiState.tracks) { playlistTotalDurationSeconds(uiState.tracks) }
    val duration = formatPlaylistTotalDuration(
        totalDurationSeconds,
        day = { context.getString(R.string.playlist_duration_day, it) },
        hour = { context.getString(R.string.playlist_duration_hour, it) },
        minute = { context.getString(R.string.playlist_duration_minute, it) },
        second = { context.getString(R.string.playlist_duration_second, it) },
    )
    val name = if (playlist.id == FavoritesPlaylistId) stringResource(R.string.library_favorites) else playlist.name
    val trackIds = remember(uiState.tracks) { uiState.tracks.map { it.id } }
    val tracksById = remember(uiState.tracks) { uiState.tracks.associateBy { it.id } }
    androidx.compose.runtime.LaunchedEffect(uiState.tracks, isReordering) {
        if (!isReordering) orderedTrackIds = uiState.tracks.map { it.id }
    }
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        item("hero") {
            ArtworkHeroBackdrop(uiState.artworkPaths.firstOrNull(), Modifier.fillMaxWidth(), onHeroColorChanged) {
                DetailHero(
                    title = name,
                    metadata = stringResource(R.string.playlist_details_metadata, count, duration),
                    playLabel = stringResource(R.string.player_play),
                    shuffleLabel = stringResource(R.string.player_shuffle),
                    moreLabel = stringResource(if (isReordering) R.string.playlist_reorder_done else R.string.album_row_more_options),
                    moreSymbol = if (isReordering) MaterialSymbols.Check else MaterialSymbols.MoreVert,
                    modifier = Modifier.fillMaxWidth().padding(
                        start = 24.dp,
                        top = contentPadding.calculateTopPadding(),
                        end = 24.dp,
                        bottom = 20.dp,
                    ),
                    artworkSize = trackInfoArtworkSize,
                    fallbackSymbol = if (playlist.id == FavoritesPlaylistId) MaterialSymbols.Favorite else MaterialSymbols.QueueMusic,
                    artworkContent = {
                        PlaylistArtwork(playlist.id, uiState.artworkPaths, size = trackInfoArtworkSize)
                    },
                    onPlayClick = onPlay,
                    onShuffleClick = onShuffle,
                    onMoreClick = { if (isReordering) isReordering = false else playlistMenuExpanded = true },
                    moreAction = { action ->
                        if (isReordering) action()
                        else PlaylistContextMenu(
                            playlistId = playlist.id,
                            trackIds = trackIds,
                            expanded = playlistMenuExpanded,
                            onDismiss = { playlistMenuExpanded = false },
                            hazeState = hazeState,
                            onPlayNext = onPlaylistPlayNext,
                            onAddToQueue = onPlaylistAddToQueue,
                            onReorder = { isReordering = true },
                            onEdit = { showPlaylistEditor = true },
                            onDelete = { showDeleteConfirmation = true },
                            anchor = action,
                        )
                    },
                )
            }
        }
        items(orderedTrackIds, key = { it }) { trackId ->
            val track = tracksById[trackId] ?: return@items
            Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(1.dp).background(colors.borderGlass).testTag(PlaylistTrackDividerTag))
            if (isReordering) {
                ReorderableItem(reorderableState, key = trackId) { isDragging ->
                    TrackRow(
                        title = track.title,
                        artist = track.artists,
                        artworkPath = track.artworkPath,
                        modifier = Modifier.fillMaxWidth().then(
                            if (isDragging) Modifier.liquidGlassBackground(
                                hazeState, colors, hazeBlurRadius = 30.dp, glassTint = colors.glassElevated,
                            ).border(1.dp, colors.borderGlass) else Modifier,
                        ),
                        trailingContent = {
                            PlaylistDragHandle(
                                isDragged = isDragging,
                                modifier = playlistDragHandleModifier(
                                    onStart = {
                                        draggedTrackId = trackId
                                        draggedTrackInitialIndex = orderedTrackIds.indexOf(trackId)
                                    },
                                    onStop = {
                                        draggedTrackId?.let { movedTrackId ->
                                            if (latestOrderedTrackIds.value.indexOf(movedTrackId) != draggedTrackInitialIndex) {
                                                val (previous, next) = playlistMoveAnchors(latestOrderedTrackIds.value, movedTrackId)
                                                latestOnTrackMove.value(movedTrackId, previous, next)
                                            }
                                        }
                                        draggedTrackId = null
                                        draggedTrackInitialIndex = -1
                                    },
                                ),
                            )
                        },
                    )
                }
            } else {
                TrackContextMenu(
                    track = track,
                    expanded = contextTrack == track.id,
                    onDismiss = { if (contextTrack == track.id) contextTrack = null },
                    actions = TrackContextMenuActions(removeFromPlaylist = playlist.id != FavoritesPlaylistId),
                    hazeState = hazeState,
                    playbackQueue = playbackQueue,
                    onPlayNext = { onTrackPlayNext(it.id) },
                    onAddToQueue = { onTrackAddToQueue(it.id) },
                    onFavoriteChange = { item, favorite -> onTrackFavoriteToggle(item.id, favorite) },
                    onGoToArtist = onTrackArtistClick,
                    onRemoveFromPlaylist = { onTrackRemoveFromPlaylist(it.id) },
                    onBottomSheetRequested = onTrackContextBottomSheet,
                ) {
                    TrackRow(
                        title = track.title, artist = track.artists, artworkPath = track.artworkPath,
                        modifier = Modifier.fillMaxWidth(), onClick = { onTrackClick(track.id) },
                        onMoreClick = { contextTrack = track.id }, onLongClick = { contextTrack = track.id },
                    )
                }
            }
            if (trackId == orderedTrackIds.lastOrNull()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(1.dp).background(colors.borderGlass).testTag(PlaylistTrackDividerTag))
            }
        }
    }
    if (showPlaylistEditor) {
        EditPlaylistBottomSheet(
            initialName = playlist.name,
            artworkPath = uiState.customArtworkPath,
            showNameInput = playlist.id != FavoritesPlaylistId,
            onDismiss = { showPlaylistEditor = false },
            onSave = { name, artwork, clearArtwork -> onPlaylistUpdate(playlist.id, name, artwork, clearArtwork); showPlaylistEditor = false },
        )
    }
    if (showDeleteConfirmation) {
        AirmedyDialog(
            title = stringResource(R.string.playlist_delete_confirm_title),
            description = stringResource(R.string.playlist_delete_confirm_description),
            dismissLabel = stringResource(R.string.cancel),
            onDismiss = { showDeleteConfirmation = false },
            confirmLabel = stringResource(R.string.playlist_delete),
            onConfirm = { onPlaylistDelete(playlist.id); showDeleteConfirmation = false },
            confirmVariant = AirmedyPillButtonVariant.Destructive,
        )
    }
}

@Composable
private fun PlaylistDragHandle(isDragged: Boolean, modifier: Modifier) {
    val colors = LocalAirmedyColors.current
    androidx.compose.foundation.layout.Box(
        Modifier.size(48.dp).then(modifier),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        MaterialSymbol(MaterialSymbols.Menu, stringResource(R.string.playlist_reorder_drag_handle), size = 24.dp, tint = if (isDragged) colors.textMain else colors.textMuted)
    }
}

private fun ReorderableCollectionItemScope.playlistDragHandleModifier(onStart: () -> Unit, onStop: () -> Unit) =
    Modifier.longPressDraggableHandle(onDragStarted = { onStart() }, onDragStopped = onStop)

internal fun movePlaylistTrack(trackIds: List<String>, fromIndex: Int, toIndex: Int): List<String> =
    trackIds.toMutableList().apply { if (fromIndex in indices && toIndex in indices && fromIndex != toIndex) add(toIndex, removeAt(fromIndex)) }

// The playlist hero occupies LazyColumn index 0; track rows begin immediately after it.
internal fun playlistTrackIndex(lazyListIndex: Int): Int = lazyListIndex - 1
