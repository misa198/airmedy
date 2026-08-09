package me.misa198.airmedy.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import me.misa198.airmedy.R
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun libraryContentDisplaysActionListItems() {
        var artistsClicked = false
        var albumsClicked = false
        var tracksClicked = false
        var genresClicked = false
        var composersClicked = false

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                LibraryContent(
                    onArtistsSelected = { artistsClicked = true },
                    onAlbumsSelected = { albumsClicked = true },
                    onTracksSelected = { tracksClicked = true },
                    onGenresSelected = { genresClicked = true },
                    onComposersSelected = { composersClicked = true },
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.onNodeWithText(context.getString(R.string.library_artists)).assertIsDisplayed().performClick()
        assertTrue(artistsClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_albums)).assertIsDisplayed().performClick()
        assertTrue(albumsClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_tracks)).assertIsDisplayed().performClick()
        assertTrue(tracksClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_genres)).assertIsDisplayed().performClick()
        assertTrue(genresClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_composers)).assertIsDisplayed().performClick()
        assertTrue(composersClicked)
    }
}
