package me.misa198.airmedy.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.misa198.airmedy.player.DailyPlaybackAttemptStat
import me.misa198.airmedy.player.DailyTrackListeningStat
import me.misa198.airmedy.player.ListeningSession
import me.misa198.airmedy.player.PlaybackAttempt

@Serializable
data class ListeningSyncSnapshot(
    val version: Int = 1,
    @SerialName("reconciliation_id") val reconciliationId: String,
    val sessions: List<ListeningSession> = emptyList(),
    val attempts: List<PlaybackAttempt> = emptyList(),
    @SerialName("daily_tracks") val dailyTracks: List<DailyTrackListeningStat> = emptyList(),
    @SerialName("daily_attempts") val dailyAttempts: List<DailyPlaybackAttemptStat> = emptyList(),
    val signature: String = "",
)

object ListeningSyncProtocol {
    fun signingInput(snapshot: ListeningSyncSnapshot): ByteArray =
        LibrarySyncProtocol.json.encodeToString(ListeningSyncSnapshot.serializer(), snapshot.copy(signature = "")).encodeToByteArray()
}

interface ListeningSyncStore {
    suspend fun listeningSnapshot(reconciliationId: String, sinceMs: Long): ListeningSyncSnapshot
    suspend fun mergeListeningSnapshot(snapshot: ListeningSyncSnapshot)
}
