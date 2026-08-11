package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.LibraryAlbum
import me.misa198.airmedy.ui.components.AlbumRow
import me.misa198.airmedy.ui.components.ArtworkHeroBackdrop
import me.misa198.airmedy.ui.components.DetailHero
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun GenreDetailsContent(
    uiState: GenreDetailsUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    contentPadding: PaddingValues = PaddingValues(),
    hazeState: HazeState? = null,
    onHeroColorChanged: (Color) -> Unit = {},
    onPlay: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onAlbumClick: (LibraryAlbum) -> Unit = {},
) {
    val genre = uiState.genre
    if (genre == null) {
        HeroCard(
            symbol = MaterialSymbols.Label,
            title = stringResource(R.string.genre_details_empty_title),
            description = stringResource(R.string.genre_details_empty_description),
            modifier = modifier.fillMaxSize().padding(contentPadding),
        )
        return
    }
    val colors = LocalAirmedyColors.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        item("hero") {
            ArtworkHeroBackdrop(null, Modifier.fillMaxWidth(), hazeState, onHeroColorChanged) {
                DetailHero(
                    title = genre.name,
                    subtitle = stringResource(
                        R.string.genre_details_summary,
                        pluralStringResource(R.plurals.genre_details_album_count, uiState.albums.size, uiState.albums.size),
                        pluralStringResource(R.plurals.genre_details_track_count, uiState.tracks.size, uiState.tracks.size),
                    ),
                    metadata = null,
                    playLabel = stringResource(R.string.player_play),
                    shuffleLabel = stringResource(R.string.player_shuffle),
                    moreLabel = stringResource(R.string.genre_row_more_options),
                    modifier = Modifier.fillMaxWidth().padding(
                        start = 24.dp,
                        top = contentPadding.calculateTopPadding() - 8.dp,
                        end = 24.dp,
                        bottom = 20.dp,
                    ),
                    showArtwork = false,
                    onPlayClick = onPlay,
                    onShuffleClick = onShuffle,
                )
            }
        }
        itemsIndexed(uiState.albums, key = { _, album -> album.id }, contentType = { _, _ -> "genre_album_row" }) { index, album ->
            AlbumRow(
                title = album.title,
                artist = album.artist.ifBlank { stringResource(R.string.album_unknown_artist) },
                artworkPath = album.artworkPath,
                onClick = { onAlbumClick(album) },
            )
            if (index < uiState.albums.lastIndex) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).background(colors.borderGlass).padding(top = 1.dp))
            }
        }
    }
}
