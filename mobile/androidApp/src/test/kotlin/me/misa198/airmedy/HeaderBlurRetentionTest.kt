package me.misa198.airmedy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderBlurRetentionTest {

    @Test
    fun homeGreetingMatchesDesktopTimeRanges() {
        assertEquals(R.string.home_greeting_morning, homeGreetingTitleRes(11))
        assertEquals(R.string.home_greeting_afternoon, homeGreetingTitleRes(12))
        assertEquals(R.string.home_greeting_evening, homeGreetingTitleRes(17))
        assertEquals(R.string.home_greeting_night, homeGreetingTitleRes(21))
    }
    @Test
    fun keepsThePreviousBlurForTheFirstCompositionOfANewDestination() {
        assertTrue(
            shouldShowHeaderBlur(
                isContentScrolled = false,
                destinationChanged = true,
                previousHeaderWasBlurred = true,
            ),
        )
    }

    @Test
    fun clearsBlurAfterTheNewDestinationHasComposed() {
        assertFalse(
            shouldShowHeaderBlur(
                isContentScrolled = false,
                destinationChanged = false,
                previousHeaderWasBlurred = true,
            ),
        )
    }
}
