package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One visible label + chips. Callers must not add a second label above.
 */
@Composable
fun <T> LeoEnumChipRow(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    labelOf: (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!label.isNullOrBlank()) {
            Text(
                text = LeoRequiredField.label(label, required),
                style = MaterialTheme.typography.labelLarge
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { entry ->
                LeoFilterChip(
                    label = labelOf(entry),
                    selected = selected == entry,
                    onClick = { onSelect(entry) }
                )
            }
        }
    }
}
