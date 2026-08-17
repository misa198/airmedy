package me.misa198.airmedy.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ContentScrollDirectionTest {
    @Test
    fun accumulatorOnlyRequestsRecompositionWhenChromeModeChanges() {
        val accumulator = NavigationChromeScrollAccumulator()

        assertEquals(false, accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 8f), 24f))
        assertEquals(false, accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 8f), 24f))
        assertEquals(true, accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 8f), 24f))
        assertEquals(true, accumulator.compact)
        assertEquals(false, accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 12f), 24f))
    }

    @Test
    fun accumulatorReversesPendingDistanceWithoutChangingMode() {
        val accumulator = NavigationChromeScrollAccumulator()

        accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 16f), 24f)
        assertEquals(false, accumulator.update(ContentScrollDelta(ContentScrollDirection.Down, 8f), 24f))
        assertEquals(false, accumulator.compact)
        assertEquals(false, accumulator.update(ContentScrollDelta(ContentScrollDirection.Down, 24f), 24f))
        assertEquals(false, accumulator.compact)
    }

    @Test
    fun mapsConsumedScrollDeltasToContentDirection() {
        assertEquals(ContentScrollDelta(ContentScrollDirection.Up, 1f), contentScrollDelta(-1f))
        assertEquals(ContentScrollDelta(ContentScrollDirection.Down, 1f), contentScrollDelta(1f))
        assertEquals(null, contentScrollDelta(0f))
    }

    @Test
    fun requiresContinuousScrollDistanceBeforeChangingChromeMode() {
        val thresholdPx = 24f
        val shortUpwardScroll = reduceNavigationChromeScroll(
            NavigationChromeScrollState(),
            ContentScrollDelta(ContentScrollDirection.Up, 8f),
            thresholdPx,
        )
        assertEquals(NavigationChromeScrollState(accumulatedDistancePx = -8f), shortUpwardScroll)

        val reversedScroll = reduceNavigationChromeScroll(
            shortUpwardScroll,
            ContentScrollDelta(ContentScrollDirection.Down, 8f),
            thresholdPx,
        )
        assertEquals(NavigationChromeScrollState(), reversedScroll)

        val compact = reduceNavigationChromeScroll(
            shortUpwardScroll,
            ContentScrollDelta(ContentScrollDirection.Up, 16f),
            thresholdPx,
        )
        assertEquals(NavigationChromeScrollState(compact = true), compact)
    }

    @Test
    fun preservesStateInstanceWhenScrollAlreadyMatchesChromeMode() {
        val compact = NavigationChromeScrollState(compact = true)

        assertSame(
            compact,
            reduceNavigationChromeScroll(
                compact,
                ContentScrollDelta(ContentScrollDirection.Up, 12f),
                thresholdPx = 24f,
            ),
        )

        val expanded = NavigationChromeScrollState()
        assertSame(
            expanded,
            reduceNavigationChromeScroll(
                expanded,
                ContentScrollDelta(ContentScrollDirection.Down, 12f),
                thresholdPx = 24f,
            ),
        )
    }
}
