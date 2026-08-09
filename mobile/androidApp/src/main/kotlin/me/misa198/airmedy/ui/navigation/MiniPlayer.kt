package me.misa198.airmedy.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import com.composables.icons.lucide.R as LucideR
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.ui.components.liquidGlassBackground
import me.misa198.airmedy.ui.components.rememberArtworkThumbnail
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal val MiniPlayerHeight = 56.dp
internal val MiniPlayerNavigationGap = 8.dp
private val MiniPlayerPillRadius = 30.dp

@Composable
internal fun MiniPlayer(
    playbackState: PlaybackState,
    hazeState: HazeState,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = playbackState.itemOrNull() ?: return
    val colors = LocalAirmedyColors.current
    val shape = RoundedCornerShape(MiniPlayerPillRadius)
    val isPlaying = playbackState is PlaybackState.Playing
    val isPreparing = playbackState is PlaybackState.Preparing
    val artwork = rememberArtworkThumbnail(item.artworkPath)
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val dragOffset = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 36.dp.toPx() }
    val dismissTargetPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .offset { IntOffset(0, dragOffset.value.roundToInt()) }
            .clip(shape)
            .liquidGlassBackground(hazeState, colors)
            .border(1.dp, colors.borderGlass, shape)
            .pointerInput(dismissThresholdPx, dismissTargetPx) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { dragOffset.animateTo(0f, tween(200)) }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            if (dragOffset.value >= dismissThresholdPx) {
                                dragOffset.animateTo(dismissTargetPx, tween(250))
                                onDismiss()
                            } else {
                                dragOffset.animateTo(0f, tween(200))
                            }
                        }
                    },
                )
            }
            .padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.glassElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_music),
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            MarqueeText(item.title, colors.textMain, MaterialTheme.typography.bodyMedium)
            MarqueeText(item.artist, colors.textMuted, MaterialTheme.typography.bodySmall)
        }
        MiniPlayerControl(
            iconRes = R.drawable.ic_player_previous_filled,
            label = stringResource(R.string.player_previous),
            onClick = onPreviousClick,
            horizontalOffset = 16.dp,
        )
        MiniPlayerControl(
            iconRes = if (isPlaying) R.drawable.ic_player_pause_filled else R.drawable.ic_player_play_filled,
            label = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
            onClick = onPlayPauseClick,
            enabled = !isPreparing,
            horizontalOffset = 8.dp,
        )
        MiniPlayerControl(
            iconRes = R.drawable.ic_player_next_filled,
            label = stringResource(R.string.player_next),
            onClick = onNextClick,
        )
    }
}

@Composable
private fun MarqueeText(text: String, color: Color, style: TextStyle) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().basicMarquee(animationMode = MarqueeAnimationMode.Immediately),
        color = color,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun MiniPlayerControl(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    horizontalOffset: Dp = 0.dp,
) {
    val colors = LocalAirmedyColors.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).offset(x = horizontalOffset),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (enabled) colors.textMain else colors.textMuted,
            modifier = Modifier.size(26.dp),
        )
    }
}

private fun PlaybackState.itemOrNull(): PlaybackItem? = when (this) {
    PlaybackState.Idle, is PlaybackState.Failed -> null
    is PlaybackState.Preparing -> item
    is PlaybackState.Playing -> item
    is PlaybackState.Paused -> item
}
