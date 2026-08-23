package me.misa198.airmedy.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsSourceTest {
    @Test
    fun selectsConfiguredSourceAndFallsBackToTheOther() {
        assertEquals("desktop", preferredLyrics(LyricsSource.Desktop, "desktop", "provider"))
        assertEquals("provider", preferredLyrics(LyricsSource.AutoFetch, "desktop", "provider"))
        assertEquals("desktop", preferredLyrics(LyricsSource.AutoFetch, "desktop", null))
        assertEquals("provider", preferredLyrics(LyricsSource.Desktop, null, "provider"))
    }
}
