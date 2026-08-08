package me.misa198.airmedy.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.composables.icons.lucide.R as LucideR
import me.misa198.airmedy.AppDestination
import me.misa198.airmedy.AppStackPage
import me.misa198.airmedy.R

@StringRes
internal fun AppStackPage.titleRes(destination: AppDestination): Int = when (this) {
    AppStackPage.HomeSampleDetail -> R.string.home_sample_page_title
    AppStackPage.LibraryTracks -> R.string.library_tracks
    AppStackPage.SettingsAppearance -> R.string.appearance_title
    AppStackPage.SettingsSync -> R.string.sync_title
    AppStackPage.SettingsSyncScanner -> R.string.sync_scan_title
    AppStackPage.SettingsAbout -> R.string.about_title
    AppStackPage.Root -> destination.titleRes
}

internal val AppDestination.titleRes: Int
    @StringRes get() = when (this) {
        AppDestination.Home -> R.string.destination_home
        AppDestination.Library -> R.string.destination_library
        AppDestination.Search -> R.string.destination_search
        AppDestination.Settings -> R.string.destination_settings
    }

internal val AppDestination.placeholderRes: Int
    @StringRes get() = when (this) {
        AppDestination.Home -> R.string.placeholder_home
        AppDestination.Library -> R.string.placeholder_library
        AppDestination.Search -> R.string.placeholder_search
        AppDestination.Settings -> R.string.placeholder_settings
    }

internal val AppDestination.iconRes: Int
    @DrawableRes get() = when (this) {
        AppDestination.Home -> LucideR.drawable.lucide_ic_house
        AppDestination.Library -> LucideR.drawable.lucide_ic_library_big
        AppDestination.Search -> LucideR.drawable.lucide_ic_search
        AppDestination.Settings -> LucideR.drawable.lucide_ic_settings
    }
