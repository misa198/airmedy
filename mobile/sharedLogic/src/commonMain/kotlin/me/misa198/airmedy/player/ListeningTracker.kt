package me.misa198.airmedy.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListeningSession(
    val id: String,
    @SerialName("source_device_id") val sourceDeviceId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("ended_at") val endedAt: Long,
    @SerialName("listened_seconds") val listenedSeconds: Int,
    @SerialName("qualified_play") val qualifiedPlay: Boolean,
)

@Serializable
enum class PlaybackEndReason { @SerialName("completed") COMPLETED, @SerialName("skipped") SKIPPED, @SerialName("stopped") STOPPED }

@Serializable
data class PlaybackAttempt(
    val id: String,
    @SerialName("source_device_id") val sourceDeviceId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("ended_at") val endedAt: Long = 0,
    @SerialName("start_position_ms") val startPositionMs: Long,
    @SerialName("listened_seconds") val listenedSeconds: Int = 0,
    @SerialName("end_reason") val endReason: PlaybackEndReason? = null,
)

@Serializable
data class DailyTrackListeningStat(
    @SerialName("source_device_id") val sourceDeviceId: String,
    @SerialName("local_date") val localDate: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("listened_seconds") val listenedSeconds: Int,
    @SerialName("play_count") val playCount: Int,
)

@Serializable
data class DailyPlaybackAttemptStat(
    @SerialName("source_device_id") val sourceDeviceId: String,
    @SerialName("local_date") val localDate: String,
    val attempts: Int,
    val completed: Int,
    val skipped: Int,
    val stopped: Int,
    @SerialName("listened_seconds") val listenedSeconds: Int,
)

sealed interface ListeningWrite {
    data class Session(val value: ListeningSession) : ListeningWrite
    data class AttemptStarted(val value: PlaybackAttempt) : ListeningWrite
    data class AttemptFinished(val value: PlaybackAttempt) : ListeningWrite
    data class QualifiedPlay(val sourceDeviceId: String, val trackId: String, val occurredAt: Long) : ListeningWrite
}

fun qualifiesPlayback(positionMs: Long, durationMs: Long): Boolean =
    durationMs >= 30_000 && (positionMs >= durationMs / 2 || positionMs >= 240_000)

/** Pure playback accounting; callers supply wall and monotonic time. */
class ListeningTracker(
    private val sourceDeviceId: String,
    private val nextId: () -> String,
) {
    private var trackId: String? = null
    private var sessionId = ""
    private var sessionStartedAt = 0L
    private var activeAt = 0L
    private var listenedMs = 0L
    private var playQualified = false
    private var sessionQualified = false
    private var attempt: PlaybackAttempt? = null
    private var attemptActiveAt = 0L
    private var attemptListenedMs = 0L
    private var pendingListeningDeductionMs = 0L

    val activeTrackId: String? get() = trackId

    fun start(trackId: String, positionMs: Long, wallMs: Long, elapsedMs: Long): List<ListeningWrite> {
        this.trackId = trackId
        sessionId = nextId()
        sessionStartedAt = wallMs
        activeAt = elapsedMs
        listenedMs = 0
        playQualified = false
        sessionQualified = false
        attemptActiveAt = elapsedMs
        attemptListenedMs = 0
        pendingListeningDeductionMs = 0
        val value = PlaybackAttempt(nextId(), sourceDeviceId, trackId, wallMs, startPositionMs = positionMs)
        attempt = value
        return listOf(ListeningWrite.AttemptStarted(value))
    }

    fun tick(positionMs: Long, durationMs: Long, wallMs: Long, elapsedMs: Long): List<ListeningWrite> {
        val id = trackId ?: return emptyList()
        val writes = mutableListOf<ListeningWrite>()
        if (!playQualified && qualifiesPlayback(positionMs, durationMs)) {
            playQualified = true
            sessionQualified = true
            writes += ListeningWrite.QualifiedPlay(sourceDeviceId, id, wallMs)
        }
        if (wallMs - sessionStartedAt >= 60_000) {
            flushActive(elapsedMs)
            session(wallMs)?.let(writes::add)
            sessionId = nextId()
            sessionStartedAt = wallMs
            activeAt = elapsedMs
            listenedMs = 0
            sessionQualified = false
        }
        return writes
    }

    fun pause(wallMs: Long, elapsedMs: Long): List<ListeningWrite> {
        if (trackId == null) return emptyList()
        flushActive(elapsedMs)
        val write = session(wallMs)
        sessionId = ""
        activeAt = 0
        if (attemptActiveAt != 0L) {
            attemptListenedMs += (elapsedMs - attemptActiveAt).coerceAtLeast(0)
            attemptActiveAt = 0
        }
        return listOfNotNull(write)
    }

    fun resume(wallMs: Long, elapsedMs: Long) {
        if (trackId == null || activeAt != 0L) return
        sessionId = nextId()
        sessionStartedAt = wallMs
        listenedMs = 0
        sessionQualified = false
        activeAt = elapsedMs
        attemptActiveAt = elapsedMs
    }

    fun suspendForInterruption(elapsedMs: Long) {
        if (trackId == null) return
        flushActive(elapsedMs)
        if (attemptActiveAt != 0L) {
            attemptListenedMs += (elapsedMs - attemptActiveAt).coerceAtLeast(0)
            attemptActiveAt = 0
        }
        activeAt = 0
    }

    fun resumeAfterInterruption(elapsedMs: Long) {
        if (trackId == null || activeAt != 0L) return
        activeAt = elapsedMs
        attemptActiveAt = elapsedMs
    }

    fun finish(reason: PlaybackEndReason, wallMs: Long, elapsedMs: Long): List<ListeningWrite> {
        val writes = pause(wallMs, elapsedMs).toMutableList()
        attempt?.let {
            writes += ListeningWrite.AttemptFinished(
                it.copy(endedAt = wallMs, listenedSeconds = (attemptListenedMs / 1_000).toInt(), endReason = reason),
            )
        }
        clear()
        return writes
    }

    fun splitCrossfadeOverlap(outgoingTrackId: String, startedAtMs: Long, overlapMs: Long): List<ListeningWrite> {
        val halfMs = overlapMs.coerceAtLeast(0) / 2
        pendingListeningDeductionMs += halfMs
        val seconds = (halfMs / 1_000).toInt()
        if (seconds <= 0) return emptyList()
        return listOf(ListeningWrite.Session(ListeningSession(nextId(), sourceDeviceId, outgoingTrackId, startedAtMs, startedAtMs + overlapMs, seconds, false)))
    }

    private fun flushActive(elapsedMs: Long) {
        if (activeAt != 0L) {
            listenedMs += (elapsedMs - activeAt).coerceAtLeast(0)
            listenedMs = (listenedMs - pendingListeningDeductionMs).coerceAtLeast(0)
            pendingListeningDeductionMs = 0
            activeAt = elapsedMs
        }
    }

    private fun session(wallMs: Long): ListeningWrite.Session? {
        val id = trackId ?: return null
        val seconds = (listenedMs / 1_000).toInt()
        if (sessionId.isEmpty() || seconds < 10) return null
        return ListeningWrite.Session(ListeningSession(sessionId, sourceDeviceId, id, sessionStartedAt, wallMs, seconds, sessionQualified))
    }

    private fun clear() {
        trackId = null
        sessionId = ""
        activeAt = 0
        listenedMs = 0
        playQualified = false
        sessionQualified = false
        attempt = null
        attemptActiveAt = 0
        attemptListenedMs = 0
        pendingListeningDeductionMs = 0
    }
}
