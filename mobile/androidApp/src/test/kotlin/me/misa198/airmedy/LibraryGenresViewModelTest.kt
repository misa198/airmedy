package me.misa198.airmedy

import me.misa198.airmedy.sync.LibraryGenre
import me.misa198.airmedy.ui.screens.GenreSortOption
import me.misa198.airmedy.ui.screens.SortOrder
import me.misa198.airmedy.ui.screens.sortGenres
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGenresViewModelTest {
    private val genres = listOf(
        LibraryGenre(id = "1", name = "Rock", createdAt = "2026-01-01T00:00:00Z", sortName = "Rock"),
        LibraryGenre(id = "2", name = "Ambient", createdAt = "2026-05-01T00:00:00Z", sortName = "Ambient"),
        LibraryGenre(id = "3", name = "Classical", createdAt = "2025-12-01T00:00:00Z", sortName = "Classical"),
    )

    @Test
    fun sortsByNameInBothDirections() {
        assertEquals(
            listOf("Ambient", "Classical", "Rock"),
            sortGenres(genres, GenreSortOption.Name, SortOrder.Ascending).map { it.name },
        )
        assertEquals(
            listOf("Rock", "Classical", "Ambient"),
            sortGenres(genres, GenreSortOption.Name, SortOrder.Descending).map { it.name },
        )
    }

    @Test
    fun sortsByDateAddedWithNameTieBreaker() {
        assertEquals(
            listOf("Classical", "Rock", "Ambient"),
            sortGenres(genres, GenreSortOption.DateAdded, SortOrder.Ascending).map { it.name },
        )
    }

    @Test
    fun usesNormalizationDerivedSortNameInsteadOfDisplayName() {
        val genres = listOf(
            LibraryGenre(id = "1", name = "Zulu", sortName = "Alpha"),
            LibraryGenre(id = "2", name = "Alpha", sortName = "Zulu"),
        )

        assertEquals(listOf("Zulu", "Alpha"), sortGenres(genres, GenreSortOption.Name, SortOrder.Ascending).map { it.name })
    }
}
