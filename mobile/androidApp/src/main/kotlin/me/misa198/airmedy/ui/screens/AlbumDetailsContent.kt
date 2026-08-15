package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.ui.components.AlbumTrackRow
import me.misa198.airmedy.ui.components.AlbumContextMenu
import me.misa198.airmedy.ui.components.ArtworkHeroBackdrop
import me.misa198.airmedy.ui.components.DetailHero
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.TrackContextArtist
import me.misa198.airmedy.ui.components.TrackContextMenu
import me.misa198.airmedy.ui.components.TrackContextMenuActions
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private const val AlbumTrackDividerTag = "album-detail-track-divider"

@Composable
internal fun AlbumDetailsContent(
    uiState: AlbumDetailsUiState,
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
    onAlbumPlayNext: (List<String>) -> Unit = {},
    onAlbumAddToQueue: (List<String>) -> Unit = {},
    onAlbumAddToFavorites: (List<String>) -> Unit = {},
) {
    val album = uiState.album
    if (album == null) {
        HeroCard(symbol = MaterialSymbols.Album, title = stringResource(R.string.album_details_empty_title), description = stringResource(R.string.album_details_empty_description), modifier = modifier.fillMaxSize().padding(contentPadding))
        return
    }
    val colors = LocalAirmedyColors.current
    var contextTrack by remember { mutableStateOf<String?>(null) }
    var albumMenuExpanded by remember(album.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val trackCount = pluralStringResource(R.plurals.album_details_track_count, uiState.tracks.size, uiState.tracks.size)
    val duration = formatAlbumTotalDuration(
        albumTotalDurationSeconds(uiState.tracks),
        day = { context.getString(R.string.playlist_duration_day, it) },
        hour = { context.getString(R.string.playlist_duration_hour, it) },
        minute = { context.getString(R.string.playlist_duration_minute, it) },
        second = { context.getString(R.string.playlist_duration_second, it) },
    )
    val metadata = if (album.year > 0) {
        stringResource(R.string.album_details_metadata_with_year, album.year, trackCount, duration)
    } else {
        stringResource(R.string.album_details_metadata, trackCount, duration)
    }
    val listPadding = PaddingValues(
        top = 0.dp,
        bottom = contentPadding.calculateBottomPadding(),
        start = 0.dp,
        end = 0.dp,
    )
    LazyColumn(modifier.fillMaxSize(), contentPadding = listPadding) {
        item("hero") {
            ArtworkHeroBackdrop(album.artworkPath, Modifier.fillMaxWidth(), hazeState, onHeroColorChanged) {
                AlbumContextMenu(
                    tracks = uiState.tracks,
                    expanded = albumMenuExpanded,
                    onDismiss = { albumMenuExpanded = false },
                    hazeState = hazeState,
                    playbackQueue = playbackQueue,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    onPlayNext = onAlbumPlayNext,
                    onAddToQueue = onAlbumAddToQueue,
                    onAddToFavorites = onAlbumAddToFavorites,
                ) {
                    DetailHero(
                    album.title,
                    album.artist.ifBlank { stringResource(R.string.album_unknown_artist) },
                    metadata,
                    stringResource(R.string.player_play),
                    stringResource(R.string.player_shuffle),
                    stringResource(R.string.album_row_more_options),
                    Modifier.fillMaxWidth().padding(
                        start = 24.dp,
                        top = contentPadding.calculateTopPadding(),
                        end = 24.dp,
                        bottom = 20.dp,
                    ),
                    album.artworkPath,
                    onPlayClick = onPlay,
                    onShuffleClick = onShuffle,
                    onMoreClick = { albumMenuExpanded = true },
                    )
                }
            }
        }
        itemsIndexed(uiState.tracks, key = { _, track -> track.id }) { index, track ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .height(1.dp)
                    .background(colors.borderGlass)
                    .testTag(AlbumTrackDividerTag),
            )
            TrackContextMenu(
                track = track,
                expanded = contextTrack == track.id,
                onDismiss = { if (contextTrack == track.id) contextTrack = null },
                actions = TrackContextMenuActions(goToAlbum = false),
                playbackQueue = playbackQueue,
                onPlayNext = { onTrackPlayNext(it.id) },
                onAddToQueue = { onTrackAddToQueue(it.id) },
                onFavoriteChange = { item, favorite -> onTrackFavoriteToggle(item.id, favorite) },
                onGoToArtist = onTrackArtistClick,
            ) {
                AlbumTrackRow(
                    track.trackNumber.takeIf { it > 0 } ?: index + 1,
                    track.title,
                    track.artists,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onTrackClick(track.id) },
                    onMoreClick = { contextTrack = track.id },
                    onLongClick = { contextTrack = track.id },
                )
            }
            if (index == uiState.tracks.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .height(1.dp)
                        .background(colors.borderGlass)
                        .testTag(AlbumTrackDividerTag),
                )
            }
        }
        album.copyright.takeIf(String::isNotBlank)?.let { copyright ->
            item("copyright") {
                Text(
                    text = copyright,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 12.dp),
                )
            }
        }
    }
}
