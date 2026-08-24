package me.misa198.airmedy.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistDetailsReorderTest {
    @Test fun lazyListIndexesSkipThePlaylistHero() {
        assertEquals(0, playlistTrackIndex(1))
        assertEquals(2, playlistTrackIndex(3))
    }

    @Test fun moveAndAnchorsDescribeTheDroppedTrackPosition() {
        val moved = movePlaylistTrack(listOf("a", "b", "c"), fromIndex = 0, toIndex = 2)

        assertEquals(listOf("b", "c", "a"), moved)
        assertEquals("c" to null, playlistMoveAnchors(moved, "a"))
        assertEquals(null to "c", playlistMoveAnchors(moved, "b"))
    }
}
