package me.misa198.airmedy.ui.navigation

import kotlin.math.abs

internal data class NavigationChromeScrollState(
    val compact: Boolean = false,
    val accumulatedDistancePx: Float = 0f,
)

/**
 * Accumulates scroll distance without exposing every pointer delta as Compose
 * state. The app only needs recomposition when the chrome actually changes
 * compact/expanded mode.
 */
internal class NavigationChromeScrollAccumulator {
    var compact: Boolean = false
        private set
    private var accumulatedDistancePx: Float = 0f

    fun update(delta: ContentScrollDelta, thresholdPx: Float): Boolean {
        val targetCompact = delta.direction == ContentScrollDirection.Up
        if (targetCompact == compact) {
            accumulatedDistancePx = 0f
            return false
        }
        val signedDistance = if (targetCompact) -delta.distancePx else delta.distancePx
        accumulatedDistancePx = if (
            accumulatedDistancePx != 0f && accumulatedDistancePx * signedDistance < 0f
        ) signedDistance else accumulatedDistancePx + signedDistance
        if (abs(accumulatedDistancePx) < thresholdPx) return false
        compact = targetCompact
        accumulatedDistancePx = 0f
        return true
    }

    fun reset() {
        compact = false
        accumulatedDistancePx = 0f
    }
}

internal fun reduceNavigationChromeScroll(
    state: NavigationChromeScrollState,
    delta: ContentScrollDelta,
    thresholdPx: Float,
): NavigationChromeScrollState {
    val targetCompact = delta.direction == ContentScrollDirection.Up
    if (targetCompact == state.compact) {
        // Scroll events arrive for every consumed pointer delta. Once the
        // chrome already matches the direction, preserve the same state
        // instance instead of allocating a value that the state equality policy
        // would immediately discard. A pending reversal still needs to reset its
        // threshold accumulation.
        return if (state.accumulatedDistancePx == 0f) state
        else state.copy(accumulatedDistancePx = 0f)
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
