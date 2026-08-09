package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
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
    onDestinationSelected: (AppDestination) -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onMiniPlayerDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MiniPlayerNavigationGap),
    ) {
        AnimatedVisibility(
            visible = playbackState.showsMiniPlayer(),
            enter = expandVertically(animationSpec = tween(250), expandFrom = Alignment.Bottom) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(250), shrinkTowards = Alignment.Bottom) + fadeOut(animationSpec = tween(200)),
        ) {
            MiniPlayer(
                playbackState = playbackState,
                hazeState = hazeState,
                onPreviousClick = onPreviousClick,
                onPlayPauseClick = onPlayPauseClick,
                onNextClick = onNextClick,
                onDismiss = onMiniPlayerDismiss,
            )
        }
        FloatingNavigationBar(selectedDestination, hazeState, onDestinationSelected)
    }
}
