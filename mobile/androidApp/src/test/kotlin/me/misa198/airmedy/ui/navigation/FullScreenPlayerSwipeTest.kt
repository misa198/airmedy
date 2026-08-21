package me.misa198.airmedy.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenPlayerSwipeTest {
    @Test
    fun rejectsVerticalGesturesEvenWhenTheyContainHorizontalJitter() {
        assertFalse(
            shouldDispatchFullScreenSwipe(
                horizontalDistancePx = 80f,
                verticalDistancePx = 140f,
                thresholdPx = 52f,
                velocityPxPerMs = 2f,
                velocityThresholdPxPerMs = 1.2f,
                velocityMinimumPx = 28f,
            ),
        )
    }

    @Test
    fun requiresMoreThanAShortHorizontalFlick() {
        assertFalse(
            shouldDispatchFullScreenSwipe(
                horizontalDistancePx = 18f,
                verticalDistancePx = 2f,
                thresholdPx = 52f,
                velocityPxPerMs = 2f,
                velocityThresholdPxPerMs = 1.2f,
                velocityMinimumPx = 28f,
            ),
        )
    }

    @Test
    fun acceptsDeliberateHorizontalSwipe() {
        assertTrue(
            shouldDispatchFullScreenSwipe(
                horizontalDistancePx = -64f,
                verticalDistancePx = 8f,
                thresholdPx = 52f,
                velocityPxPerMs = -0.4f,
                velocityThresholdPxPerMs = 1.2f,
                velocityMinimumPx = 28f,
            ),
        )
    }
}
