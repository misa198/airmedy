package me.misa198.airmedy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import me.misa198.airmedy.sync.metadataObject
import me.misa198.airmedy.ui.screens.isFavorite
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** Per-host visibility policy for the reusable track overflow sheet. */
data class TrackContextMenuActions(
    val playNext: Boolean = true,
    val addToQueue: Boolean = true,
    val trackInfo: Boolean = true,
    val favorite: Boolean = true,
    val addToPlaylist: Boolean = true,
    val goToAlbum: Boolean = true,
    val goToArtists: Boolean = true,
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
    data object Playlist : TrackContextBottomSheetRequest
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
    onPlayNext: (LibraryTrack) -> Unit = {},
    onAddToQueue: (LibraryTrack) -> Unit = {},
    onFavoriteChange: (LibraryTrack, Boolean) -> Unit = { _, _ -> },
    onGoToAlbum: (LibraryTrack) -> Unit = {},
    onGoToArtist: (TrackContextArtist) -> Unit = {},
    onBottomSheetRequested: ((TrackContextBottomSheetRequest) -> Unit)? = null,
    onCloseFullscreenThen: ((() -> Unit) -> Unit) = { action -> action() },
    anchor: @Composable () -> Unit,
) {
    var detailSheet by remember(track.id) { mutableStateOf<TrackContextBottomSheetRequest?>(null) }
    val artists = remember(track.metadataJson) { trackContextArtists(track) }
    val hasAlbum = track.albumId.isNotBlank() && track.album.isNotBlank()
    val hasArtists = track.artists.isNotBlank() && artists.isNotEmpty()
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
        Column(Modifier.fillMaxWidth()) {
            val showPlayNext = actions.playNext && queueAvailability.showPlayNext
            val showAddToQueue = actions.addToQueue && queueAvailability.showAddToQueue && queueAvailability.addToQueueEnabled
            if (showPlayNext) TrackContextAction(R.string.track_context_play_next, MaterialSymbols.QueuePlayNext) { closeAfter { onPlayNext(track) } }
            if (showAddToQueue) TrackContextAction(R.string.track_context_add_to_queue, MaterialSymbols.AddToQueue) { closeAfter { onAddToQueue(track) } }
            if (actions.trackInfo) TrackContextAction(R.string.track_context_track_info, MaterialSymbols.Info) { requestBottomSheet(TrackContextBottomSheetRequest.Info) }
            if (showPlayNext || showAddToQueue || actions.trackInfo) ActionListDivider(ActionListDividerStyle.FullWidth)
            if (actions.favorite) TrackContextAction(if (favorite) R.string.track_context_remove_from_favorites else R.string.track_context_add_to_favorites, if (favorite) MaterialSymbols.HeartMinus else MaterialSymbols.HeartPlus) {
                closeAfter { onFavoriteChange(track, !favorite) }
            }
            if (actions.addToPlaylist) TrackContextAction(R.string.track_context_add_to_playlist, MaterialSymbols.PlaylistAdd) { requestBottomSheet(TrackContextBottomSheetRequest.Playlist) }
            if (actions.favorite || actions.addToPlaylist) ActionListDivider(ActionListDividerStyle.FullWidth)
            if (actions.goToAlbum && hasAlbum) TrackContextAction(R.string.track_context_go_to_album, MaterialSymbols.Album) { presentAfterFullscreenCloses { onGoToAlbum(track) } }
            if (actions.goToArtists && hasArtists && artists.size == 1) TrackContextAction(R.string.track_context_go_to_artist, MaterialSymbols.Person, artists.single().name) { presentAfterFullscreenCloses { onGoToArtist(artists.single()) } }
            if (actions.goToArtists && hasArtists && artists.size > 1) TrackContextAction(R.string.track_context_go_to_artists, MaterialSymbols.People) {
                requestBottomSheet(TrackContextBottomSheetRequest.Artists(artists))
            }
        }
    }
    detailSheet?.let { request ->
        // The anchor can carry parent-data modifiers (for example RowScope.weight
        // in Library Home's recent-track grid). A Dialog must not inherit those
        // constraints, or its sheet can be measured at the top of the window.
        TrackContextBottomSheet(
            request = request,
            onDismiss = dismissAll,
            onArtistSelected = { artist -> presentAfterFullscreenCloses { onGoToArtist(artist) } },
        )
    }
}

@Composable
internal fun TrackContextBottomSheet(
    request: TrackContextBottomSheetRequest,
    onDismiss: () -> Unit,
    onArtistSelected: (TrackContextArtist) -> Unit,
    modifier: Modifier = Modifier,
) = AirmedyBottomSheet(
    title = {
        TrackContextSheetTitle(
            when (request) {
                TrackContextBottomSheetRequest.Info -> R.string.track_context_track_info_title
                TrackContextBottomSheetRequest.Playlist -> R.string.track_context_playlist_picker_title
                is TrackContextBottomSheetRequest.Artists -> R.string.track_context_artist_picker_title
            },
        )
    },
    onDismiss = onDismiss,
    modifier = modifier,
) {
    when (request) {
        TrackContextBottomSheetRequest.Info, TrackContextBottomSheetRequest.Playlist ->
            Text(stringResource(R.string.track_context_placeholder), modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp), color = LocalAirmedyColors.current.textMuted)
        is TrackContextBottomSheetRequest.Artists -> Column(Modifier.fillMaxWidth()) {
            request.artists.forEach { artist ->
                TrackContextActionText(artist.name, MaterialSymbols.Person) { onArtistSelected(artist) }
            }
        }
    }
}

@Composable private fun TrackContextSheetTitle(title: Int) = Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = LocalAirmedyColors.current.textMain)

@Composable private fun TrackContextAction(label: Int, symbol: String, formatArg: String? = null, onClick: () -> Unit) = TrackContextActionText(stringResource(label, *(formatArg?.let(::arrayOf) ?: emptyArray())), symbol, onClick)

@Composable private fun TrackContextActionText(label: String, symbol: String, onClick: () -> Unit) {
    val colors = LocalAirmedyColors.current
    val contentColor = colors.textMain
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
        contentDescription = label
    }.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbol(symbol, null, size = 21.dp, tint = contentColor)
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
