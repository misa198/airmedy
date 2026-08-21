package me.misa198.airmedy.ui

import java.text.Normalizer
import java.util.Locale

internal fun libraryAlphabeticalIndexLabel(value: String): String? {
    val initial = libraryAlphabeticalInitial(value) ?: return null
    return if (initial in 'a'..'z') initial.uppercaseChar().toString() else "#"
}

internal val libraryAlphabeticalComparator = Comparator<String> { first, second ->
    val group = libraryAlphabeticalGroup(first).compareTo(libraryAlphabeticalGroup(second))
    if (group != 0) group else if (libraryAlphabeticalNeedsFold(first) || libraryAlphabeticalNeedsFold(second)) {
        normalizedLibraryAlphabeticalText(first).compareTo(normalizedLibraryAlphabeticalText(second))
    } else String.CASE_INSENSITIVE_ORDER.compare(first, second)
}

private fun libraryAlphabeticalGroup(value: String): Int = when (libraryAlphabeticalInitial(value)) {
    in 'a'..'z' -> 0
    in '0'..'9' -> 1
    else -> 2
}

private fun libraryAlphabeticalInitial(value: String): Char? {
    val initial = value.trimStart().firstOrNull() ?: return null
    return when (initial) {
        'đ', 'Đ' -> 'd'
        in 'a'..'z', in 'A'..'Z', in '0'..'9' -> initial.lowercaseChar()
        else -> Normalizer.normalize(initial.toString(), Normalizer.Form.NFD).firstOrNull()?.lowercaseChar()
    }
}

private fun libraryAlphabeticalNeedsFold(value: String): Boolean = value.trimStart().firstOrNull()?.let {
    it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9'
} ?: false

private fun normalizedLibraryAlphabeticalText(value: String): String = Normalizer
    .normalize(value.trim(), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "")
    .lowercase(Locale.ROOT)
    .replace('đ', 'd')
