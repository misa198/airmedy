package me.misa198.airmedy.ui.navigation

import kotlin.math.abs

internal data class NavigationChromeScrollState(
    val compact: Boolean = false,
    val accumulatedDistancePx: Float = 0f,
)

internal fun reduceNavigationChromeScroll(
    state: NavigationChromeScrollState,
    delta: ContentScrollDelta,
    thresholdPx: Float,
): NavigationChromeScrollState {
    val targetCompact = delta.direction == ContentScrollDirection.Up
    if (targetCompact == state.compact) {
        return state.copy(accumulatedDistancePx = 0f)
    }
    val signedDistance = if (targetCompact) -delta.distancePx else delta.distancePx
    val accumulatedDistance = if (
        state.accumulatedDistancePx != 0f && state.accumulatedDistancePx * signedDistance < 0f
    ) {
        signedDistance
    } else {
        state.accumulatedDistancePx + signedDistance
    }
    return if (abs(accumulatedDistance) >= thresholdPx) {
        NavigationChromeScrollState(compact = targetCompact)
    } else {
        state.copy(accumulatedDistancePx = accumulatedDistance)
    }
}
