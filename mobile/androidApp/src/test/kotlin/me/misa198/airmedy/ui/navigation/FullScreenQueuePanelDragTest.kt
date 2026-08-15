package me.misa198.airmedy.ui.navigation

import androidx.compose.runtime.mutableStateOf
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
    fun commitsTheFinalLocalOrderInsteadOfTheOrderFromDragStart() {
        val latestOrder = mutableStateOf(listOf("one", "two", "three"))
        var committedOrder: List<String>? = null
        val latestCallback = mutableStateOf<(List<String>) -> Unit>({ committedOrder = it })

        latestOrder.value = moveQueueTrack(latestOrder.value, fromIndex = 0, toIndex = 2)
        commitQueueReorder(latestOrder, latestCallback)

        assertEquals(listOf("two", "three", "one"), committedOrder)
    }

    @Test
    fun crossfadeArtworkLayersAreResetForEachArtworkPath() {
        assertFalse(
            fullscreenArtworkMemoryKey("from.jpg", keepPrevious = false) ==
                fullscreenArtworkMemoryKey("to.jpg", keepPrevious = false),
        )
        assertEquals(
            fullscreenArtworkMemoryKey("from.jpg", keepPrevious = true),
            fullscreenArtworkMemoryKey("to.jpg", keepPrevious = true),
        )
    }

    @Test
    fun queueContextMenuEnablesRemovalForNonCurrentTrackAndHidesAppend() {
        val actions = queueTrackContextMenuActions(isCurrent = false)

        assertTrue(actions.removeFromQueue)
        assertFalse(actions.addToQueue)
        assertTrue(actions.playNext)
    }

    @Test
    fun queueContextMenuHidesRemovalForCurrentTrack() {
        val actions = queueTrackContextMenuActions(isCurrent = true)

        assertFalse(actions.removeFromQueue)
        assertFalse(actions.addToQueue)
    }

    @Test
    fun queueContextMenuDoesNotOpenFromTheReorderHandleTarget() {
        assertTrue(shouldOpenQueueTrackContextMenu(longPressX = 127f, rowWidthPx = 200, dragHandleWidthPx = 72f))
        assertFalse(shouldOpenQueueTrackContextMenu(longPressX = 128f, rowWidthPx = 200, dragHandleWidthPx = 72f))
        assertFalse(shouldOpenQueueTrackContextMenu(longPressX = 199f, rowWidthPx = 200, dragHandleWidthPx = 72f))
    }
}
