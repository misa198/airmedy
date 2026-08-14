package me.misa198.airmedy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
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
    tint: Color? = null,
    glassColor: Color? = null,
    hazeState: HazeState? = null,
    size: Dp = 48.dp,
    circleSize: Dp = size,
    iconSize: Dp = 22.dp,
    filled: Boolean = false,
    suppressPressedIndication: Boolean = false,
) {
    val colors = LocalAirmedyColors.current
    @Composable fun Content() {
        Box(
            modifier = Modifier
                .size(circleSize)
                .then(
                    if (variant == AirmedyIconButtonVariant.Glass) {
                        Modifier
                            .clip(CircleShape)
                            .border(1.dp, colors.borderGlass, CircleShape)
                            .then(
                                if (glassColor != null) Modifier.background(glassColor)
                                else Modifier.liquidGlassBackground(hazeState, colors)
                            )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol(
                symbol = symbol,
                contentDescription = label,
                tint = tint ?: if (enabled) colors.textMain else colors.textMuted,
                size = iconSize,
                filled = filled,
            )
        }
    }
    if (suppressPressedIndication) {
        Box(
            modifier = modifier
                .size(size.coerceAtLeast(48.dp))
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) { Content() }
    } else {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(size.coerceAtLeast(48.dp))) { Content() }
    }
}
