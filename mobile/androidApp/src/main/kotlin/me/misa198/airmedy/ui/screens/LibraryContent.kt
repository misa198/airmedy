package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun LibraryContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onArtistsSelected: (() -> Unit)? = null,
    onAlbumsSelected: (() -> Unit)? = null,
    onTracksSelected: (() -> Unit)? = null,
    onGenresSelected: (() -> Unit)? = null,
    onComposersSelected: (() -> Unit)? = null,
) {
    val colors = LocalAirmedyColors.current
    Column(
        modifier = modifier.padding(
            start = 8.dp,
            top = contentPadding.calculateTopPadding(),
            end = 8.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
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
                    leadingSymbol = MaterialSymbols.Mic,
                    leadingIconTint = colors.primary,
                    onClick = onComposersSelected,
                ),
            ),
            containerStyle = ActionListContainerStyle.Plain,
        )
    }
}
