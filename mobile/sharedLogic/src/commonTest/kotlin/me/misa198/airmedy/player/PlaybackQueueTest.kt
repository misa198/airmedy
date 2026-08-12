package me.misa198.airmedy.player

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PlaybackQueueTest {
    @Test
    fun `restore selects first available item when saved current item is gone`() {
        val queue = PlaybackQueue()

        queue.restore(
            PlaybackQueueSnapshot(
                originalTrackIds = listOf("available"),
                activeTrackIds = listOf("available"),
                currentIndex = -1,
            ),
        )

        assertEquals("available", queue.snapshot().currentTrackId)
    }

    @Test
    fun `peek next follows repeat semantics without moving the cursor`() {
        val queue = PlaybackQueue(Random(0))
        queue.play(PlaybackRequest(listOf("one", "two")))

        assertEquals("two", queue.peekNext())
        assertEquals("one", queue.snapshot().currentTrackId)

        queue.setRepeatMode(RepeatMode.One)
        assertEquals("one", queue.peekNext())

        queue.setRepeatMode(RepeatMode.Off)
        queue.next()
        assertNull(queue.peekNext())

        queue.setRepeatMode(RepeatMode.All)
        assertEquals("one", queue.peekNext())
    }
    @Test
    fun `play uses source order and clicked start index`() {
        val queue = PlaybackQueue()

        assertEquals(QueueTransition.Play("b"), queue.play(PlaybackRequest(listOf("a", "b", "c"), 1)))
        assertEquals("b", queue.snapshot().currentTrackId)
        assertEquals(listOf("a", "b", "c"), queue.snapshot().activeTrackIds)
    }

    @Test
    fun `repeat off stops at final track while repeat all wraps`() {
        val queue = PlaybackQueue()
        queue.play(PlaybackRequest(listOf("a", "b"), 1))
        assertEquals(QueueTransition.StopAtCurrent, queue.next())
        assertEquals("b", queue.snapshot().currentTrackId)

        queue.setRepeatMode(RepeatMode.All)
        assertEquals(QueueTransition.Play("a"), queue.next())
        assertEquals(QueueTransition.Play("b"), queue.previous())
    }

    @Test
    fun `repeat one replays current track`() {
        val queue = PlaybackQueue()
        queue.play(PlaybackRequest(listOf("a", "b")))
        queue.setRepeatMode(RepeatMode.One)

        assertEquals(QueueTransition.Play("a"), queue.next())
        assertEquals(QueueTransition.Play("a"), queue.previous())
    }

    @Test
    fun `enabling shuffle preserves played history and current track`() {
        val queue = PlaybackQueue(Random(1))
        queue.play(PlaybackRequest(listOf("a", "b", "c", "d"), 1))

        queue.setShuffle(true)
        val snapshot = queue.snapshot()
        assertEquals(listOf("a", "b"), snapshot.activeTrackIds.take(2))
        assertEquals("b", snapshot.currentTrackId)

        queue.setShuffle(false)
        assertEquals(listOf("a", "b", "c", "d"), queue.snapshot().activeTrackIds)
        assertEquals("b", queue.snapshot().currentTrackId)
    }

    @Test
    fun `remove current selects successor and invalid reorder is ignored`() {
        val queue = PlaybackQueue()
        queue.play(PlaybackRequest(listOf("a", "b", "c"), 1))

        assertEquals(QueueTransition.Play("c"), queue.removeFromQueue("b"))
        queue.reorderQueue(listOf("c", "c"))
        assertEquals(listOf("a", "c"), queue.snapshot().activeTrackIds)
    }

    @Test
    fun `selecting queued track preserves playback options`() {
        val queue = PlaybackQueue(Random(1))
        queue.play(PlaybackRequest(listOf("a", "b", "c"), 1))
        queue.setShuffle(true)
        queue.setRepeatMode(RepeatMode.All)
        val activeOrder = queue.snapshot().activeTrackIds

        assertEquals(QueueTransition.Play("c"), queue.select("c"))
        assertEquals("c", queue.snapshot().currentTrackId)
        assertEquals(activeOrder, queue.snapshot().activeTrackIds)
        assertEquals(true, queue.snapshot().shuffle)
        assertEquals(RepeatMode.All, queue.snapshot().repeatMode)
        assertEquals(QueueTransition.Unchanged, queue.select("missing"))
    }

    @Test
    fun `reordering active queue keeps current track selected`() {
        val queue = PlaybackQueue()
        queue.play(PlaybackRequest(listOf("a", "b", "c"), 1))
        queue.setRepeatMode(RepeatMode.All)

        queue.reorderQueue(listOf("c", "b", "a"))

        assertEquals(listOf("c", "b", "a"), queue.snapshot().activeTrackIds)
        assertEquals("b", queue.snapshot().currentTrackId)
        assertEquals(RepeatMode.All, queue.snapshot().repeatMode)
    }

    @Test
    fun `clear removes every queue entry and resets queue options`() {
        val queue = PlaybackQueue()
        queue.play(PlaybackRequest(listOf("a", "b")))
        queue.setShuffle(true)
        queue.setRepeatMode(RepeatMode.All)

        assertEquals(QueueTransition.Stop, queue.clear())
        assertEquals(PlaybackQueueSnapshot(), queue.snapshot())
    }
}
