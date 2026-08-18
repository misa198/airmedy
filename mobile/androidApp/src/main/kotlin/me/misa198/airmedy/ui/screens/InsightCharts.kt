package me.misa198.airmedy.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private const val ChartAnimationMillis = 400

@Composable
internal fun InsightBarChart(points: List<InsightPoint>, description: String, modifier: Modifier = Modifier) {
    val colors = LocalAirmedyColors.current
    val modelProducer = remember { CartesianChartModelProducer() }
    val pointCount = points.size.coerceAtLeast(7)
    val column = rememberLineComponent(
        fill = Fill(Brush.verticalGradient(listOf(colors.primary, colors.primary.copy(alpha = .22f)))),
        thickness = (126f / pointCount).coerceIn(2f, 18f).dp,
        shape = RoundedCornerShape(6.dp),
    )
    val markerLabels = points.map { formatDuration(it.value) }
    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(column),
            columnCollectionSpacing = (84f / pointCount).coerceAtLeast(1f).dp,
        ),
        marker = rememberInsightMarker(markerLabels),
    )
    LaunchedEffect(points) {
        modelProducer.runTransaction { columnModel { series(points.map { it.value }) } }
    }
    Column(modifier) {
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(136.dp).semantics { contentDescription = description },
            animationSpec = tween(ChartAnimationMillis),
        )
        ChartEdgeLabels(points.firstOrNull()?.date.orEmpty(), points.lastOrNull()?.date.orEmpty())
    }
}

@Composable
internal fun InsightLineChart(points: List<InsightPoint>, description: String, modifier: Modifier = Modifier) {
    val colors = LocalAirmedyColors.current
    val modelProducer = remember { CartesianChartModelProducer() }
    val pointProvider = if (points.size == 1) {
        LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(rememberShapeComponent(Fill(colors.primary), CircleShape)),
        )
    } else null
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(colors.primary)),
        areaFill = LineCartesianLayer.AreaFill.single(
            Fill(Brush.verticalGradient(listOf(colors.primary.copy(alpha = .38f), colors.primary.copy(alpha = 0f)))),
        ),
        pointProvider = pointProvider,
        interpolator = LineCartesianLayer.Interpolator.catmullRom(),
    )
    val markerLabels = points.map { number(it.value) }
    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(LineCartesianLayer.LineProvider.series(line)),
        marker = rememberInsightMarker(markerLabels),
    )
    LaunchedEffect(points) {
        modelProducer.runTransaction { lineModel { series(points.map { it.value }) } }
    }
    Column(modifier) {
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            modifier = Modifier.fillMaxWidth().height(136.dp).semantics { contentDescription = description },
            animationSpec = tween(ChartAnimationMillis),
        )
        ChartEdgeLabels(points.firstOrNull()?.date.orEmpty(), points.lastOrNull()?.date.orEmpty())
    }
}

@Composable
internal fun InsightDonut(
    values: List<Pair<Int, androidx.compose.ui.graphics.Color>>,
    centerText: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { PieChartModelProducer() }
    val chart = rememberPieChart(
        sliceProvider = PieChart.SliceProvider.series(values.map { (_, color) -> PieChart.Slice(Fill(color)) }),
        outerSize = PieSize.Outer.fixed(120.dp),
        innerSize = PieSize.Inner.fixed(82.dp),
    )
    LaunchedEffect(values) {
        modelProducer.runTransaction { pieSeries { series(values.map { it.first }) } }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        PieChartHost(
            chart = chart,
            modelProducer = modelProducer,
            modifier = Modifier.height(132.dp).fillMaxWidth().semantics { contentDescription = description },
            animationSpec = tween(ChartAnimationMillis),
        )
        Text(
            centerText,
            color = LocalAirmedyColors.current.textMain,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChartEdgeLabels(first: String, last: String) {
    val colors = LocalAirmedyColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(first, style = MaterialTheme.typography.labelSmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(last, style = MaterialTheme.typography.labelSmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun rememberInsightMarker(labels: List<String>) = rememberDefaultCartesianMarker(
    label = rememberTextComponent(TextStyle(color = LocalAirmedyColors.current.textMain, fontSize = 12.sp)),
    valueFormatter = remember(labels) {
        DefaultCartesianMarker.ValueFormatter { _, targets -> labels.getOrNull(targets.firstOrNull()?.x?.toInt() ?: -1).orEmpty() }
    },
)
