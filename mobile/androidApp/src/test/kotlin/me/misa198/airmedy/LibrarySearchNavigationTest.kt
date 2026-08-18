package me.misa198.airmedy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchNavigationTest {
    @Test fun clearsOnlyWhenSearchItselfIsPopped() {
        assertTrue(shouldClearLibrarySearch(AppIntent.NavigateBack, AppStackPage.LibrarySearch))
        assertFalse(shouldClearLibrarySearch(AppIntent.NavigateBack, AppStackPage.AlbumDetails))
        assertFalse(shouldClearLibrarySearch(AppIntent.OpenAlbumDetails("album"), AppStackPage.LibrarySearch))
    }
}
