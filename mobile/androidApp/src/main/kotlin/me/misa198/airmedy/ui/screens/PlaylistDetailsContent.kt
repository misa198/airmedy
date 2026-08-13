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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import me.misa198.airmedy.ui.components.PlaylistArtwork
import me.misa198.airmedy.ui.components.TrackRow
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

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
    val context = LocalContext.current
    val count = pluralStringResource(R.plurals.playlist_details_track_count, uiState.tracks.size, uiState.tracks.size)
    val duration = formatPlaylistTotalDuration(
        playlistTotalDurationSeconds(uiState.tracks),
        day = { context.getString(R.string.playlist_duration_day, it) },
        hour = { context.getString(R.string.playlist_duration_hour, it) },
        minute = { context.getString(R.string.playlist_duration_minute, it) },
        second = { context.getString(R.string.playlist_duration_second, it) },
    )
    val name = if (playlist.id == FavoritesPlaylistId) stringResource(R.string.library_favorites) else playlist.name
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        item("hero") {
            ArtworkHeroBackdrop(uiState.artworkPaths.firstOrNull(), Modifier.fillMaxWidth(), hazeState, onHeroColorChanged) {
                DetailHero(
                    title = name,
                    metadata = stringResource(R.string.playlist_details_metadata, count, duration),
                    playLabel = stringResource(R.string.player_play),
                    shuffleLabel = stringResource(R.string.player_shuffle),
                    moreLabel = stringResource(R.string.album_row_more_options),
                    modifier = Modifier.fillMaxWidth().padding(
                        start = 24.dp,
                        top = contentPadding.calculateTopPadding(),
                        end = 24.dp,
                        bottom = 20.dp,
                    ),
                    fallbackSymbol = if (playlist.id == FavoritesPlaylistId) MaterialSymbols.Favorite else MaterialSymbols.QueueMusic,
                    artworkContent = {
                        PlaylistArtwork(playlist.id, uiState.artworkPaths, size = 248.dp)
                    },
                    onPlayClick = onPlay,
                    onShuffleClick = onShuffle,
                )
            }
        }
        itemsIndexed(uiState.tracks, key = { index, track -> "${track.id}:$index" }) { index, track ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(1.dp).background(colors.borderGlass).testTag(PlaylistTrackDividerTag))
            TrackRow(
                title = track.title,
                artist = track.artists,
                artworkPath = track.artworkPath,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onTrackClick(track.id) },
            )
            if (index == uiState.tracks.lastIndex) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(1.dp).background(colors.borderGlass).testTag(PlaylistTrackDividerTag))
            }
        }
    }
}
