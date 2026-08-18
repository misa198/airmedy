package me.misa198.airmedy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.components.Card
import me.misa198.airmedy.ui.components.MaterialSymbol
import me.misa198.airmedy.ui.components.Selection
import me.misa198.airmedy.ui.components.SelectionOption
import me.misa198.airmedy.ui.components.TrackAudioQuality
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun ChartCard(
    titleRes: Int,
    symbol: String,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    headerPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(contentPadding = contentPadding, modifier = Modifier.testTag("insight-card-$titleRes")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(headerPadding).padding(bottom = 16.dp)) {
            MaterialSymbol(symbol, null, size = 18.dp, tint = LocalAirmedyColors.current.textMuted)
            Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge, color = LocalAirmedyColors.current.textMuted, modifier = Modifier.padding(start = 8.dp))
        }
        content()
    }
}

@Composable
internal fun ResponsivePair(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints {
        if (maxWidth >= 600.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { first() }
                Column(Modifier.weight(1f)) { second() }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { first(); second() }
    }
}

@Composable
internal fun InsightPeriodSelector(selected: InsightPeriod, onSelected: (InsightPeriod) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(InsightPeriod.SevenDays to R.string.insight_range_7d, InsightPeriod.ThirtyDays to R.string.insight_range_30d, InsightPeriod.All to R.string.insight_range_all)
    Selection(
        options = options.map { (value, label) -> SelectionOption(value, label) },
        selectedValue = selected,
        onValueSelected = onSelected,
        modifier = modifier,
    )
}

@Composable
internal fun SourceControl(state: InsightUiState, onSelected: (InsightSourceFilter) -> Unit, modifier: Modifier = Modifier) {
    val options = buildList<SelectionOption<InsightSourceFilter>> {
        add(SelectionOption(InsightSourceFilter.All, R.string.insight_all_devices))
        add(SelectionOption(InsightSourceFilter.ThisPhone, R.string.insight_this_phone))
        if (state.hasDesktopSource) add(SelectionOption(InsightSourceFilter.Desktop, R.string.insight_desktop, state.desktopName))
        if (state.hasOtherSources) add(SelectionOption(InsightSourceFilter.Other, R.string.insight_other_devices))
    }
    Selection(
        options = options,
        selectedValue = state.sourceFilter,
        onValueSelected = onSelected,
        modifier = modifier,
    )
}

@Composable
internal fun MetricPair(
    first: Pair<Int, String>,
    second: Pair<Int, String>,
    secondModifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(first.first, first.second, Modifier.weight(1f))
        MetricCard(second.first, second.second, secondModifier.weight(1f))
    }
}

@Composable
internal fun MetricCard(labelRes: Int, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, contentPadding = PaddingValues(18.dp)) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium, color = LocalAirmedyColors.current.textMuted)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = LocalAirmedyColors.current.textMain, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
internal fun BreakdownRow(label: String, value: Int, total: Int, color: Color, duration: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, color = LocalAirmedyColors.current.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (duration) formatDuration(value) else number(value), style = MaterialTheme.typography.bodySmall, color = LocalAirmedyColors.current.textMuted)
        Text(stringResource(R.string.insight_percent_value, if (total == 0) 0 else value * 100 / total), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, color = LocalAirmedyColors.current.textMuted)
    }
}

@Composable internal fun SectionHeader(titleRes: Int, modifier: Modifier = Modifier) = Text(stringResource(titleRes), modifier, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = LocalAirmedyColors.current.textMain)
@Composable internal fun EmptyInsightText(textRes: Int) = Text(stringResource(textRes), Modifier.fillMaxWidth().padding(vertical = 36.dp), style = MaterialTheme.typography.bodyMedium, color = LocalAirmedyColors.current.textMuted)
internal fun number(value: Int): String = NumberFormat.getIntegerInstance().format(value)

@Composable
internal fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val days = safe / 86_400
    val hours = safe % 86_400 / 3_600
    val minutes = safe % 3_600 / 60
    return when {
        days > 0 -> stringResource(R.string.insight_duration_days_hours, days, hours)
        hours > 0 -> stringResource(R.string.insight_duration_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.insight_duration_minutes, minutes)
        else -> stringResource(R.string.insight_duration_seconds, safe)
    }
}

@Composable
internal fun qualityLabel(value: TrackAudioQuality): String = stringResource(when (value) {
    TrackAudioQuality.Lossy -> R.string.track_info_quality_lossy
    TrackAudioQuality.Lossless -> R.string.track_info_quality_lossless
    TrackAudioQuality.HiRes -> R.string.track_info_quality_hi_res
    TrackAudioQuality.Dsd -> R.string.track_info_quality_dsd
    TrackAudioQuality.Unknown -> R.string.track_info_quality_unknown
})

@Composable
internal fun qualityColor(value: TrackAudioQuality): Color {
    val colors = LocalAirmedyColors.current
    return when (value) {
        TrackAudioQuality.Lossy -> colors.primary
        TrackAudioQuality.Lossless -> colors.success
        TrackAudioQuality.HiRes -> colors.qualityHiRes
        TrackAudioQuality.Dsd -> colors.qualityDsd
        TrackAudioQuality.Unknown -> colors.textMuted
    }
}

@Composable
internal fun insightPalette(index: Int): Color {
    val colors = LocalAirmedyColors.current
    return listOf(colors.primary, colors.success, colors.qualityHiRes, colors.qualityDsd, colors.textMain.copy(alpha = .65f), colors.textMuted)[index.coerceAtMost(5)]
}
