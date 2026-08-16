package me.misa198.airmedy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DiscGridItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkPath: String? = null,
    val fallbackSymbol: String = MaterialSymbols.MusicNote,
)

fun LazyListScope.discGridItems(
    items: List<DiscGridItem>,
    verticalItemPadding: Dp = 4.dp,
    horizontalGap: Dp = 12.dp,
    horizontalContentPadding: Dp = 0.dp,
    onClick: ((String) -> Unit)? = null,
    onLongClick: ((String) -> Unit)? = null,
    itemWrapper: @Composable (DiscGridItem, Modifier, @Composable () -> Unit) -> Unit = { _, modifier, content ->
        Box(modifier) { content() }
    },
) {
    val pairs = items.chunked(2)
    items(pairs.size, key = { index -> pairs[index].first().id }) { index ->
        val pair = pairs[index]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalContentPadding, vertical = verticalItemPadding),
            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
        ) {
            for (item in pair) {
                itemWrapper(item, Modifier.weight(1f)) {
                    DiscCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        artworkPath = item.artworkPath,
                        fallbackSymbol = item.fallbackSymbol,
                        onClick = onClick?.let { { it(item.id) } },
                        onLongClick = onLongClick?.let { { it(item.id) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (pair.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
