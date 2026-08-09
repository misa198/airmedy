package me.misa198.airmedy

import me.misa198.airmedy.ui.navigation.PageKey
import me.misa198.airmedy.ui.navigation.depth
import me.misa198.airmedy.ui.navigation.isForwardTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StackPageTransitionTest {
    @Test
    fun stackPageDepthsFormCorrectHierarchy() {
        assertEquals(0, AppStackPage.Root.depth)
        assertEquals(1, AppStackPage.HomeSampleDetail.depth)
        assertEquals(1, AppStackPage.LibraryArtists.depth)
        assertEquals(1, AppStackPage.LibraryAlbums.depth)
        assertEquals(1, AppStackPage.LibraryGenres.depth)
        assertEquals(1, AppStackPage.SettingsAppearance.depth)
        assertEquals(1, AppStackPage.SettingsSync.depth)
        assertEquals(1, AppStackPage.SettingsAbout.depth)
        assertEquals(2, AppStackPage.SettingsSyncScanner.depth)
    }

    @Test
    fun pushesInStackAreIdentifiedAsForwardTransitions() {
        val rootKey = PageKey(AppDestination.Settings, AppStackPage.Root)
        val syncKey = PageKey(AppDestination.Settings, AppStackPage.SettingsSync)
        val scannerKey = PageKey(AppDestination.Settings, AppStackPage.SettingsSyncScanner)

        assertTrue(isForwardTransition(target = syncKey, initial = rootKey))
        assertTrue(isForwardTransition(target = scannerKey, initial = syncKey))
    }

    @Test
    fun popsInStackAreIdentifiedAsBackwardTransitions() {
        val rootKey = PageKey(AppDestination.Settings, AppStackPage.Root)
        val syncKey = PageKey(AppDestination.Settings, AppStackPage.SettingsSync)
        val scannerKey = PageKey(AppDestination.Settings, AppStackPage.SettingsSyncScanner)

        assertFalse(isForwardTransition(target = syncKey, initial = scannerKey))
        assertFalse(isForwardTransition(target = rootKey, initial = syncKey))
    }

    @Test
    fun destinationChangesAreNotIdentifiedAsStackTransitions() {
        val homeKey = PageKey(AppDestination.Home, AppStackPage.Root)
        val libraryKey = PageKey(AppDestination.Library, AppStackPage.Root)

        assertFalse(homeKey.destination == libraryKey.destination)
    }
}
