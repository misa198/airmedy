package me.misa198.airmedy.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** Full-width glass playback actions for a library collection. */
@Composable
fun LibraryPlaybackActions(
    playLabel: String,
    shuffleLabel: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LibraryPlaybackAction(
            symbol = MaterialSymbols.PlayArrow,
            label = playLabel,
            onClick = onPlay,
            hazeState = hazeState,
            modifier = Modifier.weight(1f),
        )
        LibraryPlaybackAction(
            symbol = MaterialSymbols.Shuffle,
            label = shuffleLabel,
            onClick = onShuffle,
            hazeState = hazeState,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LibraryPlaybackAction(
    symbol: String,
    label: String,
    onClick: () -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .border(1.dp, colors.borderGlass, shape)
            .liquidGlassBackground(hazeState, colors)
            .semantics { contentDescription = label }
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol(symbol, null, size = 20.dp, tint = colors.primary, filled = true)
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
    }
}
