package me.misa198.airmedy.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import me.misa198.airmedy.R
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppUpdateDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longNotesScrollAndUpdateOpensTheReleaseInLightTheme() {
        var visible by mutableStateOf(true)
        var opened: String? = null
        var updateLabel = ""
        val update = AppUpdate("2.0.0", (1..100).joinToString("\n") { "- Release change $it" }, "https://github.com/misa198/airmedy/releases/tag/mobile-v2.0.0")
        compose.setContent {
            AirmedyTheme(themeMode = ThemeMode.Light) {
                updateLabel = stringResource(R.string.app_update_action)
                if (visible) AppUpdateDialog(update, { visible = false }, { opened = it })
            }
        }
        compose.onNodeWithText("2.0.0", substring = true).assertIsDisplayed()
        compose.onNodeWithText(updateLabel).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        val scrollRange = compose.onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        compose.runOnIdle {
            assertTrue(scrollRange.value() > 0f)
        }
        compose.onNodeWithText(updateLabel).performClick()
        assertEquals(update.releaseUrl, opened)
        compose.onNodeWithText("2.0.0", substring = true).assertDoesNotExist()
    }

    @Test fun emptyNotesUseFallbackAndLaterDismissesInDarkTheme() {
        var visible by mutableStateOf(true)
        var later = ""
        var fallback = ""
        compose.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                later = stringResource(R.string.app_update_later)
                fallback = stringResource(R.string.app_update_no_notes)
                if (visible) AppUpdateDialog(AppUpdate("2.0.0", "", "https://github.com/release"), { visible = false }, { fail("Unexpected navigation") })
            }
        }
        onView(withText(fallback)).check(matches(isDisplayed()))
        compose.onNodeWithText(later).performClick()
        onView(withText(fallback)).check(doesNotExist())
    }
}
