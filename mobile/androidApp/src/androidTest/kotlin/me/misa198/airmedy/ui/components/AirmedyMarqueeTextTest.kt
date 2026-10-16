package me.misa198.airmedy.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    @Test
    fun fadeSoftensOnlyTheTrailingEdgeOfOverflowingText() {
        checkFade("████████████████████████████████", overflows = true)
    }

    @Test
    fun fadeLeavesFittingTextUnchanged() {
        checkFade("Title", overflows = false)
    }

    private fun checkFade(text: String, overflows: Boolean) {
        var fade by mutableStateOf(false)
        var theme by mutableStateOf(ThemeMode.Dark)
        var fadeWidth = 0
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AirmedyTheme(themeMode = theme) {
                fadeWidth = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.roundToPx() }
                Box(Modifier.width(120.dp)) {
                    AirmedyMarqueeText(text, MaterialTheme.colorScheme.onSurface, MaterialTheme.typography.bodyMedium, fade = fade)
                }
            }
        }

        for (mode in listOf(ThemeMode.Dark, ThemeMode.Light)) {
            composeTestRule.runOnIdle { theme = mode; fade = false }
            composeTestRule.mainClock.advanceTimeBy(300)
            val node = composeTestRule.onNodeWithText(text).assertIsDisplayed()
            val original = node.captureToImage().asAndroidBitmap()
            composeTestRule.runOnIdle { fade = true }
            composeTestRule.mainClock.advanceTimeByFrame()
            val faded = node.captureToImage().asAndroidBitmap()
            val unchangedWidth = original.width - fadeWidth
            assertTrue("Fade must preserve text before the trailing edge in $mode",
                Bitmap.createBitmap(original, 0, 0, unchangedWidth, original.height)
                    .sameAs(Bitmap.createBitmap(faded, 0, 0, unchangedWidth, faded.height)))
            if (overflows) assertFalse("Overflowing text must fade in $mode", original.sameAs(faded))
            else assertTrue("Fitting text must not fade in $mode", original.sameAs(faded))
        }
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
