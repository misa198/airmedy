package me.misa198.airmedy.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.updateNoticeDataStore by preferencesDataStore(name = "update_notices")
private val ShownVersions = stringSetPreferencesKey("shown_versions")

internal class UpdateNoticePreferences(private val store: DataStore<Preferences>) : UpdateNoticeStore {
    constructor(context: Context) : this(context.applicationContext.updateNoticeDataStore)

    override suspend fun wasShown(version: String): Boolean = version in store.data.first()[ShownVersions].orEmpty()

    override suspend fun markShown(version: String): Boolean {
        var added = false
        store.edit { preferences ->
            val shown = preferences[ShownVersions].orEmpty()
            if (version !in shown) {
                preferences[ShownVersions] = shown + version
                added = true
            }
        }
        return added
    }
}
