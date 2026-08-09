package me.misa198.airmedy

import me.misa198.airmedy.settings.ThemeMode

/** User-initiated inputs accepted by the app shell. */
sealed interface AppIntent {
    data class SelectDestination(val destination: AppDestination) : AppIntent

    data class OpenPage(val page: AppStackPage) : AppIntent

    data object NavigateBack : AppIntent

    data class SetThemeMode(val themeMode: ThemeMode) : AppIntent

    data class SetReduceTransparency(val enabled: Boolean) : AppIntent

    data class OpenExternalUrl(val url: String) : AppIntent
}

/** One-time work delegated by the app shell to the Android host. */
sealed interface AppEffect {
    data class OpenExternalUrl(val url: String) : AppEffect
}
