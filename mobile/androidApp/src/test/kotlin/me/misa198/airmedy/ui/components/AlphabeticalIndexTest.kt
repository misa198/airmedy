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

    @Test
    fun mapsTouchesToTheCenteredLetterRail() {
        val entries = listOf(
            AlphabeticalIndexEntry("A", 2),
            AlphabeticalIndexEntry("B", 3),
            AlphabeticalIndexEntry("C", 4),
        )

        assertEquals(2, alphabeticalIndexItemIndexAt(210f, 200f, 60, entries))
        assertEquals(3, alphabeticalIndexItemIndexAt(230f, 200f, 60, entries))
        assertEquals(4, alphabeticalIndexItemIndexAt(250f, 200f, 60, entries))
    }
}
