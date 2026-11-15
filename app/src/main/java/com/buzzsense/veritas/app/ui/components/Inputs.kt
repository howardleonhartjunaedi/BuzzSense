package com.buzzsense.veritas.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.buzzsense.veritas.app.ui.theme.BuzzTheme

/**
 * Flat field: mist container, no indicator line or outline. Focus shifts the
 * container one tone instead of drawing a border.
 */
@Composable
fun BuzzTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    /** e.g. PasswordVisualTransformation() for secrets such as an API key. */
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val scheme = BuzzTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BuzzTheme.spacing.xs)) {
        if (label != null) BuzzEyebrow(label)
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            isError = isError,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            textStyle = BuzzTheme.typography.bodyLarge,
            shape = BuzzTheme.shapes.small,
            placeholder = placeholder?.let { { Text(it, color = scheme.onSurface.copy(alpha = 0.4f)) } },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = scheme.surfaceContainer,
                unfocusedContainerColor = scheme.surfaceContainerLow,
                disabledContainerColor = scheme.surfaceContainerLow.copy(alpha = 0.5f),
                errorContainerColor = scheme.errorContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                cursorColor = scheme.primary,
                focusedTextColor = scheme.onSurface,
                unfocusedTextColor = scheme.onSurface,
            ),
        )
        if (supportingText != null) {
            Text(
                supportingText,
                style = BuzzTheme.typography.labelMedium,
                color = if (isError) scheme.error else scheme.outline,
            )
        }
    }
}

@BuzzPreviews
@Composable
private fun BuzzTextFieldPreview() {
    BuzzPreviewContainer {
        var text by remember { mutableStateOf("") }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BuzzTextField(value = text, onValueChange = { text = it }, label = "House number", placeholder = "e.g. RT 04 / 12")
            BuzzTextField(
                value = "abc",
                onValueChange = {},
                label = "Containers checked",
                supportingText = "Enter a number",
                isError = true,
            )
        }
    }
}
