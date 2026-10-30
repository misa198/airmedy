package me.misa198.airmedy.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
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

/** The host owns mode and playback; grip and footer stay outside the changing content. */
@Composable
internal fun SquarePlayerLayout(
    selectedPanel: FullScreenPlayerPanel?,
    metadata: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    panelContent: @Composable (Dp) -> Unit,
    footer: @Composable () -> Unit,
) {
    val contentAlpha = remember(selectedPanel) { Animatable(0f) }
    LaunchedEffect(selectedPanel) { contentAlpha.animateTo(1f, tween(220)) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(bottom = 16.dp)
            .testTag("square_player_layout"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp).testTag("square_player_grip")) {
            FullScreenPlayerDragHandle()
        }
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = contentAlpha.value },
            contentAlignment = Alignment.Center,
        ) {
            val panelWidth = maxWidth
            key(selectedPanel) {
                if (selectedPanel == null) {
                    Column(
                        Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp).widthIn(max = 480.dp)
                            .fillMaxWidth().fillMaxHeight().testTag("square_player_main"),
                    ) {
                        metadata()
                        Spacer(Modifier.weight(1f))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            controls()
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize().then(
                        if (selectedPanel == FullScreenPlayerPanel.Lyrics) Modifier.padding(horizontal = 24.dp)
                        else Modifier,
                    )) {
                        panelContent(panelWidth)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("square_player_footer")) {
            footer()
        }
    }
}
