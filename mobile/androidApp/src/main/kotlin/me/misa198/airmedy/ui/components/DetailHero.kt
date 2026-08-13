package me.misa198.airmedy.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

enum class DetailHeroArtworkShape { Square, Circle }

/** Artwork-derived hero backdrop that fades into the active theme background. */
@Composable
fun ArtworkHeroBackdrop(
    artworkPath: String?,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    onDominantColorChanged: (Color) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = LocalAirmedyColors.current
    val bitmap = rememberArtworkThumbnail(artworkPath, targetPx = 240)
    var dominant by remember(artworkPath) { mutableStateOf(colors.background) }
    LaunchedEffect(bitmap, colors.background) {
        dominant = bitmap?.let { image -> withContext(Dispatchers.Default) { artworkDominantColor(image) } } ?: colors.background
        onDominantColorChanged(dominant)
    }
    val animatedDominant by animateColorAsState(dominant, tween(280, easing = FastOutSlowInEasing), label = "detail-hero-artwork-colour")
    Box(modifier = modifier.then(if (hazeState == null) Modifier else Modifier.hazeSource(hazeState)).background(colors.background)) {
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to animatedDominant.copy(alpha = 0.52f),
                    0.68f to colors.background.copy(alpha = 0.10f),
                    1f to colors.background,
                ),
            ),
        )
        Box(Modifier.matchParentSize().background(colors.background.copy(alpha = 0.28f)))
        content()
    }
}

private fun artworkDominantColor(bitmap: ImageBitmap): Color {
    val sample = android.graphics.Bitmap.createScaledBitmap(bitmap.asAndroidBitmap(), 24, 24, true)
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

/** Centered reusable identity and primary-action header for entity detail pages. */
@Composable
fun DetailHero(
    title: String,
    subtitle: String? = null,
    metadata: String? = null,
    playLabel: String,
    shuffleLabel: String,
    moreLabel: String,
    modifier: Modifier = Modifier,
    artworkPath: String? = null,
    artworkShape: DetailHeroArtworkShape = DetailHeroArtworkShape.Square,
    artworkSize: Dp = 248.dp,
    fallbackSymbol: String = MaterialSymbols.Album,
    showArtwork: Boolean = true,
    artworkContent: (@Composable () -> Unit)? = null,
    onPlayClick: () -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
) {
    val colors = LocalAirmedyColors.current
    val bitmap = rememberArtworkThumbnail(artworkPath, targetPx = 480)
    val artworkClip = if (artworkShape == DetailHeroArtworkShape.Circle) CircleShape else RoundedCornerShape(16.dp)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showArtwork) {
            Box(
                modifier = Modifier.size(artworkSize).clip(artworkClip).background(colors.glassElevated),
                contentAlignment = Alignment.Center,
            ) {
                if (artworkContent != null) artworkContent()
                else if (bitmap != null) Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                else MaterialSymbol(fallbackSymbol, null, size = 44.dp, tint = colors.textMuted)
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = colors.textMain, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            subtitle?.takeIf(String::isNotBlank)?.let { value ->
                Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            metadata?.takeIf(String::isNotBlank)?.let { value ->
                Text(value, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DetailHeroGlassAction(MaterialSymbols.Shuffle, shuffleLabel, onShuffleClick)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.textMain)
                    .semantics { contentDescription = playLabel }
                    .clickable(role = Role.Button, onClick = onPlayClick)
                    .padding(horizontal = 26.dp, vertical = 10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbol(MaterialSymbols.PlayArrow, null, size = 20.dp, tint = colors.background, filled = true)
                    Text(playLabel, style = MaterialTheme.typography.labelLarge, color = colors.background)
                }
            }
            DetailHeroGlassAction(MaterialSymbols.MoreVert, moreLabel, onMoreClick)
        }
    }
}

@Composable
private fun DetailHeroGlassAction(symbol: String, label: String, onClick: () -> Unit) {
    val colors = LocalAirmedyColors.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.glassElevated).border(1.dp, colors.borderGlass, CircleShape),
            contentAlignment = Alignment.Center,
        ) { MaterialSymbol(symbol, null, size = 20.dp, tint = colors.textMain, filled = true) }
    }
}
