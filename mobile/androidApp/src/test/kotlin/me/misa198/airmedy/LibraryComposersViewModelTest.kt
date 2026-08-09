package me.misa198.airmedy

import me.misa198.airmedy.sync.LibraryComposer
import me.misa198.airmedy.ui.screens.ComposerSortOption
import me.misa198.airmedy.ui.screens.SortOrder
import me.misa198.airmedy.ui.screens.sortComposers
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryComposersViewModelTest {
    private val composers = listOf(
        LibraryComposer(id = "1", name = "Zimmer", createdAt = "2026-01-01T00:00:00Z"),
        LibraryComposer(id = "2", name = "Bach", createdAt = "2026-05-01T00:00:00Z"),
        LibraryComposer(id = "3", name = "Chopin", createdAt = "2025-12-01T00:00:00Z"),
    )

    @Test
    fun sortsByNameInBothDirections() {
        assertEquals(
            listOf("Bach", "Chopin", "Zimmer"),
            sortComposers(composers, ComposerSortOption.Name, SortOrder.Ascending).map { it.name },
        )
        assertEquals(
            listOf("Zimmer", "Chopin", "Bach"),
            sortComposers(composers, ComposerSortOption.Name, SortOrder.Descending).map { it.name },
        )
    }

    @Test
    fun sortsByDateAddedWithNameTieBreaker() {
        assertEquals(
            listOf("Chopin", "Zimmer", "Bach"),
            sortComposers(composers, ComposerSortOption.DateAdded, SortOrder.Ascending).map { it.name },
        )
    }
}
