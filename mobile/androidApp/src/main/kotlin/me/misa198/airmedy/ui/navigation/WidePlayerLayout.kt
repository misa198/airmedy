package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The grip spans both columns; queue reordering stays inside the right pane. */
@Composable
internal fun WidePlayerLayout(
    selectedPanel: FullScreenPlayerPanel,
    artwork: @Composable (Modifier) -> Unit,
    metadata: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    panelContent: @Composable (Dp) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val compact = maxHeight < 480.dp
        val spacing = if (compact) 8.dp else 16.dp
        Column(
            Modifier.fillMaxSize().padding(bottom = spacing).testTag("wide_player_layout"),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = if (compact) 8.dp else 20.dp)
                .testTag("wide_player_grip")) {
                FullScreenPlayerDragHandle()
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().testTag("wide_player_main")
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    if (compact) {
                        Row(Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                artwork(Modifier.size(minOf(maxWidth, maxHeight)).testTag(FullScreenPlayerArtworkTestTag))
                            }
                            Box(Modifier.weight(1f)) { metadata() }
                        }
                    } else {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            artwork(Modifier.size(minOf(maxWidth, maxHeight)).testTag(FullScreenPlayerArtworkTestTag))
                        }
                        metadata()
                    }
                    controls()
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().testTag("wide_player_panel")) {
                    val panelWidth = maxWidth
                    val alpha = remember(selectedPanel) { Animatable(0f) }
                    LaunchedEffect(selectedPanel) { alpha.animateTo(1f, tween(220)) }
                    key(selectedPanel) {
                        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.then(
                            if (selectedPanel == FullScreenPlayerPanel.Lyrics) Modifier.padding(horizontal = 24.dp)
                            else Modifier,
                        )) { panelContent(panelWidth) }
                    }
                }
            }
        }
    }
}
