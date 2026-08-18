package me.misa198.airmedy.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncScannerContentTest {
    @Test
    fun landscapeViewfinderFitsTheAvailableHeightAndLeavesRoomForInstructions() {
        val size = scannerViewfinderSize(800.dp, 300.dp, landscape = true)

        assertTrue(size <= 300.dp)
        assertTrue(size <= (800.dp - 72.dp) * 0.55f)
    }
}
