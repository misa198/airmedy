package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibrarySearchContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyStateCardHasHorizontalPageInset() {
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibrarySearchContent(uiState = LibrarySearchUiState())
            }
        }

        val inset = with(composeTestRule.density) { 24.dp.toPx() }
        val emptyStateBounds = composeTestRule.onNodeWithTag(SearchEmptyStateTag)
            .fetchSemanticsNode()
            .boundsInRoot
        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot

        assertEquals(inset, emptyStateBounds.left, 0.5f)
        assertEquals(rootBounds.right - inset, emptyStateBounds.right, 0.5f)
    }
}
