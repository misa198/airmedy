package me.misa198.airmedy.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.playbackDataStore by preferencesDataStore(name = "playback_session")
private val QueueSnapshotKey = stringPreferencesKey("queue_snapshot")

/** Android-private persistence adapter; queue semantics remain in sharedLogic. */
internal class PlaybackSessionStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): PlaybackQueueSnapshot? = context.playbackDataStore.data.first()[QueueSnapshotKey]
        ?.let { encoded -> runCatching { json.decodeFromString<PlaybackQueueSnapshot>(encoded) }.getOrNull() }

    suspend fun save(snapshot: PlaybackQueueSnapshot) {
        context.playbackDataStore.edit { preferences ->
            preferences[QueueSnapshotKey] = json.encodeToString(snapshot)
        }
    }
}
