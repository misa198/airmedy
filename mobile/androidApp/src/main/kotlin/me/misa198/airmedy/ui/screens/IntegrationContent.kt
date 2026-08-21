package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.lastfm.LastFmStatus
import me.misa198.airmedy.ui.components.AirmedyPillButton
import me.misa198.airmedy.ui.components.AirmedyPillButtonVariant
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.rememberArtworkThumbnail
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun IntegrationContent(
    status: LastFmStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val avatar = rememberArtworkThumbnail(status.avatarPath)
    Column(modifier = modifier) {
        HeroCard(
            title = stringResource(R.string.lastfm_title),
            description = if (status.connected) {
                stringResource(R.string.lastfm_connected_as, status.username)
            } else {
                stringResource(R.string.lastfm_description)
            },
            bottomContent = {
                AirmedyPillButton(
                    label = stringResource(
                        when {
                            status.working -> R.string.lastfm_connecting
                            status.connected -> R.string.lastfm_disconnect
                            else -> R.string.lastfm_connect
                        },
                    ),
                    onClick = if (status.connected) onDisconnect else onConnect,
                    enabled = status.configured && !status.working,
                    variant = if (status.connected) AirmedyPillButtonVariant.Secondary else AirmedyPillButtonVariant.Primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                )
            },
        ) {
            if (status.connected && avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).testTag("lastfm-avatar"),
                )
            } else {
                MaterialSymbol(
                    symbol = MaterialSymbols.GraphicEq,
                    contentDescription = null,
                    size = 40.dp,
                    tint = colors.textMuted,
                    modifier = Modifier.testTag("lastfm-icon"),
                )
            }
        }
        if (!status.configured || status.failed) {
            Text(
                text = stringResource(if (status.configured) R.string.lastfm_error else R.string.lastfm_not_configured),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
    }
}
