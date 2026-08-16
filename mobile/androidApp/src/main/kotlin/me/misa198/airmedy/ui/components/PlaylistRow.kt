package me.misa198.airmedy.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun PlaylistRow(
    playlistId: String,
    name: String,
    artworkPaths: List<String>,
    modifier: Modifier = Modifier,
    syncFailed: Boolean = false,
    onClick: () -> Unit = {},
) {
    val colors = LocalAirmedyColors.current
    Row(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(start = 24.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistArtwork(playlistId, artworkPaths)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (syncFailed) {
                Text(
                    text = stringResource(R.string.playlist_sync_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            MaterialSymbol(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = stringResource(R.string.playlist_row_open),
                size = 20.dp,
                tint = colors.textMuted,
            )
        }
    }
}

@Composable
internal fun PlaylistArtwork(playlistId: String, artworkPaths: List<String>, modifier: Modifier = Modifier, size: Dp = 110.dp) {
    val colors = LocalAirmedyColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.glassElevated)
            .border(1.dp, colors.borderGlass, RoundedCornerShape(10.dp))
            .then(if (artworkPaths.size >= 4) Modifier.testTag("playlist-artwork-mosaic") else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val targetPx = if (size > 110.dp) 480 else 128
        when {
            artworkPaths.size >= 4 -> {
                Column(Modifier.fillMaxSize()) {
                    PlaylistArtworkPair(artworkPaths.take(2), targetPx)
                    PlaylistArtworkPair(artworkPaths.drop(2).take(2), targetPx)
                }
            }
            artworkPaths.isNotEmpty() -> PlaylistArtworkImage(artworkPaths.first(), Modifier.fillMaxSize(), targetPx)
            else -> MaterialSymbol(
                symbol = if (playlistId == "favorites") MaterialSymbols.Favorite else MaterialSymbols.QueueMusic,
                contentDescription = null,
                size = 30.dp,
                tint = colors.textMuted,
                filled = playlistId == "favorites",
            )
        }
    }
}

@Composable
private fun ColumnScope.PlaylistArtworkPair(paths: List<String>, targetPx: Int) {
    Row(Modifier.fillMaxWidth().weight(1f)) {
        paths.forEach { path -> PlaylistArtworkImage(path, Modifier.weight(1f).fillMaxSize(), targetPx) }
    }
}

@Composable
private fun PlaylistArtworkImage(path: String, modifier: Modifier, targetPx: Int) {
    val bitmap = rememberArtworkThumbnail(path, targetPx = targetPx)
    if (bitmap != null) Image(bitmap, null, modifier = modifier, contentScale = ContentScale.Crop)
}
