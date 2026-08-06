package me.misa198.airmedy

enum class AppDestination {
    Home,
    Library,
    Search,
    Settings,
}

/** Pages that can be placed on a destination's independent navigation stack. */
enum class AppStackPage {
    Root,
    HomeSampleDetail,
    SettingsAppearance,
}

fun rootDestinationStacks(): Map<AppDestination, List<AppStackPage>> =
    AppDestination.entries.associateWith { listOf(AppStackPage.Root) }
