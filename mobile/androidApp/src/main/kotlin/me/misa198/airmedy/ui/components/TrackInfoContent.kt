package me.misa198.airmedy.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.misa198.airmedy.R
import me.misa198.airmedy.sync.LibraryTrack
import me.misa198.airmedy.sync.metadataObject
import me.misa198.airmedy.ui.theme.AirmedyColors
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

internal enum class TrackAudioQuality { Lossy, Lossless, HiRes, Dsd, Unknown }

internal data class TrackQualityBadgeStyle(
    val foreground: Color,
    val background: Color,
    val border: Color,
)

internal fun trackQualityBadgeStyle(
    quality: TrackAudioQuality,
    colors: AirmedyColors,
): TrackQualityBadgeStyle? = when (quality) {
    // Desktop: bg-foreground/8 text-foreground/50 border-foreground/15.
    TrackAudioQuality.Lossy -> TrackQualityBadgeStyle(
        foreground = colors.textMain.copy(alpha = 0.50f),
        background = colors.textMain.copy(alpha = 0.08f),
        border = colors.textMain.copy(alpha = 0.15f),
    )
    // Desktop: bg-[#E11D48]/10 text-[#E11D48] border-[#E11D48]/20.
    TrackAudioQuality.Lossless -> TrackQualityBadgeStyle(
        foreground = colors.primary,
        background = colors.primary.copy(alpha = 0.10f),
        border = colors.primary.copy(alpha = 0.20f),
    )
    // Desktop: bg-amber-500/10 text-amber-500 border-amber-500/30.
    TrackAudioQuality.HiRes -> TrackQualityBadgeStyle(
        foreground = colors.qualityHiRes,
        background = colors.qualityHiRes.copy(alpha = 0.10f),
        border = colors.qualityHiRes.copy(alpha = 0.30f),
    )
    // Desktop: bg-fuchsia-500/10 text-fuchsia-400 border-fuchsia-400/30.
    TrackAudioQuality.Dsd -> TrackQualityBadgeStyle(
        foreground = colors.qualityDsd,
        background = colors.qualityDsdSurface.copy(alpha = 0.10f),
        border = colors.qualityDsd.copy(alpha = 0.30f),
    )
    TrackAudioQuality.Unknown -> null
}

internal data class TrackInfoValue(val labelRes: Int, val value: String)

internal fun trackAudioQuality(track: LibraryTrack): TrackAudioQuality {
    val metadata = track.metadataObject()
    val format = metadata.string("format").lowercase()
    val codec = metadata.string("codec").lowercase()
    val bitDepth = metadata.int("bit_depth")
    val sampleRate = metadata.long("sample_rate")

    if (format in setOf("mp3", "aac", "ogg", "opus")) return TrackAudioQuality.Lossy
    if (format in setOf("dsf", "dff")) return TrackAudioQuality.Dsd
    if (format in setOf("m4a", "mp4")) {
        if (codec.isBlank()) return TrackAudioQuality.Unknown
        if (codec != "alac") return TrackAudioQuality.Lossy
    }
    if (format in setOf("flac", "wav", "aiff", "ape", "wv", "m4a", "mp4")) {
        return if (bitDepth > 16 || (sampleRate ?: 0) > 48_000) TrackAudioQuality.HiRes else TrackAudioQuality.Lossless
    }
    return TrackAudioQuality.Unknown
}

internal fun trackInfoValues(track: LibraryTrack): List<TrackInfoValue> {
    val metadata = track.metadataObject()
    fun text(name: String) = metadata.string(name)
    fun number(name: String) = metadata.long(name)
    fun numericText(name: String) = number(name)?.toString().orEmpty()
    val discNumber = number("disc_number") ?: track.discNumber.toLong().takeIf { it > 0 }
    val trackNumber = number("track_number") ?: track.trackNumber.toLong().takeIf { it > 0 }
    val totalDiscs = number("total_discs")
    val totalTracks = number("total_tracks")
    val bitrate = number("bitrate")
    val sampleRate = number("sample_rate")
    val bitDepth = number("bit_depth")
    val fileSize = number("file_size")

    return listOf(
        TrackInfoValue(R.string.track_info_artist, text("raw_artist_names").ifBlank { track.artists }),
        TrackInfoValue(R.string.track_info_genre, text("raw_genre_names")),
        TrackInfoValue(R.string.track_info_year, numericText("year")),
        TrackInfoValue(R.string.track_info_composer, text("raw_composer_names")),
        TrackInfoValue(R.string.track_info_disc, trackNumberText(discNumber, totalDiscs)),
        TrackInfoValue(R.string.track_info_track, trackNumberText(trackNumber, totalTracks)),
        TrackInfoValue(R.string.track_info_play_count, (number("play_count") ?: track.playCount.toLong()).takeIf { it > 0 }?.toString().orEmpty()),
        TrackInfoValue(R.string.track_info_label, text("label")),
        TrackInfoValue(R.string.track_info_isrc, text("isrc")),
        TrackInfoValue(R.string.track_info_duration, formatTrackDuration(number("duration"))),
        TrackInfoValue(R.string.track_info_format, text("format").uppercase()),
        TrackInfoValue(R.string.track_info_bitrate, bitrate?.takeIf { it > 0 }?.let { "${it} kbps" }.orEmpty()),
        TrackInfoValue(R.string.track_info_sample_rate, sampleRate?.takeIf { it > 0 }?.let { formatSampleRate(it) }.orEmpty()),
        TrackInfoValue(R.string.track_info_bit_depth, bitDepth?.takeIf { it > 0 }?.let { "$it-bit" }.orEmpty()),
        TrackInfoValue(R.string.track_info_codec, text("codec").uppercase()),
        TrackInfoValue(R.string.track_info_bpm, numericText("bpm")),
        TrackInfoValue(R.string.track_info_file_size, fileSize?.takeIf { it > 0 }?.let(::formatFileSize).orEmpty()),
    ).filter { it.value.isNotBlank() }
}

private fun JsonObject?.string(name: String): String = (this?.get(name) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
private fun JsonObject?.long(name: String): Long? = (this?.get(name) as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()
private fun JsonObject?.int(name: String): Int = long(name)?.toInt() ?: 0

private fun trackNumberText(number: Long?, total: Long?): String = when {
    number == null || number <= 0 -> ""
    total != null && total > 1 -> "$number / $total"
    else -> number.toString()
}

internal fun formatTrackDuration(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return ""
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

internal fun trackInfoMaxContentHeight(availableHeight: Dp, topInset: Dp): Dp =
    (availableHeight - topInset - 8.dp).coerceAtLeast(0.dp)

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) { size /= 1024; unit++ }
    return "%.1f %s".format(size, units[unit])
}

private fun formatSampleRate(hertz: Long): String = if (hertz % 1000 == 0L) {
    "${hertz / 1000} kHz"
} else {
    "${hertz / 1000.0} kHz"
}

@Composable
internal fun TrackInfoContent(track: LibraryTrack, modifier: Modifier = Modifier) {
    val colors = LocalAirmedyColors.current
    val details = remember(track) { trackInfoValues(track) }
    val quality = remember(track) { trackAudioQuality(track) }
    val artwork = rememberArtworkThumbnail(track.artworkPath, targetPx = 480)
    val metadata = track.metadataObject()
    val albumArtist = metadata.string("raw_album_artist_names").ifBlank { track.artists }
    val subtitle = listOf(albumArtist, track.album).filter(String::isNotBlank).joinToString(" · ")
    val density = LocalDensity.current
    val topSafeInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(trackInfoMaxContentHeight(maxHeight, topSafeInset)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(192.dp).clip(RoundedCornerShape(16.dp)).background(colors.glassElevated).border(1.dp, colors.borderGlass, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (artwork != null) Image(artwork, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    else MaterialSymbol(MaterialSymbols.MusicNote, null, size = 56.dp, tint = colors.textMuted)
                }
                Text(track.title, modifier = Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textMain, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TrackQualityBadge(quality, Modifier.padding(top = 12.dp))
            }
        }
        if (details.isNotEmpty()) {
            item {
                Text(stringResource(R.string.track_info_details), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = colors.textMuted)
            }
            items(details, key = { it.labelRes }) { detail ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    Text(stringResource(detail.labelRes), Modifier.weight(0.42f), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    Text(detail.value, Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textMain, textAlign = TextAlign.End)
                }
            }
        }
        }
    }
}

@Composable
private fun TrackQualityBadge(quality: TrackAudioQuality, modifier: Modifier = Modifier) {
    if (quality == TrackAudioQuality.Unknown) return
    val colors = LocalAirmedyColors.current
    val style = trackQualityBadgeStyle(quality, colors) ?: return
    val (label, symbol) = when (quality) {
        TrackAudioQuality.Lossy -> R.string.track_info_quality_lossy to MaterialSymbols.MusicNote
        TrackAudioQuality.Lossless -> R.string.track_info_quality_lossless to MaterialSymbols.GraphicEq
        TrackAudioQuality.HiRes -> R.string.track_info_quality_hi_res to MaterialSymbols.Bolt
        TrackAudioQuality.Dsd -> R.string.track_info_quality_dsd to MaterialSymbols.Crown
        TrackAudioQuality.Unknown -> return
    }
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(style.background).border(1.dp, style.border, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(symbol, null, size = 14.dp, tint = style.foreground)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = style.foreground)
    }
}
