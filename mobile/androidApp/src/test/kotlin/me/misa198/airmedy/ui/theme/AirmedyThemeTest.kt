package me.misa198.airmedy.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AirmedyThemeTest {
    @Test
    fun `OLED uses pure black backgrounds and keeps dark foreground colors`() {
        assertEquals(Color.Black, OledColors.background)
        assertEquals(Color.Black, OledColors.playerBackdrop)
        assertEquals(
            DarkColors,
            OledColors.copy(
                background = DarkColors.background,
                playerBackdrop = DarkColors.playerBackdrop,
                card = DarkColors.card,
                glass = DarkColors.glass,
                glassOpaque = DarkColors.glassOpaque,
                glassElevated = DarkColors.glassElevated,
                buttonSecondary = DarkColors.buttonSecondary,
                textFieldClear = DarkColors.textFieldClear,
            ),
        )
    }
}
