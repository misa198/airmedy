package me.misa198.airmedy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

/** A capsule text input with a neutral inset surface, styled independently of Material text fields. */
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
    onDone: () -> Unit = {},
) {
    val colors = LocalAirmedyColors.current
    val shape = RoundedCornerShape((size.height / 2).dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(size.height.dp)
            .clip(shape)
            .background(colors.buttonSecondary)
            .semantics { contentDescription = placeholder }
            .padding(horizontal = size.horizontalPadding.dp),
        singleLine = true,
        textStyle = TextStyle(fontSize = size.textSize.sp, fontWeight = FontWeight.Medium, color = colors.textMain),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    BasicText(
                        text = placeholder,
                        style = TextStyle(fontSize = size.textSize.sp, fontWeight = FontWeight.Medium, color = colors.textMuted),
                    )
                }
                innerTextField()
            }
        },
    )
}
