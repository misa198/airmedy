package me.misa198.airmedy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.R
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
fun HomeDemoContent(
    onOpenSampleDetail: () -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.home_demo_eyebrow),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
                Text(
                    text = stringResource(R.string.home_demo_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textMain,
                )
                Text(
                    text = stringResource(R.string.home_demo_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMuted,
                )
            }
        }
        item { SampleArtwork() }
        item {
            Card(
                title = stringResource(R.string.home_demo_open_page),
                description = stringResource(R.string.home_demo_open_page_hint),
                onClick = onOpenSampleDetail,
            )
        }
        item {
            HomeActionListSection(
                title = stringResource(R.string.home_action_list_card_title),
                containerStyle = ActionListContainerStyle.Card,
                onOpenSampleDetail = onOpenSampleDetail,
            )
        }
        item {
            HomeActionListSection(
                title = stringResource(R.string.home_action_list_plain_title),
                containerStyle = ActionListContainerStyle.Plain,
                onOpenSampleDetail = onOpenSampleDetail,
            )
        }
        items(4) { section ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.glassElevated)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_demo_section_title, section + 1),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textMain,
                )
                Text(
                    text = stringResource(R.string.home_demo_section_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun HomeActionListSection(
    title: String,
    containerStyle: ActionListContainerStyle,
    onOpenSampleDetail: () -> Unit,
) {
    val colors = LocalAirmedyColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textMain,
        )
        ActionList(
            items = listOf(
                ActionListItem(
                    labelRes = R.string.home_action_one,
                    iconRes = LucideR.drawable.lucide_ic_circle_play,
                    onClick = onOpenSampleDetail,
                ),
                ActionListItem(
                    labelRes = R.string.home_action_two,
                    iconRes = LucideR.drawable.lucide_ic_list_music,
                    onClick = onOpenSampleDetail,
                ),
                ActionListItem(
                    labelRes = R.string.home_action_three,
                    iconRes = LucideR.drawable.lucide_ic_sliders_horizontal,
                    onClick = onOpenSampleDetail,
                ),
            ),
            containerStyle = containerStyle,
        )
    }
}

@Composable
private fun SampleArtwork() {
    val colors = LocalAirmedyColors.current
    val artworkDescription = stringResource(R.string.home_demo_artwork_description)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.glassElevated)
            .semantics { contentDescription = artworkDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            drawRoundRect(
                color = colors.primary.copy(alpha = 0.28f),
                size = Size(size.width, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
            )
            drawCircle(
                color = colors.glass,
                radius = size.minDimension * 0.32f,
                center = center,
            )
            drawCircle(
                color = colors.primary,
                radius = size.minDimension * 0.12f,
                center = center,
            )
            drawCircle(
                color = colors.textMain.copy(alpha = 0.70f),
                radius = size.minDimension * 0.035f,
                center = Offset(size.width * 0.50f, size.height * 0.50f),
            )
        }
    }
}
