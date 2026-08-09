package me.misa198.airmedy.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private val artworkCache = LruCache<String, ImageBitmap>(250)

@Composable
internal fun rememberArtworkThumbnail(artworkPath: String?): ImageBitmap? {
    if (artworkPath.isNullOrBlank()) return null
    val context = LocalContext.current
    val absolutePath = remember(artworkPath, context) {
        val file = File(artworkPath)
        if (file.isAbsolute) file.absolutePath else File(context.filesDir, artworkPath).absolutePath
    }

    var bitmap by remember(absolutePath) { mutableStateOf(artworkCache.get(absolutePath)) }

    LaunchedEffect(absolutePath) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                val file = File(absolutePath)
                if (!file.isFile) return@withContext null
                runCatching {
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

                    val targetPx = 120
                    var sampleSize = 1
                    while (boundsOptions.outWidth / (sampleSize * 2) >= targetPx &&
                        boundsOptions.outHeight / (sampleSize * 2) >= targetPx
                    ) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeFile(file.absolutePath, decodeOptions)?.asImageBitmap()
                }.getOrNull()
            }
            if (loaded != null) {
                artworkCache.put(absolutePath, loaded)
                bitmap = loaded
            }
        }
    }
    return bitmap
}

@Composable
fun TrackRow(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    artworkPath: String? = null,
    onClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
) {
    val colors = LocalAirmedyColors.current
    val bitmap = rememberArtworkThumbnail(artworkPath)
    val clickModifier = remember(onClick) {
        if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }
    }
    val handleMoreClick = remember(onMoreClick) {
        { onMoreClick?.invoke(); Unit }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(top = 6.dp, end = 8.dp, bottom = 6.dp, start = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Artwork
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.glassElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_music),
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // 2. Title & Artist Column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // 3. More options (...) button
        IconButton(
            onClick = handleMoreClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_ellipsis),
                contentDescription = stringResource(R.string.track_row_more_options),
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
