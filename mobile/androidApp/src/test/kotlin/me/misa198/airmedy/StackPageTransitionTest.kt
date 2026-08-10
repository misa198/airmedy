package me.misa198.airmedy

import me.misa198.airmedy.ui.navigation.PageKey
import me.misa198.airmedy.ui.navigation.isForwardTransition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StackPageTransitionTest {
    @Test
    fun pushesInStackAreIdentifiedAsForwardTransitions() {
        val rootKey = PageKey(AppDestination.Settings, AppStackPage.Root, index = 0)
        val syncKey = PageKey(AppDestination.Settings, AppStackPage.SettingsSync, index = 1)
        val scannerKey = PageKey(AppDestination.Settings, AppStackPage.SettingsSyncScanner, index = 2)

        assertTrue(isForwardTransition(target = syncKey, initial = rootKey))
        assertTrue(isForwardTransition(target = scannerKey, initial = syncKey))
    }

    @Test
    fun popsInStackAreIdentifiedAsBackwardTransitions() {
        val rootKey = PageKey(AppDestination.Settings, AppStackPage.Root, index = 0)
        val syncKey = PageKey(AppDestination.Settings, AppStackPage.SettingsSync, index = 1)
        val scannerKey = PageKey(AppDestination.Settings, AppStackPage.SettingsSyncScanner, index = 2)

        assertFalse(isForwardTransition(target = syncKey, initial = scannerKey))
        assertFalse(isForwardTransition(target = rootKey, initial = syncKey))
    }

    @Test
    fun destinationChangesAreNotIdentifiedAsStackTransitions() {
        val homeKey = PageKey(AppDestination.Home, AppStackPage.Root, index = 0)
        val libraryKey = PageKey(AppDestination.Library, AppStackPage.Root, index = 0)

        assertFalse(homeKey.destination == libraryKey.destination)
    }

    @Test
    fun albumDetailsPushesForwardFromTheAlbumList() {
        val stack = listOf(
            AppStackPage.Root,
            AppStackPage.LibraryAlbums,
            AppStackPage.AlbumDetails,
        )
        val albumsKey = stack.dropLast(1).currentStackPage(AppDestination.Library)
        val albumDetailsKey = stack.currentStackPage(AppDestination.Library)

        assertTrue(isForwardTransition(target = albumDetailsKey, initial = albumsKey))
    }
}
