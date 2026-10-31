package me.misa198.airmedy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TextHelperTest {
    @get:Rule val compose = createComposeRule()

    @Test fun annotationWrapsAndUsesMutedBodySmallInBothThemes() {
        val text = "Living artwork uses more GPU resources. For less powerful devices, Solid is recommended."
        var theme by mutableStateOf(ThemeMode.Light)
        var muted = Color.Unspecified
        var fontSize = TextUnit.Unspecified
        compose.setContent {
            AirmedyTheme(themeMode = theme) {
                muted = LocalAirmedyColors.current.textMuted
                fontSize = MaterialTheme.typography.bodySmall.fontSize
                Box(Modifier.width(180.dp)) { TextHelper(text) }
            }
        }
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            compose.runOnIdle { theme = mode }
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).assertHasNoClickAction()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.single()
            assertEquals(muted, layout.layoutInput.style.color)
            assertEquals(fontSize, layout.layoutInput.style.fontSize)
            assertTrue("Long annotations must wrap", layout.lineCount > 1)
            assertFalse("Annotations must not be truncated", layout.hasVisualOverflow)
        }
    }
}
