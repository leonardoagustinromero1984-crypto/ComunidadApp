package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import com.comunidapp.app.ui.components.HealthOptionDropdown
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.NeutralBorder

@Composable
fun LeoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    required: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        label = label?.let { { Text(LeoRequiredField.label(it, required)) } },
        placeholder = placeholder?.let { { Text(it, color = MutedText) } },
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(LeoDimens.RadiusField),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = BrandWhite,
            unfocusedContainerColor = BrandWhite,
            disabledContainerColor = BrandWhite,
            focusedBorderColor = BrandOrangeSoft,
            unfocusedBorderColor = NeutralBorder,
            focusedTextColor = BrandText,
            unfocusedTextColor = BrandText
        )
    )
}

@Composable
fun LeoVerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    keyboardActions: KeyboardActions = KeyboardActions.Default
) = LeoTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    label = label,
    placeholder = placeholder,
    enabled = enabled,
    isError = isError,
    supportingText = supportingText,
    singleLine = singleLine,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions
)

/** Canonical option selector — reuses [HealthOptionDropdown], not a second menu system. */
@Composable
fun LeoVerSelector(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = HealthOptionDropdown(label, options, selected, onSelected, modifier, enabled)
