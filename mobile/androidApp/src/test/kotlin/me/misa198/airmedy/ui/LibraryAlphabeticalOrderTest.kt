package me.misa198.airmedy.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAlphabeticalOrderTest {
    @Test
    fun ordersLettersThenNumbersThenOtherCharacters() {
        assertEquals(
            listOf("Álpha", "Đen", "Zulu", "2 Fast", "#Hashtag"),
            listOf("#Hashtag", "2 Fast", "Zulu", "Đen", "Álpha").sortedWith(libraryAlphabeticalComparator),
        )
    }
}
