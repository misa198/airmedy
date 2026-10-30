package me.misa198.airmedy.ui.navigation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenPlayerSwipeTest {
    @Test
    fun excludesTheEntireMinimumTouchTargetWithoutExcludingSpaceAroundLargeButtons() {
        assertEquals(
            Rect(10f, 6f, 70f, 54f),
            fullScreenSwipeExclusionBounds(Rect(10f, 20f, 70f, 40f), Size(48f, 48f)),
        )
        val button = Rect(20f, 20f, 84f, 84f)
        assertEquals(button, fullScreenSwipeExclusionBounds(button, Size(48f, 48f)))
    }

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
