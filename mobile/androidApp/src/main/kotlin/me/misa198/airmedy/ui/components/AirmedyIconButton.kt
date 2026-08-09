package me.misa198.airmedy.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

enum class AirmedyIconButtonVariant { Ghost, Glass }

/** A theme-safe icon-only action with either an unfilled or liquid-glass surface. */
@Composable
fun AirmedyIconButton(
    symbol: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: AirmedyIconButtonVariant = AirmedyIconButtonVariant.Ghost,
    hazeState: HazeState? = null,
) {
    val colors = LocalAirmedyColors.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (variant == AirmedyIconButtonVariant.Glass) {
                        Modifier
                            .clip(CircleShape)
                            .border(1.dp, colors.borderGlass, CircleShape)
                            .liquidGlassBackground(hazeState, colors)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol(
                symbol = symbol,
                contentDescription = label,
                tint = if (enabled) colors.textMain else colors.textMuted,
                size = 22.dp,
            )
        }
    }
}
