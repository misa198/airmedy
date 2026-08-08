package me.misa198.airmedy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.settings.ThemePreferences

data class AppUiState(
    val selectedDestination: AppDestination = AppDestination.Home,
    val themeMode: ThemeMode = ThemeMode.System,
    val destinationStacks: Map<AppDestination, List<AppStackPage>> = rootDestinationStacks(),
    val syncDevice: SyncDevice? = null,
) {
    fun stackFor(destination: AppDestination): List<AppStackPage> =
        destinationStacks[destination].orEmpty().ifEmpty { listOf(AppStackPage.Root) }

    val currentPage: AppStackPage
        get() = stackFor(selectedDestination).last()
}

class MainViewModel(
    private val themePreferences: ThemePreferences,
) : ViewModel() {
    private val selectedDestination = MutableStateFlow(AppDestination.Home)
    private val destinationStacks = MutableStateFlow(rootDestinationStacks())

    val uiState: StateFlow<AppUiState> = combine(
        themePreferences.themeMode,
        selectedDestination,
        destinationStacks,
    ) { themeMode, destination, pages ->
        AppUiState(
            selectedDestination = destination,
            themeMode = themeMode,
            destinationStacks = pages,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = AppUiState(),
    )

    fun selectDestination(destination: AppDestination) {
        if (destination == selectedDestination.value) {
            destinationStacks.value = destinationStacks.value + (
                destination to listOf(AppStackPage.Root)
            )
            return
        }
        selectedDestination.value = destination
    }

    fun openHomeSampleDetail() {
        selectedDestination.value = AppDestination.Home
        val homeStack = destinationStacks.value.getValue(AppDestination.Home)
        if (homeStack.lastOrNull() != AppStackPage.HomeSampleDetail) {
            destinationStacks.value = destinationStacks.value + (
                AppDestination.Home to homeStack + AppStackPage.HomeSampleDetail
            )
        }
    }

    fun openSettingsAppearance() {
        selectedDestination.value = AppDestination.Settings
        val settingsStack = destinationStacks.value.getValue(AppDestination.Settings)
        if (settingsStack.lastOrNull() != AppStackPage.SettingsAppearance) {
            destinationStacks.value = destinationStacks.value + (
                AppDestination.Settings to settingsStack + AppStackPage.SettingsAppearance
            )
        }
    }

    fun openSettingsSync() {
        selectedDestination.value = AppDestination.Settings
        val settingsStack = destinationStacks.value.getValue(AppDestination.Settings)
        if (settingsStack.lastOrNull() != AppStackPage.SettingsSync) {
            destinationStacks.value = destinationStacks.value + (
                AppDestination.Settings to settingsStack + AppStackPage.SettingsSync
            )
        }
    }

    fun openSettingsAbout() {
        selectedDestination.value = AppDestination.Settings
        val settingsStack = destinationStacks.value.getValue(AppDestination.Settings)
        if (settingsStack.lastOrNull() != AppStackPage.SettingsAbout) {
            destinationStacks.value = destinationStacks.value + (
                AppDestination.Settings to settingsStack + AppStackPage.SettingsAbout
            )
        }
    }

    fun navigateBack() {
        val destination = selectedDestination.value
        val stack = destinationStacks.value.getValue(destination)
        if (stack.size > 1) {
            destinationStacks.value = destinationStacks.value + (destination to stack.dropLast(1))
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(themeMode)
        }
    }

    class Factory(
        private val themePreferences: ThemePreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(themePreferences) as T
        }
    }
}
