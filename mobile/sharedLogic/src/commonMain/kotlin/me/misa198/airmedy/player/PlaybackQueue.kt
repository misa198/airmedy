package me.misa198.airmedy.player

import kotlin.random.Random
import kotlinx.serialization.Serializable

/** Platform-neutral queue and repeat rules shared by Android and future iOS adapters. */
@Serializable
enum class RepeatMode { Off, One, All }

@Serializable
data class PlaybackRequest(
    val trackIds: List<String>,
    val startIndex: Int = 0,
) {
    init {
        require(trackIds.isNotEmpty()) { "A playback request needs at least one track" }
        require(startIndex in trackIds.indices) { "Start index is outside the queue" }
    }
}

@Serializable
data class PlaybackQueueSnapshot(
    val originalTrackIds: List<String> = emptyList(),
    val activeTrackIds: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
) {
    val currentTrackId: String? get() = activeTrackIds.getOrNull(currentIndex)
}

sealed interface QueueTransition {
    data class Play(val trackId: String) : QueueTransition
    data object Stop : QueueTransition
    data object Unchanged : QueueTransition
}

/**
 * Stateful, UI-free implementation of the queue contract in the player catalog.
 * Callers serialize mutations; the class deliberately has no Android/iOS dependencies.
 */
class PlaybackQueue(private val random: Random = Random.Default) {
    private var original = mutableListOf<String>()
    private var active = mutableListOf<String>()
    private var currentIndex = -1
    private var shuffle = false
    private var repeatMode = RepeatMode.Off

    fun snapshot(): PlaybackQueueSnapshot = PlaybackQueueSnapshot(
        originalTrackIds = original.toList(),
        activeTrackIds = active.toList(),
        currentIndex = currentIndex,
        shuffle = shuffle,
        repeatMode = repeatMode,
    )

    fun restore(snapshot: PlaybackQueueSnapshot) {
        val source = distinct(snapshot.originalTrackIds).take(MaxSize)
        val sourceSet = source.toSet()
        original = source.toMutableList()
        active = snapshot.activeTrackIds.take(MaxSize).takeIf { it.size == source.size && it.toSet() == sourceSet }
            ?.toMutableList() ?: source.toMutableList()
        shuffle = snapshot.shuffle
        repeatMode = snapshot.repeatMode
        currentIndex = snapshot.currentTrackId?.let(active::indexOf) ?: -1
    }

    fun play(request: PlaybackRequest): QueueTransition {
        original = distinct(request.trackIds).take(MaxSize).toMutableList()
        active = original.toMutableList()
        shuffle = false
        currentIndex = request.startIndex.coerceIn(0, active.lastIndex)
        return QueueTransition.Play(active[currentIndex])
    }

    fun playShuffled(request: PlaybackRequest): QueueTransition {
        val selected = distinct(request.trackIds)
        original = selected.take(MaxSize).toMutableList()
        active = selected.shuffled(random).take(MaxSize).toMutableList()
        // In shuffled playback the cap deliberately samples first; source retains that sample's source order.
        original = selected.filter(active::contains).toMutableList()
        shuffle = true
        currentIndex = 0
        return QueueTransition.Play(active.first())
    }

    fun setShuffle(enabled: Boolean): QueueTransition {
        if (enabled == shuffle) return QueueTransition.Unchanged
        val current = snapshot().currentTrackId
        if (enabled) {
            if (current == null) {
                active.shuffle(random)
                currentIndex = active.indices.firstOrNull() ?: -1
            } else {
                val history = active.take(currentIndex + 1)
                val future = active.drop(currentIndex + 1).shuffled(random)
                active = (history + future).toMutableList()
            }
        } else {
            active = original.toMutableList()
            currentIndex = current?.let(active::indexOf) ?: -1
        }
        shuffle = enabled
        return QueueTransition.Unchanged
    }

    fun setRepeatMode(mode: RepeatMode) { repeatMode = mode }

    fun next(): QueueTransition {
        val current = currentTrackId() ?: return selectFirstOrStop()
        if (repeatMode == RepeatMode.One) return QueueTransition.Play(current)
        if (currentIndex < active.lastIndex) {
            currentIndex += 1
            return QueueTransition.Play(active[currentIndex])
        }
        return if (repeatMode == RepeatMode.All) {
            currentIndex = 0
            QueueTransition.Play(active.first())
        } else {
            QueueTransition.Stop
        }
    }

    fun previous(): QueueTransition {
        val current = currentTrackId() ?: return selectFirstOrStop()
        if (repeatMode == RepeatMode.One) return QueueTransition.Play(current)
        if (currentIndex > 0) {
            currentIndex -= 1
            return QueueTransition.Play(active[currentIndex])
        }
        return if (repeatMode == RepeatMode.All && active.isNotEmpty()) {
            currentIndex = active.lastIndex
            QueueTransition.Play(active[currentIndex])
        } else QueueTransition.Play(current)
    }

    fun playNext(trackId: String): QueueTransition = playNext(listOf(trackId))

    fun playNext(trackIds: List<String>): QueueTransition {
        val current = currentTrackId()
        val incoming = distinct(trackIds).filter { it != current }
        if (incoming.isEmpty()) return QueueTransition.Unchanged
        incoming.forEach(::removeId)
        trimFor(incoming.size)
        val accepted = incoming.take((MaxSize - active.size).coerceAtLeast(0))
        val activeAt = if (currentIndex >= 0) currentIndex + 1 else active.size
        active.addAll(activeAt, accepted)
        val sourceAt = current?.let(original::indexOf)?.plus(1) ?: original.size
        original.addAll(sourceAt, accepted)
        currentIndex = current?.let(active::indexOf) ?: currentIndex
        return QueueTransition.Unchanged
    }

    fun append(trackIds: List<String>): QueueTransition {
        val current = currentTrackId()
        val incoming = distinct(trackIds).filter { it != current }
        if (incoming.isEmpty()) return QueueTransition.Unchanged
        incoming.forEach(::removeId)
        trimFor(incoming.size)
        val accepted = incoming.take((MaxSize - active.size).coerceAtLeast(0))
        original.addAll(accepted)
        active.addAll(accepted)
        currentIndex = current?.let(active::indexOf) ?: currentIndex
        return QueueTransition.Unchanged
    }

    fun removeFromQueue(trackId: String): QueueTransition {
        val wasCurrent = trackId == currentTrackId()
        val current = currentTrackId()
        val removedIndex = active.indexOf(trackId)
        if (removedIndex < 0) return QueueTransition.Unchanged
        active.removeAt(removedIndex)
        original.remove(trackId)
        if (!wasCurrent) {
            currentIndex = current?.let(active::indexOf) ?: currentIndex.coerceAtMost(active.lastIndex)
            return QueueTransition.Unchanged
        }
        if (active.isEmpty()) { currentIndex = -1; return QueueTransition.Stop }
        if (removedIndex < active.size) { currentIndex = removedIndex; return QueueTransition.Play(active[currentIndex]) }
        return if (repeatMode == RepeatMode.All) {
            currentIndex = 0; QueueTransition.Play(active.first())
        } else {
            currentIndex = -1; QueueTransition.Stop
        }
    }

    fun reorderQueue(trackIds: List<String>): QueueTransition {
        if (trackIds.size != active.size || trackIds.toSet().size != trackIds.size || trackIds.toSet() != active.toSet()) {
            return QueueTransition.Unchanged
        }
        val current = currentTrackId()
        active = trackIds.toMutableList()
        currentIndex = current?.let(active::indexOf) ?: -1
        return QueueTransition.Unchanged
    }

    private fun currentTrackId(): String? = active.getOrNull(currentIndex)
    private fun selectFirstOrStop(): QueueTransition = active.firstOrNull()?.let {
        currentIndex = 0; QueueTransition.Play(it)
    } ?: QueueTransition.Stop

    private fun trimFor(incoming: Int) {
        while (active.size + incoming > MaxSize && active.size > 1) {
            val current = currentTrackId()
            val history = active.indices.firstOrNull { it < currentIndex && active[it] != current }
            val future = active.indices.lastOrNull { it > currentIndex && active[it] != current }
            val removeAt = history ?: future ?: break
            removeId(active[removeAt])
        }
    }

    private fun removeId(id: String) {
        val current = currentTrackId()
        active.remove(id)
        original.remove(id)
        currentIndex = current?.let(active::indexOf) ?: -1
    }

    private fun distinct(ids: List<String>): List<String> = ids.distinct()

    private companion object { const val MaxSize = 1000 }
}
