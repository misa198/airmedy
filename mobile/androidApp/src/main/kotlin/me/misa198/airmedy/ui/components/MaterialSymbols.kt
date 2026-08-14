@file:OptIn(ExperimentalTextApi::class)

package me.misa198.airmedy.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

val MaterialSymbolsFilledFontFamily = FontFamily(
    Font(
        resId = R.font.material_symbols_rounded,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("FILL", 1f),
        ),
    ),
)

val MaterialSymbolsOutlinedFontFamily = FontFamily(
    Font(
        resId = R.font.material_symbols_rounded,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("FILL", 0f),
        ),
    ),
)

object MaterialSymbols {
    const val Home = "home"
    const val Library = "graphic_eq"
    const val GraphicEq = "graphic_eq"
    const val Search = "search"
    const val Settings = "settings"
    const val PlayCircle = "play_circle"
    const val QueueMusic = "queue_music"
    const val Tune = "tune"
    const val MaskedTransitions = "masked_transitions"

    const val Gradient = "gradient"
    const val Subwoofer = "subwoofer"
    const val ChevronRight = "chevron_right"
    const val ChevronLeft = "chevron_left"
    const val UnfoldMore = "unfold_more"
    const val Check = "check"
    const val Close = "close"
    const val MusicNote = "music_note"
    const val Menu = "menu"
    const val MoreVert = "more_vert"
    const val ArrowUpward = "arrow_upward"
    const val ArrowDownward = "arrow_downward"
    const val SkipPrevious = "skip_previous"
    const val PlayArrow = "play_arrow"
    const val Pause = "pause"
    const val SkipNext = "skip_next"
    const val Shuffle = "shuffle"
    const val Repeat = "repeat"
    const val RepeatOne = "repeat_one"
    const val Reorder = "reorder"
    const val Mic = "mic"
    const val Chat = "chat"
    const val Cast = "cast"
    const val Airplay = "airplay"
    const val VolumeDown = "volume_down"
    const val VolumeUp = "volume_up"
    const val People = "group"
    const val Person = "person"
    const val Album = "album"
    const val Label = "label"
    const val Palette = "palette"
    const val Refresh = "refresh"
    const val Info = "info"
    const val Power = "power"
    const val DesktopWindows = "desktop_windows"
    const val Sync = "sync"
    const val Add = "add"
    const val Image = "image"
    const val FavoriteBorder = "favorite"
    const val Favorite = "favorite"
    const val SwapVert = "swap_vert"
    const val StylusFountainPen = "stylus_fountain_pen"
}

@Composable
fun MaterialSymbol(
    symbol: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalAirmedyColors.current.textMain,
    filled: Boolean = false,
) {
    Text(
        text = symbol,
        fontFamily = if (filled) MaterialSymbolsFilledFontFamily else MaterialSymbolsOutlinedFontFamily,
        style = TextStyle(
            fontSize = size.value.sp,
            color = tint,
            textAlign = TextAlign.Center,
        ),
        modifier = modifier.then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else Modifier,
        ),
    )
}
