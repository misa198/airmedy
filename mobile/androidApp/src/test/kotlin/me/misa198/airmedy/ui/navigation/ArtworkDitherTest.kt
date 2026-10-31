package me.misa198.airmedy.ui.navigation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkDitherTest {
    @Test fun noiseIsAchromaticBalancedAndStableBetweenFrames() {
        val pixels = artworkDitherPixels()
        val histogram = IntArray(256)
        pixels.forEach { pixel ->
            val red = (pixel ushr 16) and 255
            assertEquals(255, pixel ushr 24)
            assertEquals(red, (pixel ushr 8) and 255)
            assertEquals(red, pixel and 255)
            histogram[red]++
        }
        assertTrue("Avoid repeating coarse levels in the dither", histogram.all { it > 0 })
        val average = pixels.map { it and 255 }.average()
        assertEquals("Noise must not introduce a brightness bias", 127.5, average, 2.0)
        assertArrayEquals("Static dither must not shimmer during playback", pixels, artworkDitherPixels())
    }
}
