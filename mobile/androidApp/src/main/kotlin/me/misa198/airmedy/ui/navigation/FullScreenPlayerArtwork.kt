package me.misa198.airmedy.ui.navigation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.misa198.airmedy.player.ArtworkCrossfadeTransition
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.MaterialSymbols
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import java.io.File

private val FullScreenArtworkShape = RoundedCornerShape(16.dp)

internal data class FullScreenArtwork(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val dominant: Color,
)

@Composable
internal fun FullScreenPlayerBackground(
    artwork: FullScreenArtwork?,
    outgoingArtwork: FullScreenArtwork?,
    incomingArtwork: FullScreenArtwork?,
    crossfadeProgress: Float,
    isArtworkCrossfading: Boolean,
    modifier: Modifier,
) {
    val colors = LocalAirmedyColors.current
    val dominantColor by animateColorAsState(
        targetValue = artwork?.dominant ?: colors.playerBackdrop,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "full-screen-background-colour",
    )
    Box(modifier.background(colors.playerBackdrop)) {
        if (isArtworkCrossfading) {
            PlayerBackgroundGradient(outgoingArtwork?.dominant ?: colors.playerBackdrop, equalPowerOutgoing(crossfadeProgress))
            PlayerBackgroundGradient(incomingArtwork?.dominant ?: colors.playerBackdrop, equalPowerIncoming(crossfadeProgress))
        } else {
            PlayerBackgroundGradient(dominantColor, 1f)
        }
        Box(Modifier.fillMaxSize().background(colors.playerBackdrop.copy(alpha = 0.24f)))
    }
}

@Composable
private fun PlayerBackgroundGradient(dominant: Color, alpha: Float) {
    Box(Modifier.fillMaxSize().alpha(alpha).background(dominant.copy(alpha = 0.66f)))
}

@Composable
internal fun FullScreenPlayerArtwork(
    artwork: FullScreenArtwork?,
    outgoingArtwork: FullScreenArtwork?,
    incomingArtwork: FullScreenArtwork?,
    crossfadeProgress: Float,
    isArtworkCrossfading: Boolean,
    modifier: Modifier,
) {
    val colors = LocalAirmedyColors.current
    Box(
        modifier.clip(FullScreenArtworkShape).background(colors.glassElevated)
            .border(1.dp, colors.borderGlass, FullScreenArtworkShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isArtworkCrossfading) {
            ArtworkLayer(outgoingArtwork, equalPowerOutgoing(crossfadeProgress))
            ArtworkLayer(incomingArtwork, equalPowerIncoming(crossfadeProgress))
        } else {
            ArtworkLayer(artwork, 1f)
        }
    }
}

@Composable
private fun ArtworkLayer(artwork: FullScreenArtwork?, alpha: Float) {
    val colors = LocalAirmedyColors.current
    if (artwork != null) Image(artwork.image, null, Modifier.fillMaxSize().alpha(alpha), contentScale = ContentScale.Crop)
    else Box(Modifier.fillMaxSize().alpha(alpha), contentAlignment = Alignment.Center) {
        MaterialSymbol(MaterialSymbols.MusicNote, null, tint = colors.textMuted, size = 64.dp)
    }
}

@Composable
internal fun rememberArtworkCrossfadeProgress(crossfade: ArtworkCrossfadeTransition?): Float {
    var progress by remember(crossfade?.id) { mutableFloatStateOf(if (crossfade == null) 1f else 0f) }
    LaunchedEffect(crossfade?.id) {
        if (crossfade == null) return@LaunchedEffect
        val durationNanos = crossfade.durationMs.coerceAtLeast(1L) * 1_000_000L
        var startedAtNanos = 0L
        while (progress < 1f) withFrameNanos { frameNanos ->
            if (startedAtNanos == 0L) startedAtNanos = frameNanos
            progress = ((frameNanos - startedAtNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
        }
    }
    return progress
}

internal fun equalPowerOutgoing(progress: Float): Float = kotlin.math.cos(progress.coerceIn(0f, 1f) * Math.PI.toFloat() / 2f)
internal fun equalPowerIncoming(progress: Float): Float = kotlin.math.sin(progress.coerceIn(0f, 1f) * Math.PI.toFloat() / 2f)

@Composable
internal fun rememberFullscreenArtwork(artworkPath: String?, keepPrevious: Boolean = true): FullScreenArtwork? {
    val context = LocalContext.current
    var artwork by remember(fullscreenArtworkMemoryKey(artworkPath, keepPrevious)) { mutableStateOf<FullScreenArtwork?>(null) }
    LaunchedEffect(artworkPath) {
        if (artworkPath.isNullOrBlank()) {
            artwork = null
            return@LaunchedEffect
        }
        artwork = withContext(Dispatchers.IO) {
            val file = File(if (File(artworkPath).isAbsolute) artworkPath else File(context.filesDir, artworkPath).path)
            if (!file.isFile) return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1080 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
                val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: return@runCatching null
                FullScreenArtwork(bitmap.asImageBitmap(), dominantColor(bitmap))
            }.getOrNull()
        }
    }
    return artwork
}

/** A crossfade layer is path-scoped; only the normal player cover may persist across paths. */
internal fun fullscreenArtworkMemoryKey(artworkPath: String?, keepPrevious: Boolean): Any? =
    if (keepPrevious) FullscreenArtworkRetainedKey else artworkPath

private object FullscreenArtworkRetainedKey

private fun dominantColor(bitmap: Bitmap): Color {
    val sample = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
    val pixels = IntArray(24 * 24)
    sample.getPixels(pixels, 0, 24, 0, 0, 24, 24)
    val opaque = pixels.filter { android.graphics.Color.alpha(it) > 32 }
    if (opaque.isEmpty()) return Color.Transparent
    return Color(
        opaque.sumOf { android.graphics.Color.red(it) } / opaque.size,
        opaque.sumOf { android.graphics.Color.green(it) } / opaque.size,
        opaque.sumOf { android.graphics.Color.blue(it) } / opaque.size,
    )
}
