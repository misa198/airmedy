package me.misa198.airmedy.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
fun AlbumRow(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    artworkPath: String? = null,
    onClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
) {
    val colors = LocalAirmedyColors.current
    val bitmap = rememberArtworkThumbnail(artworkPath)
    val clickModifier = remember(onClick) {
        if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(top = 6.dp, end = 8.dp, bottom = 6.dp, start = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.glassElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                MaterialSymbol(
                    symbol = MaterialSymbols.Album,
                    contentDescription = null,
                    size = 22.dp,
                    tint = colors.textMuted,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        IconButton(
            onClick = { onMoreClick?.invoke() },
            modifier = Modifier.size(48.dp),
        ) {
            MaterialSymbol(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = stringResource(R.string.album_row_open),
                size = 20.dp,
                tint = colors.textMuted,
            )
        }
    }
}
