package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.R
import me.misa198.airmedy.lastfm.LastFmStatus
import me.misa198.airmedy.lyrics.LyricsSettings
import me.misa198.airmedy.lyrics.LyricsSource
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListDividerStyle
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.LabeledCard
import me.misa198.airmedy.ui.components.Selection
import me.misa198.airmedy.ui.components.SelectionOption
import me.misa198.airmedy.ui.components.AirmedyPillButton
import me.misa198.airmedy.ui.components.AirmedyPillButtonVariant
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.rememberArtworkThumbnail
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun IntegrationContent(
    onLastFmSelected: () -> Unit,
    onLyricsSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ActionList(
            items = listOf(
                ActionListItem(R.string.lastfm_title, onClick = onLastFmSelected),
                ActionListItem(R.string.lyrics_title, onClick = onLyricsSelected),
            ),
            containerStyle = ActionListContainerStyle.Card,
            dividerStyle = ActionListDividerStyle.FullWidth,
        )
    }
}

@Composable
internal fun LastFmContent(
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

@Composable
internal fun LyricsContent(
    settings: LyricsSettings,
    onSourceChanged: (LyricsSource) -> Unit,
    onLrclibChanged: (Boolean) -> Unit,
    onKugouChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LabeledCard(label = stringResource(R.string.lyrics_data_sources), modifier = modifier) {
        Selection(
            labelRes = R.string.lyrics_preferred_source,
            options = listOf(
                SelectionOption(LyricsSource.Desktop, R.string.lyrics_source_desktop),
                SelectionOption(LyricsSource.AutoFetch, R.string.lyrics_source_auto_fetch),
            ),
            selectedValue = settings.preferredSource,
            onValueSelected = onSourceChanged,
        )
        me.misa198.airmedy.ui.components.ActionListDivider(style = ActionListDividerStyle.FullWidth)
        ActionList(
            items = listOf(
                ActionListItem(R.string.lyrics_lrclib, trailingContent = { Switch(checked = settings.lrclib, onCheckedChange = onLrclibChanged) }, onClick = { onLrclibChanged(!settings.lrclib) }),
                ActionListItem(R.string.lyrics_kugou, trailingContent = { Switch(checked = settings.kugou, onCheckedChange = onKugouChanged) }, onClick = { onKugouChanged(!settings.kugou) }),
            ),
            containerStyle = ActionListContainerStyle.Plain,
            dividerStyle = ActionListDividerStyle.FullWidth,
        )
    }
}
