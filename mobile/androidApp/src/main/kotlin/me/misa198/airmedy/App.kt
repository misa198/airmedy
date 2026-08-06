package me.misa198.airmedy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.tooling.preview.Preview
import com.composables.icons.lucide.R as LucideR
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.components.ActionList
import me.misa198.airmedy.ui.components.ActionListContainerStyle
import me.misa198.airmedy.ui.components.ActionListItem
import me.misa198.airmedy.ui.components.AirmedyGlassIconButton
import me.misa198.airmedy.ui.components.Card
import me.misa198.airmedy.ui.components.HomeDemoContent
import me.misa198.airmedy.ui.components.HeroCard
import me.misa198.airmedy.ui.components.Selection
import me.misa198.airmedy.ui.components.SelectionOption
import me.misa198.airmedy.ui.components.StackPageLayout
import me.misa198.airmedy.ui.components.StackPageHeader
import me.misa198.airmedy.ui.components.liquidGlassBackground
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

private val OuterPillRadius = 36.dp
private val InnerPillRadius = 32.dp
private val PillGap = 4.dp
private val NavigationHeight = 72.dp
private val NavigationBottomMargin = 4.dp

private data class PageKey(
    val destination: AppDestination,
    val page: AppStackPage,
)

internal fun shouldShowHeaderBlur(
    isContentScrolled: Boolean,
    destinationChanged: Boolean,
    previousHeaderWasBlurred: Boolean,
): Boolean = isContentScrolled || (destinationChanged && previousHeaderWasBlurred)

@Composable
fun App(
    uiState: AppUiState = AppUiState(),
    onDestinationSelected: (AppDestination) -> Unit = {},
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    onHomeSampleDetailSelected: () -> Unit = {},
    onAppearanceSelected: () -> Unit = {},
    onSyncSelected: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    AirmedyTheme(themeMode = uiState.themeMode) {
        val hazeState = rememberHazeState()
        val homeListState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        var previousDestination by remember { mutableStateOf(uiState.selectedDestination) }
        var previousHeaderWasBlurred by remember { mutableStateOf(false) }
        val destinationChanged = previousDestination != uiState.selectedDestination
        val animateHeaderChanges = !destinationChanged
        val currentPage = uiState.currentPage
        val navigationBottomPadding = NavigationHeight + NavigationBottomMargin +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val pageTitle = stringResource(currentPage.titleRes(uiState.selectedDestination))
        val showBack = currentPage != AppStackPage.Root
        val showSyncAddAction = currentPage == AppStackPage.SettingsSync && uiState.syncDevice == null
        BackHandler(enabled = showBack, onBack = onNavigateBack)
        val isContentScrolled = uiState.selectedDestination == AppDestination.Home &&
            currentPage == AppStackPage.Root &&
            (homeListState.firstVisibleItemIndex > 0 || homeListState.firstVisibleItemScrollOffset > 0)
        val showHeaderBlur = shouldShowHeaderBlur(
            isContentScrolled = isContentScrolled,
            destinationChanged = destinationChanged,
            previousHeaderWasBlurred = previousHeaderWasBlurred,
        )
        SideEffect {
            previousDestination = uiState.selectedDestination
            previousHeaderWasBlurred = isContentScrolled
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AppDestinationContent(
                destination = uiState.selectedDestination,
                page = currentPage,
                themeMode = uiState.themeMode,
                hazeState = hazeState,
                navigationBottomPadding = navigationBottomPadding,
                homeListState = homeListState,
                onThemeModeSelected = onThemeModeSelected,
                onHomeSampleDetailSelected = onHomeSampleDetailSelected,
                onAppearanceSelected = onAppearanceSelected,
                onSyncSelected = onSyncSelected,
                syncDevice = uiState.syncDevice,
                onNavigateBack = onNavigateBack,
            )
            StackPageHeader(
                title = pageTitle,
                hazeState = hazeState,
                isContentScrolled = showHeaderBlur,
                onBackClick = if (showBack) onNavigateBack else null,
                hasActions = showSyncAddAction,
                animateChanges = animateHeaderChanges,
                // A stack-page change can add or remove header controls, which
                // changes the title's available width. Give it a separate key
                // so the header crossfades rather than sliding through a reflow.
                titleStackKey = "${uiState.selectedDestination.name}:${currentPage.name}",
            ) {
                AirmedyGlassIconButton(
                    hazeState = hazeState,
                    iconRes = LucideR.drawable.lucide_ic_plus,
                    label = stringResource(R.string.sync_add_device),
                    onClick = {},
                )
            }
            FloatingNavigationBar(
                selectedDestination = uiState.selectedDestination,
                hazeState = hazeState,
                onDestinationSelected = { destination ->
                    if (destination == uiState.selectedDestination && destination == AppDestination.Home) {
                        coroutineScope.launch {
                            homeListState.animateScrollToItem(0)
                        }
                    }
                    onDestinationSelected(destination)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = NavigationBottomMargin),
            )
        }
    }
}

@Composable
private fun AppDestinationContent(
    destination: AppDestination,
    page: AppStackPage,
    themeMode: ThemeMode,
    hazeState: HazeState,
    navigationBottomPadding: androidx.compose.ui.unit.Dp,
    homeListState: androidx.compose.foundation.lazy.LazyListState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onHomeSampleDetailSelected: () -> Unit,
    onAppearanceSelected: () -> Unit,
    onSyncSelected: () -> Unit,
    syncDevice: SyncDevice?,
    onNavigateBack: () -> Unit,
) {
    val colors = LocalAirmedyColors.current
    val pageKey = PageKey(
        destination = destination,
        page = page,
    )
    Surface(
        color = colors.background,
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
    ) {
        StackPageLayout(
            title = stringResource(page.titleRes(destination)),
            hazeState = hazeState,
            contentBottomPadding = navigationBottomPadding,
            isContentScrolled = false,
            onBackClick = if (page != AppStackPage.Root) {
                onNavigateBack
            } else {
                null
            },
            showHeader = false,
        ) { modifier, contentPadding ->
            AnimatedContent(
                targetState = pageKey,
                modifier = modifier,
                transitionSpec = {
                    if (targetState.destination != initialState.destination) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (targetState.isForwardFrom(initialState)) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "stack-page-content",
            ) { currentPage ->
                when (currentPage.destination) {
                    AppDestination.Home -> if (currentPage.page == AppStackPage.Root) {
                        HomeDemoContent(
                            modifier = Modifier.fillMaxSize(),
                            listState = homeListState,
                            contentPadding = contentPadding,
                            onOpenSampleDetail = onHomeSampleDetailSelected,
                        )
                    } else {
                        HomeSampleDetailContent(
                            modifier = Modifier.padding(contentPadding),
                        )
                    }
                    AppDestination.Settings -> when (currentPage.page) {
                        AppStackPage.SettingsAppearance -> AppearanceContent(
                            modifier = Modifier.padding(contentPadding),
                            themeMode = themeMode,
                            onThemeModeSelected = onThemeModeSelected,
                        )
                        AppStackPage.SettingsSync -> SyncContent(
                            syncDevice = syncDevice,
                            modifier = Modifier.padding(contentPadding),
                        )
                        else -> SettingsContent(
                            modifier = Modifier.padding(contentPadding),
                            onAppearanceSelected = onAppearanceSelected,
                            onSyncSelected = onSyncSelected,
                        )
                    }
                    else -> PlaceholderContent(
                        destination = currentPage.destination,
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
        }
    }
}

private fun PageKey.isForwardFrom(previous: PageKey): Boolean = when {
    page == AppStackPage.HomeSampleDetail -> true
    page == AppStackPage.SettingsAppearance -> true
    page == AppStackPage.SettingsSync -> true
    previous.page == AppStackPage.HomeSampleDetail -> false
    previous.page == AppStackPage.SettingsAppearance -> false
    previous.page == AppStackPage.SettingsSync -> false
    else -> false
}

private fun AppStackPage.titleRes(destination: AppDestination): Int = when (this) {
    AppStackPage.HomeSampleDetail -> R.string.home_sample_page_title
    AppStackPage.SettingsAppearance -> R.string.appearance_title
    AppStackPage.SettingsSync -> R.string.sync_title
    AppStackPage.Root -> destination.titleRes
}

@Composable
private fun HomeSampleDetailContent(modifier: Modifier = Modifier) {
    val colors = LocalAirmedyColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_sample_page_heading),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textMain,
        )
        Text(
            text = stringResource(R.string.home_sample_page_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun PlaceholderContent(destination: AppDestination, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(destination.placeholderRes),
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = LocalAirmedyColors.current.textMuted,
    )
}

@Composable
private fun SettingsContent(
    onAppearanceSelected: () -> Unit,
    onSyncSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ActionList(
            items = listOf(
                ActionListItem(
                    R.string.settings_appearance,
                    LucideR.drawable.lucide_ic_palette,
                    onClick = onAppearanceSelected,
                ),
                ActionListItem(
                    R.string.settings_sync,
                    LucideR.drawable.lucide_ic_refresh_cw,
                    onClick = onSyncSelected,
                ),
                ActionListItem(R.string.settings_playback, LucideR.drawable.lucide_ic_play),
                ActionListItem(R.string.settings_integration, LucideR.drawable.lucide_ic_plug),
                ActionListItem(R.string.settings_about, LucideR.drawable.lucide_ic_info),
            ),
            containerStyle = ActionListContainerStyle.Card,
        )
    }
}

@Composable
private fun SyncContent(
    syncDevice: SyncDevice?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAirmedyColors.current
    Column(modifier = modifier) {
        if (syncDevice == null) {
            HeroCard(
                iconRes = LucideR.drawable.lucide_ic_plug,
                title = stringResource(R.string.sync_empty_title),
                description = stringResource(R.string.sync_empty_description),
            )
        } else {
            Card(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = syncDevice.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textMain,
                    )
                    Text(
                        text = stringResource(syncDevice.type.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Text(
                        text = stringResource(R.string.sync_status_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primary,
                    )
                    Text(
                        text = stringResource(R.string.sync_revoke),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.glassElevated)
                            .border(1.dp, colors.borderGlass, RoundedCornerShape(24.dp))
                            .clickable(
                                onClick = {},
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textMain,
                    )
                }
            }
        }
    }
}

private val SyncDeviceType.labelRes: Int
    get() = when (this) {
        SyncDeviceType.Desktop -> R.string.sync_device_type_desktop
    }

@Composable
private fun AppearanceContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Selection(
                labelRes = R.string.appearance_theme_title,
                options = ThemeMode.entries.map { mode ->
                    SelectionOption(value = mode, labelRes = mode.labelRes)
                },
                selectedValue = themeMode,
                onValueSelected = onThemeModeSelected,
            )
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
            .liquidGlassBackground(hazeState, colors)
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
                animationSpec = if (isDragging) snap() else spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
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
                            onDragCancel = { isDragging = false },
                            onDragEnd = {
                                val destinationIndex = (dragOffset / itemWidth).toInt()
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
                FloatingNavigationVisuals(
                    foreground = colors.textMain,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationForegroundMask(
                            indicatorOffset = indicatorOffset,
                            itemWidth = itemWidth,
                            clipOp = ClipOp.Difference,
                        ),
                )
                FloatingNavigationVisuals(
                    foreground = colors.primary,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationForegroundMask(
                            indicatorOffset = indicatorOffset,
                            itemWidth = itemWidth,
                            clipOp = ClipOp.Intersect,
                        ),
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppDestination.entries.forEach { destination ->
                        FloatingNavigationTarget(
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

private fun Modifier.navigationForegroundMask(
    indicatorOffset: androidx.compose.ui.unit.Dp,
    itemWidth: androidx.compose.ui.unit.Dp,
    clipOp: ClipOp,
): Modifier = drawWithContent {
    val contentDrawScope = this
    // Modifier.offset rounds Dp to whole pixels. Match that conversion so the
    // inactive and active foregrounds meet on the pill's exact edge.
    val pillLeft = indicatorOffset.roundToPx().toFloat()
    val pillWidth = itemWidth.roundToPx().toFloat()
    val pillRadius = InnerPillRadius.roundToPx().toFloat()
    val pillPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = pillLeft,
                top = 0f,
                right = pillLeft + pillWidth,
                bottom = size.height,
                radiusX = pillRadius,
                radiusY = pillRadius,
            ),
        )
    }
    clipPath(pillPath, clipOp = clipOp) { contentDrawScope.drawContent() }
}

@Composable
private fun FloatingNavigationVisuals(
    foreground: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppDestination.entries.forEach { destination ->
            FloatingNavigationVisual(
                destination = destination,
                foreground = foreground,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FloatingNavigationVisual(
    destination: AppDestination,
    foreground: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val destinationLabel = stringResource(destination.titleRes)
    Column(
        modifier = modifier
            .fillMaxSize(),
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

@Composable
private fun FloatingNavigationTarget(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinationLabel = stringResource(destination.titleRes)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(InnerPillRadius))
            .semantics { contentDescription = destinationLabel }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
    )
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

@Preview(showBackground = true)
@Composable
private fun SyncConnectedPreview() {
    App(
        uiState = AppUiState(
            selectedDestination = AppDestination.Settings,
            destinationStacks = rootDestinationStacks() + (
                AppDestination.Settings to listOf(AppStackPage.Root, AppStackPage.SettingsSync)
            ),
            syncDevice = SyncDevice(
                name = "Airmedy Desktop",
                type = SyncDeviceType.Desktop,
            ),
        ),
    )
}
