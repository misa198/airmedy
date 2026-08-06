package me.misa198.airmedy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.tooling.preview.Preview
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private val OuterPillRadius = 36.dp
private val InnerPillRadius = 32.dp
private val PillGap = 4.dp
private val NavigationHeight = 72.dp

@Composable
fun App(
    uiState: AppUiState = AppUiState(),
    onDestinationSelected: (AppDestination) -> Unit = {},
    onThemeModeSelected: (ThemeMode) -> Unit = {},
) {
    AirmedyTheme(themeMode = uiState.themeMode) {
        val hazeState = rememberHazeState()
        Box(modifier = Modifier.fillMaxSize()) {
            AppDestinationContent(
                destination = uiState.selectedDestination,
                themeMode = uiState.themeMode,
                hazeState = hazeState,
                onThemeModeSelected = onThemeModeSelected,
            )
            FloatingNavigationBar(
                selectedDestination = uiState.selectedDestination,
                hazeState = hazeState,
                onDestinationSelected = onDestinationSelected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun AppDestinationContent(
    destination: AppDestination,
    themeMode: ThemeMode,
    hazeState: HazeState,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    val colors = LocalAirmedyColors.current
    Surface(
        color = colors.background,
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(destination.titleRes),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textMain,
            )
            Text(
                text = stringResource(destination.placeholderRes),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted,
            )
            if (destination == AppDestination.Settings) {
                ThemeModeSelector(
                    selectedThemeMode = themeMode,
                    onThemeModeSelected = onThemeModeSelected,
                )
            }
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selectedThemeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    val colors = LocalAirmedyColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.appearance_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textMain,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                val selected = mode == selectedThemeMode
                val background by animateColorAsState(
                    targetValue = if (selected) colors.primary.copy(alpha = 0.16f) else colors.glassElevated,
                    animationSpec = tween(durationMillis = 220),
                    label = "theme-mode-background",
                )
                val labelColor by animateColorAsState(
                    targetValue = if (selected) colors.primary else colors.textMain,
                    animationSpec = tween(durationMillis = 220),
                    label = "theme-mode-text",
                )
                Text(
                    text = stringResource(mode.labelRes),
                    color = labelColor,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.large)
                        .background(background)
                        .border(1.dp, colors.borderGlass, MaterialTheme.shapes.large)
                        .selectable(
                            selected = selected,
                            onClick = { onThemeModeSelected(mode) },
                            role = Role.RadioButton,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun FloatingNavigationBar(
    selectedDestination: AppDestination,
    hazeState: HazeState,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val outerPillShape = RoundedCornerShape(OuterPillRadius)
    Box(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(NavigationHeight)
            .clip(outerPillShape)
            .hazeEffect(hazeState) {
                inputScale = HazeInputScale.Auto
                blurEffect {
                    blurRadius = 30.dp
                    colorEffects = listOf(HazeColorEffect.tint(colors.glass))
                }
            }
            .background(colors.glass)
            .border(1.dp, colors.borderGlass, outerPillShape)
            .padding(PillGap),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / AppDestination.entries.size
            val maxIndicatorOffset = maxWidth - itemWidth
            var isDragging by remember { mutableStateOf(false) }
            var dragOffset by remember { mutableStateOf(0.dp) }
            val targetOffset = if (isDragging) dragOffset else itemWidth * selectedDestination.ordinal
            val indicatorOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = if (isDragging) {
                    snap()
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                },
                label = "navigation-selection-offset",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(itemWidth, maxIndicatorOffset, selectedDestination) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragOffset = itemWidth * selectedDestination.ordinal
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onDragEnd = {
                                val destinationIndex = (dragOffset / itemWidth)
                                    .toInt()
                                    .coerceIn(0, AppDestination.entries.lastIndex)
                                onDestinationSelected(AppDestination.entries[destinationIndex])
                                isDragging = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset = (dragOffset + dragAmount.x.toDp())
                                    .coerceIn(0.dp, maxIndicatorOffset)
                            },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(InnerPillRadius))
                        .background(colors.navigationActive),
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppDestination.entries.forEach { destination ->
                        FloatingNavigationItem(
                            destination = destination,
                            selected = destination == selectedDestination,
                            onClick = { onDestinationSelected(destination) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNavigationItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    val foreground by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.textMain,
        animationSpec = tween(durationMillis = 250),
        label = "navigation-item-foreground",
    )
    val destinationLabel = stringResource(destination.titleRes)
    val innerPillShape = RoundedCornerShape(InnerPillRadius)
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(innerPillShape)
            .semantics { contentDescription = destinationLabel }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(
            painter = painterResource(destination.iconRes),
            contentDescription = null,
            tint = foreground,
        )
        Text(
            text = destinationLabel,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
        )
    }
}

private val AppDestination.titleRes: Int
    get() = when (this) {
        AppDestination.Home -> R.string.destination_home
        AppDestination.Library -> R.string.destination_library
        AppDestination.Search -> R.string.destination_search
        AppDestination.Settings -> R.string.destination_settings
    }

private val AppDestination.placeholderRes: Int
    get() = when (this) {
        AppDestination.Home -> R.string.placeholder_home
        AppDestination.Library -> R.string.placeholder_library
        AppDestination.Search -> R.string.placeholder_search
        AppDestination.Settings -> R.string.placeholder_settings
    }

private val AppDestination.iconRes: Int
    get() = when (this) {
        AppDestination.Home -> LucideR.drawable.lucide_ic_house
        AppDestination.Library -> LucideR.drawable.lucide_ic_library_big
        AppDestination.Search -> LucideR.drawable.lucide_ic_search
        AppDestination.Settings -> LucideR.drawable.lucide_ic_settings
    }

@Preview(showBackground = true)
@Composable
private fun AppPreview() {
    App()
}
