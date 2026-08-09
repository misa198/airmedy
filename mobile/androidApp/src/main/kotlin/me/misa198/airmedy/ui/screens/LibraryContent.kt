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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.DiscGridItem
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.discGridItems
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun LibraryContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    recentTracks: List<LibraryTrack> = emptyList(),
    listState: LazyListState = rememberLazyListState(),
    onTrackClick: ((String) -> Unit)? = null,
    onArtistsSelected: (() -> Unit)? = null,
    onAlbumsSelected: (() -> Unit)? = null,
    onTracksSelected: (() -> Unit)? = null,
    onGenresSelected: (() -> Unit)? = null,
    onComposersSelected: (() -> Unit)? = null,
    onPlaylistsSelected: (() -> Unit)? = null,
) {
    val colors = LocalAirmedyColors.current
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
            )
        }
    }
}
