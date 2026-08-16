package me.misa198.airmedy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.LibraryPlaylist
import me.misa198.airmedy.sync.LibrarySyncProtocol
import me.misa198.airmedy.sync.metadataObject
import me.misa198.airmedy.ui.screens.isFavorite
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** Per-host visibility policy for the reusable track overflow sheet. */
data class TrackContextMenuActions(
    val removeFromQueue: Boolean = false,
    val playNext: Boolean = true,
    val addToQueue: Boolean = true,
    val trackInfo: Boolean = true,
    val favorite: Boolean = true,
    val addToPlaylist: Boolean = true,
    val goToAlbum: Boolean = true,
    val goToArtists: Boolean = true,
    val removeFromPlaylist: Boolean = false,
)

data class TrackContextArtist(val id: String, val name: String)

/** Queue-derived availability mirrors the desktop track context menu. */
internal data class TrackContextQueueAvailability(
    val showPlayNext: Boolean,
    val showAddToQueue: Boolean,
    val addToQueueEnabled: Boolean,
)

internal fun trackContextQueueAvailability(
    trackId: String,
    queue: PlaybackQueueSnapshot,
): TrackContextQueueAvailability = TrackContextQueueAvailability(
    showPlayNext = trackId != queue.currentTrackId,
    showAddToQueue = trackId != queue.currentTrackId,
    addToQueueEnabled = trackId != queue.currentTrackId && trackId !in queue.activeTrackIds,
)

internal fun trackContextArtists(track: LibraryTrack): List<TrackContextArtist> {
    val root = track.metadataObject() ?: return emptyList()
    return (root["artists"] as? JsonArray).orEmpty().mapNotNull { value ->
        val artist = value as? JsonObject ?: return@mapNotNull null
        val id = (artist["id"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val name = (artist["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        TrackContextArtist(id, name).takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
    }.distinctBy(TrackContextArtist::id)
}

sealed interface TrackContextBottomSheetRequest {
    data object Info : TrackContextBottomSheetRequest
    data class Playlist(val trackIds: List<String>) : TrackContextBottomSheetRequest
    data class Artists(val artists: List<TrackContextArtist>) : TrackContextBottomSheetRequest
}

@Composable
fun TrackContextMenu(
    track: LibraryTrack,
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actions: TrackContextMenuActions = TrackContextMenuActions(),
    hazeState: HazeState? = null,
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    onRemoveFromQueue: (LibraryTrack) -> Unit = {},
    onPlayNext: (LibraryTrack) -> Unit = {},
    onAddToQueue: (LibraryTrack) -> Unit = {},
    onFavoriteChange: (LibraryTrack, Boolean) -> Unit = { _, _ -> },
    onRemoveFromPlaylist: (LibraryTrack) -> Unit = {},
    onGoToAlbum: (LibraryTrack) -> Unit = {},
    onGoToArtist: (TrackContextArtist) -> Unit = {},
    onBottomSheetRequested: ((TrackContextBottomSheetRequest) -> Unit)? = null,
    onCloseFullscreenThen: ((() -> Unit) -> Unit) = { action -> action() },
    playlists: List<LibraryPlaylist> = emptyList(),
    onPlaylistMembershipChange: (playlistId: String, trackIds: List<String>, add: Boolean) -> Unit = { _, _, _ -> },
    onCreatePlaylistRequested: (trackIds: List<String>) -> Unit = {},
    anchor: @Composable () -> Unit,
) {
    var detailSheet by remember(track.id) { mutableStateOf<TrackContextBottomSheetRequest?>(null) }
    val artists = remember(track.metadataJson) { trackContextArtists(track) }
    val hasAlbum = track.albumId.isNotBlank() && track.album.isNotBlank()
    val hasArtists = track.artists.isNotBlank() && artists.isNotEmpty()
    val hasNavigationActions = actions.goToAlbum && hasAlbum || actions.goToArtists && hasArtists
    val queueAvailability = remember(track.id, playbackQueue) {
        trackContextQueueAvailability(track.id, playbackQueue)
    }
    val favorite = track.isFavorite()
    val dismissAll = {
        detailSheet = null
        onDismiss()
    }
    val closeAfter: ((() -> Unit) -> Unit) = { action -> action(); dismissAll() }
    val presentAfterFullscreenCloses: ((() -> Unit) -> Unit) = { action ->
        dismissAll()
        onCloseFullscreenThen(action)
    }
    val requestBottomSheet: (TrackContextBottomSheetRequest) -> Unit = { request ->
        if (onBottomSheetRequested == null) {
            presentAfterFullscreenCloses { detailSheet = request }
        } else {
            dismissAll()
            onBottomSheetRequested(request)
        }
    }

    // The anchor must remain in composition while a sheet is open. In a LazyColumn,
    // replacing it with the Dialog would make the row measure to zero and disappear.
    AnchoredPopupMenu(
        expanded = expanded && detailSheet == null,
        onDismissRequest = dismissAll,
        modifier = modifier,
        width = 272.dp,
        offset = DpOffset(x = (-8).dp, y = 8.dp),
        hazeState = hazeState,
        anchor = anchor,
    ) {
        val entries = buildList {
            if (actions.removeFromQueue) {
                add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_remove_from_queue), MaterialSymbols.RemoveFromQueue, destructive = true) { closeAfter { onRemoveFromQueue(track) } })
                add(ContextActionMenuEntry.Divider)
            }
            val showPlayNext = actions.playNext && queueAvailability.showPlayNext
            val showAddToQueue = actions.addToQueue && queueAvailability.showAddToQueue && queueAvailability.addToQueueEnabled
            if (showPlayNext) add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_play_next), MaterialSymbols.QueuePlayNext) { closeAfter { onPlayNext(track) } })
            if (showAddToQueue) add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_add_to_queue), MaterialSymbols.AddToQueue) { closeAfter { onAddToQueue(track) } })
            if (actions.trackInfo) add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_track_info), MaterialSymbols.Info) { requestBottomSheet(TrackContextBottomSheetRequest.Info) })
            if (showPlayNext || showAddToQueue || actions.trackInfo) add(ContextActionMenuEntry.Divider)
            if (actions.favorite) add(ContextActionMenuEntry.Action(stringResource(if (favorite) R.string.track_context_remove_from_favorites else R.string.track_context_add_to_favorites), if (favorite) MaterialSymbols.HeartMinus else MaterialSymbols.HeartPlus) { closeAfter { onFavoriteChange(track, !favorite) } })
            if (actions.addToPlaylist) add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_add_to_playlist), MaterialSymbols.PlaylistAdd) { requestBottomSheet(TrackContextBottomSheetRequest.Playlist(listOf(track.id))) })
            if ((actions.favorite || actions.addToPlaylist) && (hasNavigationActions || actions.removeFromPlaylist)) {
                add(ContextActionMenuEntry.Divider)
            }
            if (actions.goToAlbum && hasAlbum) add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_go_to_album), MaterialSymbols.Album) { presentAfterFullscreenCloses { onGoToAlbum(track) } })
            if (actions.goToArtists && hasArtists && artists.size == 1) add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_go_to_artist, artists.single().name), MaterialSymbols.Person) { presentAfterFullscreenCloses { onGoToArtist(artists.single()) } })
            if (actions.goToArtists && hasArtists && artists.size > 1) add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_go_to_artists), MaterialSymbols.People) { requestBottomSheet(TrackContextBottomSheetRequest.Artists(artists)) })
            if (actions.removeFromPlaylist) {
                if (hasNavigationActions) add(ContextActionMenuEntry.Divider)
                add(ContextActionMenuEntry.Action(stringResource(R.string.track_context_remove_from_playlist), MaterialSymbols.PlaylistRemove, destructive = true) { closeAfter { onRemoveFromPlaylist(track) } })
            }
        }
        ContextActionMenu(entries)
    }
    detailSheet?.let { request ->
        // The anchor can carry parent-data modifiers (for example RowScope.weight
        // in Library Home's recent-track grid). A Dialog must not inherit those
        // constraints, or its sheet can be measured at the top of the window.
        TrackContextBottomSheet(
            request = request,
            onDismiss = dismissAll,
            onArtistSelected = { artist -> presentAfterFullscreenCloses { onGoToArtist(artist) } },
            playlists = playlists,
            onPlaylistMembershipChange = onPlaylistMembershipChange,
            onCreatePlaylistRequested = onCreatePlaylistRequested,
        )
    }
}

@Composable
internal fun TrackContextBottomSheet(
    request: TrackContextBottomSheetRequest,
    onDismiss: () -> Unit,
    onArtistSelected: (TrackContextArtist) -> Unit,
    modifier: Modifier = Modifier,
    playlists: List<LibraryPlaylist> = emptyList(),
    onPlaylistMembershipChange: (playlistId: String, trackIds: List<String>, add: Boolean) -> Unit = { _, _, _ -> },
    onCreatePlaylistRequested: (trackIds: List<String>) -> Unit = {},
) = AirmedyBottomSheet(
    title = {
        TrackContextSheetTitle(
            when (request) {
                TrackContextBottomSheetRequest.Info -> R.string.track_context_track_info_title
                is TrackContextBottomSheetRequest.Playlist -> R.string.track_context_playlist_picker_title
                is TrackContextBottomSheetRequest.Artists -> R.string.track_context_artist_picker_title
            },
        )
    },
    onDismiss = onDismiss,
    modifier = modifier,
) {
    when (request) {
        TrackContextBottomSheetRequest.Info ->
            Text(stringResource(R.string.track_context_placeholder), modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp), color = LocalAirmedyColors.current.textMuted)
        is TrackContextBottomSheetRequest.Playlist -> PlaylistPickerContent(
            trackIds = request.trackIds,
            playlists = playlists,
            onPlaylistMembershipChange = onPlaylistMembershipChange,
            onCreatePlaylistRequested = onCreatePlaylistRequested,
        )
        is TrackContextBottomSheetRequest.Artists -> ContextActionMenu(
            request.artists.map { artist ->
                ContextActionMenuEntry.Action(artist.name, MaterialSymbols.Person) { onArtistSelected(artist) }
            },
        )
    }
}

@Composable private fun TrackContextSheetTitle(title: Int) = Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = LocalAirmedyColors.current.textMain)

@Composable
private fun PlaylistPickerContent(
    trackIds: List<String>,
    playlists: List<LibraryPlaylist>,
    onPlaylistMembershipChange: (playlistId: String, trackIds: List<String>, add: Boolean) -> Unit,
    onCreatePlaylistRequested: (trackIds: List<String>) -> Unit,
) {
    val colors = LocalAirmedyColors.current
    val editable = remember(playlists) { playlists.filter(::playlistIsEditable) }
    val singleTrack = trackIds.distinct().singleOrNull()
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onCreatePlaylistRequested(trackIds) }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                MaterialSymbol(MaterialSymbols.Add, null, size = 18.dp, tint = colors.primary)
            }
            Text(stringResource(R.string.track_context_create_playlist), modifier = Modifier.padding(start = 12.dp), color = colors.textMain)
        }
        if (editable.isEmpty()) {
            Text(stringResource(R.string.track_context_no_editable_playlists), modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp), color = colors.textMuted)
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(editable, key = LibraryPlaylist::id) { playlist ->
                    val checked = if (singleTrack != null) {
                        singleTrack in playlist.trackIds
                    } else {
                        trackIds.isNotEmpty() && trackIds.all { it in playlist.trackIds }
                    }
                    val add = !checked
                    val targetIds = when {
                        singleTrack != null -> listOf(singleTrack)
                        add -> trackIds.filter { it !in playlist.trackIds }
                        else -> trackIds
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { if (targetIds.isNotEmpty()) onPlaylistMembershipChange(playlist.id, targetIds, add) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistPickerCheckbox(
                            checked = checked,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(playlist.name, modifier = Modifier.padding(start = 12.dp), color = colors.textMain)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistPickerCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAirmedyColors.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (checked) colors.primary else colors.glassElevated)
            .border(1.dp, if (checked) colors.primary else colors.borderGlass, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) MaterialSymbol(MaterialSymbols.Check, null, size = 14.dp, tint = colors.onPrimary)
    }
}

internal fun playlistIsEditable(playlist: LibraryPlaylist): Boolean {
    if (playlist.id == "favorites") return false
    val root = runCatching { LibrarySyncProtocol.json.parseToJsonElement(playlist.metadataJson) as? JsonObject }.getOrNull()
    val value = (root?.get("playlist") as? JsonObject ?: root)?.get("is_smart") as? JsonPrimitive
    return value?.contentOrNull != "true"
}
