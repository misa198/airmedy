package me.misa198.airmedy

import me.misa198.airmedy.ui.screens.matchesLibraryTextFilter
import me.misa198.airmedy.ui.screens.normalizedLibrarySearchText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTextSearchTest {
    @Test
    fun foldsDiacriticsWhitespaceAndCaseLikeDesktopSearch() {
        assertTrue(matchesLibraryTextFilter("beyonce", "Beyoncé"))
        assertTrue(matchesLibraryTextFilter("MUSE", "Muse"))
        assertTrue(matchesLibraryTextFilter("", "Anything"))
        assertFalse(matchesLibraryTextFilter("ambient", "Electronic", "Rock"))
        assertTrue(normalizedLibrarySearchText("Đặng") == "dang")
    }

    @Test
    fun matchesAnyConfiguredSearchField() {
        assertTrue(matchesLibraryTextFilter("discovery", "One More Time", "Daft Punk", "Discovery"))
        assertTrue(matchesLibraryTextFilter("daft", "One More Time", "Daft Punk", "Discovery"))
        assertFalse(matchesLibraryTextFilter("jazz", "One More Time", "Daft Punk", "Discovery"))
    }
}
