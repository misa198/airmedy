package me.misa198.airmedy

internal fun shouldClearLibrarySearch(intent: AppIntent, currentPage: AppStackPage): Boolean =
    intent == AppIntent.NavigateBack && currentPage == AppStackPage.LibrarySearch
