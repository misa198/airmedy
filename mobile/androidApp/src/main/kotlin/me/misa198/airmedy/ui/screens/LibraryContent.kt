package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListItem

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
                    leadingIconRes = LucideR.drawable.lucide_ic_users,
                    leadingIconTint = colors.primary,
                    onClick = onArtistsSelected,
                ),
                ActionListItem(
                    labelRes = R.string.library_albums,
                    leadingIconRes = LucideR.drawable.lucide_ic_disc,
                    leadingIconTint = colors.primary,
                    onClick = onAlbumsSelected,
                ),
                ActionListItem(
                    labelRes = R.string.library_tracks,
                    leadingIconRes = LucideR.drawable.lucide_ic_music,
                    leadingIconTint = colors.primary,
                    onClick = onTracksSelected,
                ),
                ActionListItem(
                    labelRes = R.string.library_genres,
                    leadingIconRes = LucideR.drawable.lucide_ic_tags,
                    leadingIconTint = colors.primary,
                    onClick = onGenresSelected,
                ),
                ActionListItem(
                    labelRes = R.string.library_composers,
                    leadingIconRes = LucideR.drawable.lucide_ic_mic,
                    leadingIconTint = colors.primary,
                    onClick = onComposersSelected,
                ),
            ),
            containerStyle = ActionListContainerStyle.Plain,
        )
    }
}
