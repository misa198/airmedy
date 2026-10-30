package me.misa198.airmedy

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerOrientationTest {
    @Test fun onlyFullscreenAllowsUserRotation() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, playerRequestedOrientation(true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, playerRequestedOrientation(false))
    }
}
