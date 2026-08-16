package me.misa198.airmedy.ui.screens

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryAlbumsPreferencesDataStore by preferencesDataStore(name = "library_albums")
private val AlbumLayoutModeKey = stringPreferencesKey("layout_mode")

internal interface LibraryAlbumsLayoutStore {
    val layoutMode: Flow<AlbumLayoutMode>

    suspend fun setLayoutMode(layoutMode: AlbumLayoutMode)
}

/** Persists the user's Albums presentation independently from sync data. */
internal class LibraryAlbumsLayoutPreferences(
    private val context: Context,
) : LibraryAlbumsLayoutStore {
    override val layoutMode: Flow<AlbumLayoutMode> = context.libraryAlbumsPreferencesDataStore.data.map { preferences ->
        AlbumLayoutMode.fromStorage(preferences[AlbumLayoutModeKey])
    }

    override suspend fun setLayoutMode(layoutMode: AlbumLayoutMode) {
        context.libraryAlbumsPreferencesDataStore.edit { preferences ->
            preferences[AlbumLayoutModeKey] = layoutMode.storageValue
        }
    }
}
