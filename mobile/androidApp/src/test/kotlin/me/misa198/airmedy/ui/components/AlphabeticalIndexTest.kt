package me.misa198.airmedy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabeticalIndexTest {
    @Test
    fun derivesPresentLettersAndTheirFirstLazyItems() {
        assertEquals(
            listOf(
                AlphabeticalIndexEntry("A", 2),
                AlphabeticalIndexEntry("#", 3),
                AlphabeticalIndexEntry("B", 4),
            ),
            alphabeticalIndexEntries(
                values = listOf("Álpha", "2 Fast", "Bravo", "Beta", ""),
                itemOffset = 2,
            ),
        )
    }

    @Test
    fun usesTheFirstAlbumGridRowForEachLetter() {
        assertEquals(
            listOf(
                AlphabeticalIndexEntry("A", 2),
                AlphabeticalIndexEntry("B", 3),
                AlphabeticalIndexEntry("#", 4),
            ),
            alphabeticalIndexEntries(
                values = listOf("Alpha", "Another", "Bravo", "Beta", "4ever"),
                itemOffset = 2,
                itemsPerLazyItem = 2,
            ),
        )
    }
}
