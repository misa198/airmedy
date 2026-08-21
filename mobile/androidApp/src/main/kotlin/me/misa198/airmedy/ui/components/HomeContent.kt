package me.misa198.airmedy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun HomeContent(
    isLoaded: Boolean,
    keepListeningTracks: List<LibraryTrack>,
    mostPlayedTracks: List<LibraryTrack>,
    forgottenTracks: List<LibraryTrack>,
    onTrackClick: (List<LibraryTrack>, String) -> Unit,
    playbackQueue: PlaybackQueueSnapshot,
    onTrackPlayNext: (LibraryTrack) -> Unit,
    onTrackAddToQueue: (LibraryTrack) -> Unit,
    onTrackFavoriteToggle: (LibraryTrack, Boolean) -> Unit,
    onTrackAlbumClick: (LibraryTrack) -> Unit,
    onTrackArtistClick: (TrackContextArtist) -> Unit,
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val titleHorizontalPadding = contentPadding.calculateStartPadding(layoutDirection)
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        if (!isLoaded && keepListeningTracks.isEmpty() && mostPlayedTracks.isEmpty() && forgottenTracks.isEmpty()) {
            return@LazyColumn
        }
        if (keepListeningTracks.isEmpty() && mostPlayedTracks.isEmpty() && forgottenTracks.isEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    HeroCard(
                        symbol = MaterialSymbols.MusicNote,
                        title = stringResource(R.string.library_empty_title),
                        description = stringResource(R.string.library_empty_description),
                    )
                }
            }
        } else {
            if (keepListeningTracks.isNotEmpty()) item {
                HomeTrackSection(
                    titleRes = R.string.home_keep_listening,
                    tracks = keepListeningTracks,
                    onTrackClick = onTrackClick,
                    playbackQueue = playbackQueue,
                    onTrackPlayNext = onTrackPlayNext,
                    onTrackAddToQueue = onTrackAddToQueue,
                    onTrackFavoriteToggle = onTrackFavoriteToggle,
                    onTrackAlbumClick = onTrackAlbumClick,
                    onTrackArtistClick = onTrackArtistClick,
                    onTrackContextBottomSheet = onTrackContextBottomSheet,
                    titleHorizontalPadding = titleHorizontalPadding,
                )
            }
            if (mostPlayedTracks.isNotEmpty()) item {
                HomeTrackSection(
                    titleRes = R.string.home_most_played,
                    tracks = mostPlayedTracks,
                    onTrackClick = onTrackClick,
                    playbackQueue = playbackQueue,
                    onTrackPlayNext = onTrackPlayNext,
                    onTrackAddToQueue = onTrackAddToQueue,
                    onTrackFavoriteToggle = onTrackFavoriteToggle,
                    onTrackAlbumClick = onTrackAlbumClick,
                    onTrackArtistClick = onTrackArtistClick,
                    onTrackContextBottomSheet = onTrackContextBottomSheet,
                    titleHorizontalPadding = titleHorizontalPadding,
                )
            }
            if (forgottenTracks.isNotEmpty()) item {
                HomeTrackSection(
                    titleRes = R.string.home_forgotten,
                    tracks = forgottenTracks,
                    onTrackClick = onTrackClick,
                    playbackQueue = playbackQueue,
                    onTrackPlayNext = onTrackPlayNext,
                    onTrackAddToQueue = onTrackAddToQueue,
                    onTrackFavoriteToggle = onTrackFavoriteToggle,
                    onTrackAlbumClick = onTrackAlbumClick,
                    onTrackArtistClick = onTrackArtistClick,
                    onTrackContextBottomSheet = onTrackContextBottomSheet,
                    titleHorizontalPadding = titleHorizontalPadding,
                )
            }
        }
    }
}

@Composable
private fun HomeTrackSection(
    titleRes: Int,
    tracks: List<LibraryTrack>,
    onTrackClick: (List<LibraryTrack>, String) -> Unit,
    playbackQueue: PlaybackQueueSnapshot,
    onTrackPlayNext: (LibraryTrack) -> Unit,
    onTrackAddToQueue: (LibraryTrack) -> Unit,
    onTrackFavoriteToggle: (LibraryTrack, Boolean) -> Unit,
    onTrackAlbumClick: (LibraryTrack) -> Unit,
    onTrackArtistClick: (TrackContextArtist) -> Unit,
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit,
    titleHorizontalPadding: androidx.compose.ui.unit.Dp,
) {
    if (tracks.isEmpty()) return

    val colors = LocalAirmedyColors.current
    var contextTrack by remember { mutableStateOf<LibraryTrack?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textMain,
            modifier = Modifier.padding(horizontal = titleHorizontalPadding, vertical = 4.dp),
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(homeTrackSectionRows(tracks.size)),
            modifier = Modifier
                .fillMaxWidth()
                .height(homeTrackSectionHeight(tracks.size)),
            contentPadding = PaddingValues(horizontal = titleHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(tracks.size, key = { index -> tracks[index].id }) { index ->
                val track = tracks[index]
                TrackContextMenu(
                    track = track,
                    expanded = contextTrack?.id == track.id,
                    onDismiss = { if (contextTrack?.id == track.id) contextTrack = null },
                    modifier = Modifier.width(128.dp),
                    playbackQueue = playbackQueue,
                    onPlayNext = onTrackPlayNext,
                    onAddToQueue = onTrackAddToQueue,
                    onFavoriteChange = onTrackFavoriteToggle,
                    onGoToAlbum = onTrackAlbumClick,
                    onGoToArtist = onTrackArtistClick,
                    onBottomSheetRequested = onTrackContextBottomSheet,
                ) {
                    DiscCard(
                    title = track.title,
                    subtitle = track.artists,
                    artworkPath = track.artworkPath,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTrackClick(tracks, track.id) },
                    onLongClick = { contextTrack = track },
                    )
                }
            }
        }
    }
}

internal fun homeTrackSectionRows(trackCount: Int) = minOf(trackCount, 2)

internal fun homeTrackSectionHeight(trackCount: Int) = if (homeTrackSectionRows(trackCount) == 1) 182.dp else 384.dp
