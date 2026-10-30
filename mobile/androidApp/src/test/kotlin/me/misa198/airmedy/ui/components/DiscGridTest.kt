package me.misa198.airmedy.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscGridTest {
    @Test
    fun computesColumnsFromAvailableContentWidth() {
        listOf(
            0f to 1, 100f to 1, 150f to 1,
            311.9f to 1, 312f to 2,
            473.9f to 2, 474f to 3,
            635.9f to 3, 636f to 4,
        ).forEach { (width, expected) ->
            assertEquals("content width $width dp", expected, discGridColumnCount(width.dp))
        }
    }

    @Test
    fun alphabeticalIndexUsesResponsiveRowSize() {
        val values = listOf("Alpha", "Another", "Bravo", "Beta", "Charlie")
        listOf(312 to 4, 474 to 3, 636 to 3).forEach { (width, expectedRow) ->
            assertEquals(
                AlphabeticalIndexEntry("C", expectedRow),
                alphabeticalIndexEntries(values, itemOffset = 2, itemsPerLazyItem = discGridColumnCount(width.dp)).last(),
            )
        }
    }
}
