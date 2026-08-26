package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeState
import me.misa198.airmedy.R
import me.misa198.airmedy.player.ArtworkCrossfadeTransition
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.components.AirmedyIconButton
import me.misa198.airmedy.ui.components.AirmedyIconButtonVariant
import me.misa198.airmedy.ui.components.AirmedyMarqueeText
import me.misa198.airmedy.ui.components.AirmedyPillButton
import me.misa198.airmedy.ui.components.AirmedyPillButtonVariant
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.components.TrackContextBottomSheetRequest
import me.misa198.airmedy.ui.components.TrackContextMenu
import me.misa198.airmedy.ui.components.TrackInfoValue
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

internal const val FullScreenPlayerMetadataSwipeTestTag = "full_screen_player_metadata_swipe_target"

@Composable
internal fun FullScreenPlayerMetadataTransition(
    item: PlaybackItem,
    crossfade: ArtworkCrossfadeTransition?,
    displayedHorizontalSwipeOffset: Float,
    hazeState: HazeState?,
    compact: Boolean,
    isFavorite: Boolean,
    onFavoriteToggle: (String, Boolean) -> Unit,
    contextTrack: LibraryTrack?,
    contextMenuExpanded: Boolean,
    onContextMenuOpen: () -> Unit,
    onContextMenuDismiss: () -> Unit,
    playbackQueue: PlaybackQueueSnapshot,
    onTrackPlayNext: (String) -> Unit,
    onTrackAddToQueue: (String) -> Unit,
    onTrackGoToAlbum: (String) -> Unit,
    onTrackGoToArtist: (String) -> Unit,
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit,
    onCloseFullscreenThen: ((() -> Unit) -> Unit),
) {
    androidx.compose.animation.AnimatedContent(
        targetState = item,
        transitionSpec = {
            if (crossfade != null) {
                (slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                    (slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(160, easing = FastOutSlowInEasing)))
            } else EnterTransition.None togetherWith ExitTransition.None
        },
        label = "full-screen-player-metadata-crossfade",
    ) { animatedItem ->
        FullScreenPlayerMetadata(
            animatedItem, displayedHorizontalSwipeOffset, hazeState, compact, isFavorite, onFavoriteToggle,
            contextTrack, contextMenuExpanded, onContextMenuOpen, onContextMenuDismiss, playbackQueue,
            onTrackPlayNext, onTrackAddToQueue, onTrackGoToAlbum, onTrackGoToArtist,
            onTrackContextBottomSheet, onCloseFullscreenThen,
        )
    }
}

@Composable
private fun FullScreenPlayerMetadata(
    item: PlaybackItem,
    displayedHorizontalSwipeOffset: Float,
    hazeState: HazeState?,
    compact: Boolean,
    isFavorite: Boolean,
    onFavoriteToggle: (String, Boolean) -> Unit,
    contextTrack: LibraryTrack?,
    contextMenuExpanded: Boolean,
    onContextMenuOpen: () -> Unit,
    onContextMenuDismiss: () -> Unit,
    playbackQueue: PlaybackQueueSnapshot,
    onTrackPlayNext: (String) -> Unit,
    onTrackAddToQueue: (String) -> Unit,
    onTrackGoToAlbum: (String) -> Unit,
    onTrackGoToArtist: (String) -> Unit,
    onTrackContextBottomSheet: (TrackContextBottomSheetRequest) -> Unit,
    onCloseFullscreenThen: ((() -> Unit) -> Unit),
) {
    val colors = LocalAirmedyColors.current
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val favoriteScale = remember(item.trackId) { Animatable(1f) }
    var previousFavorite by remember(item.trackId) { mutableStateOf(isFavorite) }
    LaunchedEffect(item.trackId, isFavorite) {
        val wasAdded = isFavorite && !previousFavorite
        previousFavorite = isFavorite
        if (wasAdded) {
            favoriteScale.snapTo(1f)
            favoriteScale.animateTo(1.14f, tween(120, easing = FastOutSlowInEasing))
            favoriteScale.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        } else if (!isFavorite) favoriteScale.snapTo(1f)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clipToBounds().graphicsLayer { translationX = displayedHorizontalSwipeOffset }
            .semantics { testTag = FullScreenPlayerMetadataSwipeTestTag }) {
            AirmedyMarqueeText(item.title, colors.onPrimary, if (compact) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            AirmedyMarqueeText(item.artist, colors.foregroundSubtle, if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(4.dp))
        if (!compact) AirmedyIconButton(
            symbol = if (isFavorite) MaterialSymbols.Favorite else MaterialSymbols.FavoriteBorder,
            label = stringResource(R.string.player_heart),
            onClick = { if (!isFavorite) hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm); onFavoriteToggle(item.trackId, !isFavorite) },
            modifier = Modifier.graphicsLayer { scaleX = favoriteScale.value; scaleY = favoriteScale.value },
            variant = AirmedyIconButtonVariant.Glass, tint = colors.onPrimary,
            glassColor = fullScreenSecondaryControlBackground(colors), hazeState = hazeState, showGlassBorder = false,
            circleSize = 36.dp, iconSize = 20.dp, filled = isFavorite, suppressPressedIndication = true,
        )
        @Composable fun moreButton(onClick: () -> Unit) = AirmedyIconButton(
            MaterialSymbols.MoreVert, stringResource(R.string.player_more), onClick,
            variant = AirmedyIconButtonVariant.Glass, tint = colors.onPrimary,
            glassColor = fullScreenSecondaryControlBackground(colors), hazeState = hazeState, showGlassBorder = false,
            circleSize = if (compact) 32.dp else 36.dp, iconSize = if (compact) 18.dp else 20.dp,
        )
        if (contextTrack == null) moreButton({}) else TrackContextMenu(
            track = contextTrack, expanded = contextMenuExpanded, onDismiss = onContextMenuDismiss, hazeState = hazeState,
            playbackQueue = playbackQueue, onPlayNext = { onTrackPlayNext(it.id) }, onAddToQueue = { onTrackAddToQueue(it.id) },
            onFavoriteChange = { track, favorite -> onFavoriteToggle(track.id, favorite) }, onGoToAlbum = { onTrackGoToAlbum(it.albumId) },
            onGoToArtist = { onTrackGoToArtist(it.id) },
            onBottomSheetRequested = { onCloseFullscreenThen { onTrackContextBottomSheet(it) } },
            onCloseFullscreenThen = onCloseFullscreenThen,
        ) { moreButton(onContextMenuOpen) }
    }
}

@Composable
internal fun FullScreenQualityDialog(labelRes: Int, symbol: String, details: List<TrackInfoValue>, onDismiss: () -> Unit) {
    val colors = LocalAirmedyColors.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(colors.card, RoundedCornerShape(24.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbol(symbol, null, size = 32.dp, tint = colors.textMain)
                    Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.textMain)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    details.forEach { detail -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(detail.labelRes), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                        Text(detail.value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colors.textMain)
                    } }
                }
            }
            AirmedyPillButton(stringResource(R.string.ok), onDismiss, AirmedyPillButtonVariant.Primary, Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp))
        }
    }
}
