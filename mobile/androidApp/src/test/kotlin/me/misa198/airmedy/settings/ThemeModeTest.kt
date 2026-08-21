package me.misa198.airmedy.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `uses system mode for missing or unknown stored values`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage("oled"))
    }

    @Test
    fun `restores each supported stored theme value`() {
        ThemeMode.entries.forEach { expected ->
            assertEquals(expected, ThemeMode.fromStorage(expected.storageValue))
        }
    }
}
