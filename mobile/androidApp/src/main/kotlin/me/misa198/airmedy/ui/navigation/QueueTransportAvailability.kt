package me.misa198.airmedy.ui.navigation

import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.RepeatMode

/** Transport affordance policy for the active queue order. */
internal fun PlaybackQueueSnapshot.canNavigatePrevious(): Boolean =
    activeTrackIds.isEmpty() || repeatMode != RepeatMode.Off || currentIndex > 0

internal fun PlaybackQueueSnapshot.canNavigateNext(): Boolean =
    activeTrackIds.isEmpty() || repeatMode != RepeatMode.Off || currentIndex in 0 until activeTrackIds.lastIndex

internal fun canDispatchQueueSwipe(
    swipeDirection: Int,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
): Boolean = when {
    swipeDirection < 0 -> canNavigateNext
    swipeDirection > 0 -> canNavigatePrevious
    else -> false
}
