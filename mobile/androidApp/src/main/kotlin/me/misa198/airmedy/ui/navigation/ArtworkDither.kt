package me.misa198.airmedy.ui.navigation

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/** Procedural achromatic pixel noise, not a UI/theme color. Never scale or blur this layer. */
@Composable
internal fun ArtworkDither() {
    val brush = remember {
        val bitmap = Bitmap.createBitmap(artworkDitherPixels(), 128, 128, Bitmap.Config.ARGB_8888)
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    Canvas(Modifier.fillMaxSize()) {
        // Apply after blur at display resolution; earlier noise gets averaged away.
        drawRect(brush, alpha = 2f / 255f)
    }
}

internal fun artworkDitherPixels(): IntArray {
    val random = Random(0)
    return IntArray(128 * 128) {
        val gray = random.nextInt(256)
        (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
    }
}
