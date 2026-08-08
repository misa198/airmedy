package me.misa198.airmedy.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AirmedyPillButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun destructiveVariantExposesItsActionAndInvokesCallback() {
        var clicked by mutableStateOf(false)
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                AirmedyPillButton(
                    label = "Revoke",
                    onClick = { clicked = true },
                    variant = AirmedyPillButtonVariant.Destructive,
                )
            }
        }

        composeTestRule.onNodeWithText("Revoke").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun darkThemeUsesWhitePrimaryForegroundForPrimaryAndDestructiveActions() {
        var onPrimary: Color? = null

        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                onPrimary = MaterialTheme.colorScheme.onPrimary
                AirmedyPillButton(
                    label = "Continue",
                    onClick = {},
                    variant = AirmedyPillButtonVariant.Primary,
                )
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(Color.White, onPrimary)
        }
    }
}
