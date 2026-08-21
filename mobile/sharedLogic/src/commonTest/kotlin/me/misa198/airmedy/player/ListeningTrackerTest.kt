package me.misa198.airmedy.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ListeningTrackerTest {
    private var id = 0
    private fun tracker() = ListeningTracker("phone") { "id-${++id}" }

    @Test
    fun countsOnlyRunningAudioAndKeepsAttemptAcrossPause() {
        val tracker = tracker()
        assertIs<ListeningWrite.AttemptStarted>(tracker.start("track", 0, 1_000, 1_000).single())
        val first = tracker.pause(13_000, 13_000).single()
        assertEquals(12, assertIs<ListeningWrite.Session>(first).value.listenedSeconds)
        tracker.resume(20_000, 20_000)
        val finished = tracker.finish(PlaybackEndReason.STOPPED, 31_000, 31_000)
        assertEquals(11, assertIs<ListeningWrite.Session>(finished[0]).value.listenedSeconds)
        assertEquals(23, assertIs<ListeningWrite.AttemptFinished>(finished[1]).value.listenedSeconds)
    }

    @Test
    fun qualifiesOnceAndCheckpointsEveryMinute() {
        val tracker = tracker()
        tracker.start("track", 0, 0, 1)
        val qualified = tracker.tick(50_000, 100_000, 50_000, 50_001)
        assertIs<ListeningWrite.QualifiedPlay>(qualified.single())
        val checkpoint = tracker.tick(60_000, 100_000, 60_000, 60_001)
        assertEquals(60, assertIs<ListeningWrite.Session>(checkpoint.single()).value.listenedSeconds)
        assertTrue(tracker.tick(70_000, 100_000, 70_000, 70_001).isEmpty())
    }

    @Test
    fun discardsShortSessions() {
        val tracker = tracker()
        tracker.start("track", 0, 0, 1)
        assertTrue(tracker.finish(PlaybackEndReason.SKIPPED, 9_000, 9_001).none { it is ListeningWrite.Session })
    }

    @Test
    fun splitsCrossfadeOverlapBetweenOutgoingAndIncoming() {
        val tracker = tracker()
        tracker.start("incoming", 0, 0, 1)
        tracker.tick(4_000, 60_000, 4_000, 4_001)
        val outgoing = assertIs<ListeningWrite.Session>(tracker.splitCrossfadeOverlap("outgoing", 0, 4_000).single())
        assertEquals(2, outgoing.value.listenedSeconds)
        val incoming = assertIs<ListeningWrite.Session>(tracker.pause(12_000, 12_001).single())
        assertEquals(10, incoming.value.listenedSeconds)
    }
}
