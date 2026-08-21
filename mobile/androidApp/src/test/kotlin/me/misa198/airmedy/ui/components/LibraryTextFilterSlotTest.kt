package me.misa198.airmedy.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTextFilterSlotTest {
    @Test
    fun closedFilterHasNoRetainedHeightAfterDismissal() {
        assertEquals(
            0.dp,
            libraryTextFilterSlotTargetHeight(
                visible = false,
                dismissalProgress = 48.dp,
                previewHeight = 0.dp,
            ),
        )
    }

    @Test
    fun finalCloseFrameAnimatesInsteadOfSnapping() {
        assertEquals(
            false,
            libraryTextFilterSlotUsesDragSnap(
                isUserDragging = true,
                visible = false,
                dismissalProgressPx = 48f,
                previewHeightPx = 0f,
            ),
        )
    }
}
