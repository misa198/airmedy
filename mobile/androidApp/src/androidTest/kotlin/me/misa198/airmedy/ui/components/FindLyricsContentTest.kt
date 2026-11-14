package me.misa198.airmedy.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.Locale
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FindLyricsContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun durationFitsLongPlaceholderAndGrowsForLongValues() {
        var metadata by mutableStateOf("{}")
        var density = 1f
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocale(Locale.forLanguageTag("ru"))
            }
            val context = LocalContext.current.createConfigurationContext(configuration)
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density, fontScale = 1.3f),
            ) {
                AirmedyTheme(themeMode = ThemeMode.Dark) {
                    val track = LibraryTrack("track-1", "Track", "Artist", metadataJson = metadata)
                    Box(Modifier.size(width = 320.dp, height = 640.dp)) {
                        FindLyricsContent(track, FindLyricsSearchState(track), { _, _, _ -> emptyList() }, {})
                    }
                }
            }
        }

        val label = "Длительность"
        val placeholderLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(placeholderLayouts) }
        assertEquals(1, placeholderLayouts.single().lineCount)
        assertFalse(placeholderLayouts.single().hasVisualOverflow)

        var shortWidth = 0f
        for (seconds in listOf(245L, 360000L)) {
            compose.runOnIdle { metadata = """{"duration":$seconds}""" }
            val field = compose.onNodeWithContentDescription(label).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val width = field.fetchSemanticsNode().boundsInRoot.width
            // Horizontal padding (36dp), leading icon (20dp), and icon gap (10dp).
            assertTrue(layouts.single().size.width <= width - 66 * density + 2)
            if (seconds == 245L) shortWidth = width else assertTrue(width > shortWidth)
        }
    }
}
