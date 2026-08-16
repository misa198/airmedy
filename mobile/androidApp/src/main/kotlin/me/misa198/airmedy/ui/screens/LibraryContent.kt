package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.DiscGridItem
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.TrackContextArtist
import me.misa198.airmedy.ui.components.TrackContextMenu
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import me.misa198.airmedy.ui.components.discGridItems
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun LibraryContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    recentTracks: List<LibraryTrack> = emptyList(),
    listState: LazyListState = rememberLazyListState(),
    onTrackClick: ((String) -> Unit)? = null,
    playbackQueue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    onTrackPlayNext: (LibraryTrack) -> Unit = {},
    onTrackAddToQueue: (LibraryTrack) -> Unit = {},
    onTrackFavoriteToggle: (LibraryTrack, Boolean) -> Unit = { _, _ -> },
    onTrackAlbumClick: (LibraryTrack) -> Unit = {},
    onTrackArtistClick: (TrackContextArtist) -> Unit = {},
    onArtistsSelected: (() -> Unit)? = null,
    onAlbumsSelected: (() -> Unit)? = null,
    onTracksSelected: (() -> Unit)? = null,
    onGenresSelected: (() -> Unit)? = null,
    onComposersSelected: (() -> Unit)? = null,
    onPlaylistsSelected: (() -> Unit)? = null,
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
) {
    val colors = LocalAirmedyColors.current
    var contextTrack by remember { mutableStateOf<LibraryTrack?>(null) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        item {
            ActionList(
                items = listOf(
                    ActionListItem(
                        labelRes = R.string.library_artists,
                        leadingSymbol = MaterialSymbols.People,
                        leadingIconTint = colors.primary,
                        onClick = onArtistsSelected,
                    ),
                    ActionListItem(
                        labelRes = R.string.library_albums,
                        leadingSymbol = MaterialSymbols.Album,
                        leadingIconTint = colors.primary,
                        onClick = onAlbumsSelected,
                    ),
                    ActionListItem(
                        labelRes = R.string.library_tracks,
                        leadingSymbol = MaterialSymbols.MusicNote,
                        leadingIconTint = colors.primary,
                        onClick = onTracksSelected,
                    ),
                    ActionListItem(
                        labelRes = R.string.library_genres,
                        leadingSymbol = MaterialSymbols.Label,
                        leadingIconTint = colors.primary,
                        onClick = onGenresSelected,
                    ),
                    ActionListItem(
                        labelRes = R.string.library_composers,
                        leadingSymbol = MaterialSymbols.StylusFountainPen,
                        leadingIconTint = colors.primary,
                        onClick = onComposersSelected,
                    ),
                    ActionListItem(
                        labelRes = R.string.library_playlists,
                        leadingSymbol = MaterialSymbols.QueueMusic,
                        leadingIconTint = colors.primary,
                        onClick = onPlaylistsSelected,
                    ),
                ),
                containerStyle = ActionListContainerStyle.Plain,
                horizontalContentPadding = 0.dp,
                showTrailingDivider = true,
            )
        }

        if (recentTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.library_recently_added),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textMain,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            discGridItems(
                items = recentTracks.map { track ->
                    DiscGridItem(
                        id = track.id,
                        title = track.title,
                        subtitle = track.artists,
                        artworkPath = track.artworkPath,
                        fallbackSymbol = MaterialSymbols.MusicNote,
                    )
                },
                onClick = onTrackClick,
                onLongClick = { trackId -> contextTrack = recentTracks.find { it.id == trackId } },
                itemWrapper = { item, itemModifier, content ->
                    val track = recentTracks.find { it.id == item.id }
                    if (track == null) {
                        content()
                    } else {
                        TrackContextMenu(
                            track = track,
                            expanded = contextTrack?.id == track.id,
                            onDismiss = { if (contextTrack?.id == track.id) contextTrack = null },
                            modifier = itemModifier,
                            playbackQueue = playbackQueue,
                            onPlayNext = onTrackPlayNext,
                            onAddToQueue = onTrackAddToQueue,
                            onFavoriteChange = onTrackFavoriteToggle,
                            onGoToAlbum = onTrackAlbumClick,
                            onGoToArtist = onTrackArtistClick,
                            onBottomSheetRequested = onTrackContextBottomSheet,
                            anchor = content,
                        )
                    }
                },
            )
        }
    }
}
