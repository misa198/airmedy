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
    fun autoFollowUsesTheMeasuredDeltaBetweenVisibleQueueRows() {
        assertEquals(
            56f,
            queueAutoFollowScrollDelta(previousItemOffset = 112, targetItemOffset = 168),
        )
        assertEquals(
            -56f,
            queueAutoFollowScrollDelta(previousItemOffset = 168, targetItemOffset = 112),
        )
    }

    @Test
    fun autoFollowFallsBackToIndexedScrollingWhenEitherQueueRowIsNotVisible() {
        assertEquals(
            null,
            queueAutoFollowScrollDelta(previousItemOffset = 112, targetItemOffset = null),
        )
        assertEquals(
            null,
            queueAutoFollowScrollDelta(previousItemOffset = null, targetItemOffset = 168),
        )
    }

    @Test
    fun autoFollowUsesIndexedScrollingWhenTheNewCurrentRowIsOutsideViewport() {
        assertTrue(queueAutoFollowRequiresIndexedScroll(previousItemOffset = 112, targetItemOffset = null))
        assertTrue(queueAutoFollowRequiresIndexedScroll(previousItemOffset = null, targetItemOffset = 168))
        assertFalse(queueAutoFollowRequiresIndexedScroll(previousItemOffset = 112, targetItemOffset = 168))
    }

    @Test
    fun queueSelectionScrollDeltaAccountsForPartialLeadingRow() {
        assertEquals(
            140f,
            queueScrollTargetDelta(
                firstVisibleItemIndex = 3,
                firstVisibleItemScrollOffset = 28,
                targetIndex = 6,
                rowHeightPx = 56f,
            ),
        )
        assertEquals(
            -84f,
            queueScrollTargetDelta(
                firstVisibleItemIndex = 6,
                firstVisibleItemScrollOffset = 28,
                targetIndex = 5,
                rowHeightPx = 56f,
            ),
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
