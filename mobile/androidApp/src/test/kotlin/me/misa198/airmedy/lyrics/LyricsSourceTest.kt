package me.misa198.airmedy.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSourceTest {
    @Test
    fun glowDefaultsOffAndAllowsExplicitOptIn() {
        assertFalse(LyricsSettings().glowEnabled)
        assertTrue(LyricsSettings(glowEnabled = true).glowEnabled)
    }

    @Test
    fun selectsConfiguredSourceAndFallsBackToTheOther() {
        assertEquals("desktop", preferredLyrics(LyricsSource.Desktop, "desktop", "provider"))
        assertEquals("provider", preferredLyrics(LyricsSource.AutoFetch, "desktop", "provider"))
        assertEquals("desktop", preferredLyrics(LyricsSource.AutoFetch, "desktop", null))
        assertEquals("provider", preferredLyrics(LyricsSource.Desktop, null, "provider"))
    }
}
