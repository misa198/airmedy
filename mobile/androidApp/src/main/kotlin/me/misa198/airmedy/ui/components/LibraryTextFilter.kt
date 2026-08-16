package me.misa198.airmedy.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Full-width text filter displayed at the leading edge of a library collection. */
@Composable
fun LibraryTextFilter(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    showPlaceholderAndLeadingSymbol: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AirmedyTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        size = AirmedyTextFieldSize.Medium,
        leadingSymbol = MaterialSymbols.Search,
        showPlaceholderAndLeadingSymbol = showPlaceholderAndLeadingSymbol,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 10.dp),
    )
}
