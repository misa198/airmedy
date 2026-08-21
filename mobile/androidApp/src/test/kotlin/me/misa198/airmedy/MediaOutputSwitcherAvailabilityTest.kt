package me.misa198.airmedy

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaOutputSwitcherAvailabilityTest {
    @Test
    fun supportsTheSystemMediaOutputSwitcherFromAndroid14() {
        assertTrue(canShowSystemMediaOutputSwitcher(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
    }

    @Test
    fun doesNotSupportTheSystemMediaOutputSwitcherBeforeAndroid14() {
        assertFalse(canShowSystemMediaOutputSwitcher(Build.VERSION_CODES.UPSIDE_DOWN_CAKE - 1))
    }

    @Test
    fun normalizesTheSystemMusicVolumeForTheFullscreenSlider() {
        assertEquals(0.5f, normalizeSystemMusicVolume(current = 5, maximum = 10), 0f)
        assertEquals(1f, normalizeSystemMusicVolume(current = 12, maximum = 10), 0f)
    }

    @Test
    fun predictsHardwareMusicVolumeChangesImmediately() {
        assertEquals(0.6f, adjustSystemMusicVolume(current = 0.5f, maximum = 10, direction = 1), 0f)
        assertEquals(0f, adjustSystemMusicVolume(current = 0f, maximum = 10, direction = -1), 0f)
        assertEquals(1f, adjustSystemMusicVolume(current = 1f, maximum = 10, direction = 1), 0f)
    }
}
