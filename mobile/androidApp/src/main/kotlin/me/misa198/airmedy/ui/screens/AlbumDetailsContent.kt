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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.AlbumTrackRow
import me.misa198.airmedy.ui.components.ArtworkHeroBackdrop
import me.misa198.airmedy.ui.components.DetailHero
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private const val AlbumTrackDividerTag = "album-detail-track-divider"

@Composable
internal fun AlbumDetailsContent(uiState: AlbumDetailsUiState, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(), hazeState: HazeState? = null, onHeroColorChanged: (Color) -> Unit = {}, onPlay: () -> Unit = {}, onShuffle: () -> Unit = {}, onTrackClick: (String) -> Unit = {}) {
    val album = uiState.album
    if (album == null) {
        HeroCard(symbol = MaterialSymbols.Album, title = stringResource(R.string.album_details_empty_title), description = stringResource(R.string.album_details_empty_description), modifier = modifier.fillMaxSize().padding(contentPadding))
        return
    }
    val colors = LocalAirmedyColors.current
    val trackCount = pluralStringResource(R.plurals.album_details_track_count, uiState.tracks.size, uiState.tracks.size)
    val metadata = if (album.year > 0) {
        stringResource(R.string.album_details_metadata_with_year, album.year, trackCount)
    } else {
        trackCount
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
                )
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
            AlbumTrackRow(
                track.trackNumber.takeIf { it > 0 } ?: index + 1,
                track.title,
                track.artists,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onTrackClick(track.id) },
            )
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
