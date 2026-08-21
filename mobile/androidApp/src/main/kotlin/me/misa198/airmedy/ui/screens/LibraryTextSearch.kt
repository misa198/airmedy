package me.misa198.airmedy.ui.screens

import java.text.Normalizer
import java.util.Locale

/** Matches desktop library search: trim, ignore case, and fold diacritics. */
internal fun normalizedLibrarySearchText(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "")
    .lowercase(Locale.ROOT)
    .replace('đ', 'd')

internal fun matchesLibraryTextFilter(query: String, vararg values: String): Boolean {
    val normalizedQuery = normalizedLibrarySearchText(query)
    return normalizedQuery.isEmpty() || values.any { normalizedLibrarySearchText(it).contains(normalizedQuery) }
}
