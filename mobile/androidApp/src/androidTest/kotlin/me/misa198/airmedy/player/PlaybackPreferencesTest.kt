package me.misa198.airmedy.player

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPreferencesTest {
    @Test
    fun keepScreenOnForLyricsDefaultsOffAndPersists() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = booleanPreferencesKey("keep_screen_on_for_lyrics")
        val original = context.playbackPreferencesDataStore.data.first()[key]
        try {
            context.playbackPreferencesDataStore.edit { it.remove(key) }
            assertFalse(PlaybackPreferences(context).keepScreenOnForLyrics.first())
            PlaybackPreferences(context).setKeepScreenOnForLyrics(true)
            assertTrue(PlaybackPreferences(context).keepScreenOnForLyrics.first())
        } finally {
            context.playbackPreferencesDataStore.edit { preferences ->
                if (original == null) preferences.remove(key) else preferences[key] = original
            }
        }
    }
}
