package me.misa198.airmedy.lyrics

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPreferencesTest {
    @Test
    fun glowDefaultsOnAndPersists() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = booleanPreferencesKey("lyrics_glow")
        val original = context.lyricsDataStore.data.first()[key]
        try {
            context.lyricsDataStore.edit { it.remove(key) }
            assertTrue(LyricsPreferences(context).settings.first().glowEnabled)
            LyricsPreferences(context).setGlowEnabled(false)
            assertFalse(LyricsPreferences(context).settings.first().glowEnabled)
            LyricsPreferences(context).setGlowEnabled(true)
            assertTrue(LyricsPreferences(context).settings.first().glowEnabled)
        } finally {
            context.lyricsDataStore.edit { preferences ->
                if (original == null) preferences.remove(key) else preferences[key] = original
            }
        }
    }
}
