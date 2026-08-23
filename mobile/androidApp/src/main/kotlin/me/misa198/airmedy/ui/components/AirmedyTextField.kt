package me.misa198.airmedy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import me.misa198.airmedy.R

/**
 * A capsule input with a neutral inset surface, styled independently of Material text fields.
 *
 * A non-empty value shows a clear action by default. Set [showClearButton] to `false` to hide
 * it, or provide [trailingContent] to replace it.
 */
enum class AirmedyTextFieldSize(val height: Int, val horizontalPadding: Int, val textSize: Int) {
    Small(height = 40, horizontalPadding = 16, textSize = 14),
    Medium(height = 48, horizontalPadding = 18, textSize = 15),
    Large(height = 56, horizontalPadding = 20, textSize = 16),
}

@Composable
fun AirmedyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    size: AirmedyTextFieldSize = AirmedyTextFieldSize.Medium,
    leadingSymbol: String? = null,
    showPlaceholderAndLeadingSymbol: Boolean = true,
    showClearButton: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    onDone: () -> Unit = {},
) {
    val colors = LocalAirmedyColors.current
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape((size.height / 2).dp)
    val showsDefaultClearAction = trailingContent == null && showClearButton && enabled && value.isNotEmpty()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(size.height.dp)
            .clip(shape)
            .background(colors.buttonSecondary)
            .semantics { contentDescription = placeholder }
            .padding(
                start = size.horizontalPadding.dp,
                end = if (showsDefaultClearAction) 0.dp else size.horizontalPadding.dp,
            ),
        singleLine = true,
        textStyle = TextStyle(fontSize = size.textSize.sp, fontWeight = FontWeight.Medium, color = colors.textMain),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
            onDone()
        }),
        decorationBox = { innerTextField ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                leadingSymbol?.takeIf { showPlaceholderAndLeadingSymbol }?.let { symbol ->
                    MaterialSymbol(symbol, contentDescription = null, size = 20.dp, tint = colors.textMuted)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && showPlaceholderAndLeadingSymbol) {
                        BasicText(
                            text = placeholder,
                            style = TextStyle(fontSize = size.textSize.sp, fontWeight = FontWeight.Medium, color = colors.textMuted),
                        )
                    }
                    innerTextField()
                }
                when {
                    trailingContent != null -> trailingContent()
                    showsDefaultClearAction -> AirmedyTextFieldClearButton(
                        label = stringResource(R.string.text_field_clear),
                        onClick = { onValueChange("") },
                        endPadding = size.horizontalPadding.dp,
                    )
                }
            }
        },
    )
}

@Composable
private fun AirmedyTextFieldClearButton(
    label: String,
    onClick: () -> Unit,
    endPadding: Dp,
) {
    val colors = LocalAirmedyColors.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .padding(end = endPadding)
            .semantics {
                contentDescription = label
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(colors.textFieldClear),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol(
                symbol = MaterialSymbols.Close,
                contentDescription = null,
                size = 12.dp,
                tint = colors.textMain,
            )
        }
    }
}
