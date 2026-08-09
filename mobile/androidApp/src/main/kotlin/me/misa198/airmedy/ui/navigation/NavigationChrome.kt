package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.AppDestination
import me.misa198.airmedy.player.PlaybackState

internal fun PlaybackState.showsMiniPlayer(): Boolean = when (this) {
    PlaybackState.Idle, is PlaybackState.Failed -> false
    is PlaybackState.Preparing, is PlaybackState.Playing, is PlaybackState.Paused -> true
}

@Composable
internal fun NavigationChrome(
    selectedDestination: AppDestination,
    playbackState: PlaybackState,
    hazeState: HazeState,
    compact: Boolean = false,
    onExpandClick: () -> Unit = {},
    onDestinationSelected: (AppDestination) -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onMiniPlayerDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompact = compact && playbackState.showsMiniPlayer()
    val geometryTransition = updateTransition(
        targetState = isCompact,
        label = "navigation-chrome-geometry",
    )
    BoxWithConstraints(
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
    ) {
        val compactMiniPlayerWidth = (maxWidth - CompactNavigationHeight - MiniPlayerNavigationGap).coerceAtLeast(0.dp)
        val navigationWidth by geometryTransition.animateDp(
            transitionSpec = { tween(420, easing = FastOutSlowInEasing) },
            label = "navigation-width",
        ) { compactLayout -> if (compactLayout) CompactNavigationHeight else maxWidth }
        val navigationHeight by geometryTransition.animateDp(
            transitionSpec = { tween(420, easing = FastOutSlowInEasing) },
            label = "navigation-height",
        ) { compactLayout -> if (compactLayout) CompactNavigationHeight else FloatingNavigationHeight }
        val fullNavigationContentAlpha by animateFloatAsState(
            targetValue = if (!isCompact && navigationWidth >= maxWidth * 0.85f) 1f else 0f,
            animationSpec = tween(160),
            label = "full-navigation-content-alpha",
        )
        val miniPlayerWidth by geometryTransition.animateDp(
            transitionSpec = { tween(420, easing = FastOutSlowInEasing) },
            label = "mini-player-width",
        ) { compactLayout -> if (compactLayout) compactMiniPlayerWidth else maxWidth }
        val miniPlayerHorizontalOffset by geometryTransition.animateDp(
            transitionSpec = { tween(420, easing = FastOutSlowInEasing) },
            label = "mini-player-horizontal-offset",
        ) { compactLayout -> if (compactLayout) CompactNavigationHeight + MiniPlayerNavigationGap else 0.dp }
        val miniPlayerVerticalOffset by geometryTransition.animateDp(
            transitionSpec = { tween(420, easing = FastOutSlowInEasing) },
            label = "mini-player-vertical-offset",
        ) { compactLayout -> if (compactLayout) 0.dp else -(FloatingNavigationHeight + MiniPlayerNavigationGap) }
        val chromeHeight by geometryTransition.animateDp(
            transitionSpec = { tween(420, easing = FastOutSlowInEasing) },
            label = "navigation-chrome-height",
        ) { compactLayout ->
            if (compactLayout) CompactNavigationHeight else FloatingNavigationHeight + MiniPlayerHeight + MiniPlayerNavigationGap
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(chromeHeight),
        ) {
            if (playbackState.showsMiniPlayer()) {
                MiniPlayer(
                    playbackState = playbackState,
                    hazeState = hazeState,
                    compact = isCompact,
                    onPreviousClick = onPreviousClick,
                    onPlayPauseClick = onPlayPauseClick,
                    onNextClick = onNextClick,
                    onDismiss = onMiniPlayerDismiss,
                    stableGlassWidth = maxWidth,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(miniPlayerWidth)
                        .offset(x = miniPlayerHorizontalOffset, y = miniPlayerVerticalOffset),
                )
            }
            FloatingNavigationBar(
                selectedDestination = selectedDestination,
                hazeState = hazeState,
                onDestinationSelected = onDestinationSelected,
                fullNavigationContentAlpha = fullNavigationContentAlpha,
                onCompactClick = onExpandClick,
                height = navigationHeight,
                stableGlassWidth = maxWidth,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(navigationWidth),
            )
        }
    }
}
