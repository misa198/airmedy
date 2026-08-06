package me.misa198.airmedy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderBlurRetentionTest {
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
