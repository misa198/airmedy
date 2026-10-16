package me.misa198.airmedy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AirmedyMarqueeTextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun overflowingTextPausesThenScrolls() {
        checkMotion("Very Long Track Title That Will Overflow The Container Width", scrolls = true)
    }

    @Test
    fun fittingTextRemainsStill() {
        checkMotion("Title", scrolls = false)
    }

    private fun checkMotion(text: String, scrolls: Boolean) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AirmedyTheme(themeMode = ThemeMode.Dark) {
                Box(Modifier.width(120.dp)) {
                    AirmedyMarqueeText(
                        text = text,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        val node = composeTestRule.onNodeWithText(text).assertIsDisplayed()
        val initial = node.captureToImage().asAndroidBitmap()
        composeTestRule.mainClock.advanceTimeBy(2_000)
        assertTrue("Text must pause initially", initial.sameAs(node.captureToImage().asAndroidBitmap()))
        composeTestRule.mainClock.advanceTimeBy(2_000)
        val unchanged = initial.sameAs(node.captureToImage().asAndroidBitmap())
        if (scrolls) assertFalse("Overflowing text must scroll after three seconds", unchanged)
        else assertTrue("Fitting text must stay still", unchanged)
    }
}
