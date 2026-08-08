package me.misa198.airmedy.ui.components

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chrisbanes.haze.rememberHazeState
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.screens.SortOrder
import me.misa198.airmedy.ui.screens.TrackSortOption
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrackSortHeaderButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun opensMenuAndHandlesOptionSelection() {
        var selectedOption: TrackSortOption? = null
        var orderToggled = false

        composeTestRule.setContent {
            val hazeState = rememberHazeState()
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                TrackSortHeaderButton(
                    hazeState = hazeState,
                    sortOption = TrackSortOption.Name,
                    sortOrder = SortOrder.Ascending,
                    onSortOptionSelected = { selectedOption = it },
                    onToggleSortOrder = { orderToggled = true },
                )
            }
        }

        composeTestRule.onNode(hasContentDescription("Sort by")).performClick()

        composeTestRule.onNodeWithText("Artist").assertExists()
        composeTestRule.onNodeWithText("Artist").performClick()
        assertTrue(selectedOption == TrackSortOption.Artist)
    }
}
