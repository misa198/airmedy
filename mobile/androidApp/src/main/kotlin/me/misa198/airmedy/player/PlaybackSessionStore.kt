package me.misa198.airmedy.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private val Context.playbackDataStore by preferencesDataStore(name = "playback_session")
private val QueueSnapshotKey = stringPreferencesKey("queue_snapshot")

/** Everything needed to reopen the current item without resuming audio automatically. */
internal data class PlaybackSession(
    val queue: PlaybackQueueSnapshot = PlaybackQueueSnapshot(),
    val positionMs: Long = 0L,
)

private val PlaybackSessionJson = Json { ignoreUnknownKeys = true }

/** Accept queue-only sessions written before position persistence was added. */
internal fun decodePlaybackSession(encoded: String): PlaybackSession {
    val root = PlaybackSessionJson.parseToJsonElement(encoded).jsonObject
    return if ("queue" in root) {
        PlaybackSession(
            queue = PlaybackSessionJson.decodeFromJsonElement(root.getValue("queue")),
            positionMs = root["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    } else {
        PlaybackSession(queue = PlaybackSessionJson.decodeFromString<PlaybackQueueSnapshot>(encoded))
    }
}

internal fun encodePlaybackSession(session: PlaybackSession): String = buildJsonObject {
    put("queue", PlaybackSessionJson.encodeToJsonElement(session.queue))
    put("positionMs", session.positionMs)
}.toString()

/** Android-private persistence adapter; queue semantics remain in sharedLogic. */
internal class PlaybackSessionStore(private val context: Context) {
    suspend fun load(): PlaybackSession? {
        val encoded = context.playbackDataStore.data.first()[QueueSnapshotKey] ?: return null
        return runCatching { decodePlaybackSession(encoded) }
            .getOrElse {
                // A corrupt or obsolete payload must not trap every subsequent
                // app launch in the same failed restoration attempt.
                clear()
                null
            }
    }

    suspend fun save(session: PlaybackSession) {
        context.playbackDataStore.edit { preferences ->
            preferences[QueueSnapshotKey] = encodePlaybackSession(session)
        }
    }

    suspend fun clear() {
        context.playbackDataStore.edit { preferences -> preferences.remove(QueueSnapshotKey) }
    }
}
