package me.misa198.airmedy.ui.navigation

import me.misa198.airmedy.AppDestination
import me.misa198.airmedy.ui.components.MaterialSymbols
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationMetadataTest {
    @Test
    fun libraryDestinationUsesGraphicEqSymbol() {
        assertEquals(MaterialSymbols.GraphicEq, AppDestination.Library.symbol)
        assertEquals("graphic_eq", AppDestination.Library.symbol)
    }

    @Test
    fun mapsDestinationsToCorrectSymbols() {
        assertEquals(MaterialSymbols.Home, AppDestination.Home.symbol)
        assertEquals(MaterialSymbols.GraphicEq, AppDestination.Library.symbol)
        assertEquals(MaterialSymbols.Search, AppDestination.Search.symbol)
        assertEquals(MaterialSymbols.Settings, AppDestination.Settings.symbol)
    }
}
