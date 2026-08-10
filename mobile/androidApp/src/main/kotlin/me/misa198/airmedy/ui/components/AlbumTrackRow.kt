package me.misa198.airmedy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** Track row for detail pages: ordinal, metadata, then an independent overflow action. */
@Composable
fun AlbumTrackRow(
    number: Int,
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
) {
    val colors = LocalAirmedyColors.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(start = 22.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            number.toString(),
            Modifier.width(36.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted
        )
        Column(Modifier
            .weight(1f)
            .padding(end = 8.dp), verticalArrangement = Arrangement.Center) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                artist,
                Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onMoreClick?.invoke() }, modifier = Modifier.padding(0.dp)) {
            MaterialSymbol(
                MaterialSymbols.MoreVert,
                stringResource(R.string.album_track_row_more_options),
                size = 20.dp,
                tint = colors.textMuted
            )
        }
    }
}
