package me.misa198.airmedy.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenQueuePanelDragTest {
    @Test
    fun hidesTheFullscreenControlsOnlyWhileQueueReorderingIsActive() {
        assertEquals(true, areFullScreenPlayerControlsVisible(isQueueReordering = false))
        assertEquals(false, areFullScreenPlayerControlsVisible(isQueueReordering = true))
    }

    @Test
    fun movesAQueueTrackToTheLibraryReportedDestinationIndex() {
        assertEquals(
            listOf("one", "three", "four", "two"),
            moveQueueTrack(listOf("one", "two", "three", "four"), fromIndex = 1, toIndex = 3),
        )
    }

    @Test
    fun doesNotMutateTheQueueForInvalidOrUnchangedMoves() {
        val queue = listOf("one", "two")
        assertEquals(
            queue,
            moveQueueTrack(queue, fromIndex = 0, toIndex = 0),
        )
        assertEquals(
            queue,
            moveQueueTrack(queue, fromIndex = -1, toIndex = 1),
        )
    }

    @Test
    fun queueContextMenuEnablesRemovalAndHidesAppend() {
        val actions = queueTrackContextMenuActions()

        assertTrue(actions.removeFromQueue)
        assertFalse(actions.addToQueue)
        assertTrue(actions.playNext)
    }

    @Test
    fun queueContextMenuDoesNotOpenFromTheReorderHandleTarget() {
        assertTrue(shouldOpenQueueTrackContextMenu(longPressX = 127f, rowWidthPx = 200, dragHandleWidthPx = 72f))
        assertFalse(shouldOpenQueueTrackContextMenu(longPressX = 128f, rowWidthPx = 200, dragHandleWidthPx = 72f))
        assertFalse(shouldOpenQueueTrackContextMenu(longPressX = 199f, rowWidthPx = 200, dragHandleWidthPx = 72f))
    }
}
