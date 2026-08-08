package me.misa198.airmedy.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "appearance")
private val ThemeModeKey = stringPreferencesKey("theme_mode")

enum class ThemeMode(val storageValue: String, val labelRes: Int) {
    System("system", me.misa198.airmedy.R.string.theme_system),
    Light("light", me.misa198.airmedy.R.string.theme_light),
    Dark("dark", me.misa198.airmedy.R.string.theme_dark),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: System
    }
}

interface ThemeModeStore {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(themeMode: ThemeMode)
}

class ThemePreferences(
    private val context: Context,
) : ThemeModeStore {
    override val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { preferences: Preferences ->
        ThemeMode.fromStorage(preferences[ThemeModeKey])
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[ThemeModeKey] = themeMode.storageValue
        }
    }
}
