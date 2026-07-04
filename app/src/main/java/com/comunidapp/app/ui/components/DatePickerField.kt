package com.comunidapp.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.comunidapp.app.ui.util.formatDisplayDate
import com.comunidapp.app.ui.util.isoDateFromMillis
import com.comunidapp.app.ui.util.millisFromIsoDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    isoDate: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "Seleccionar fecha"
) {
    var showDialog by remember { mutableStateOf(false) }
    val displayValue = isoDate.takeIf { it.isNotBlank() }?.let { formatDisplayDate(it) }.orEmpty()

    OutlinedTextField(
        value = displayValue,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        trailingIcon = {
            IconButton(onClick = { if (enabled) showDialog = true }, enabled = enabled) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Elegir fecha")
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true }
    )

    if (showDialog) {
        val initialMillis = millisFromIsoDate(isoDate) ?: System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateSelected(isoDateFromMillis(millis))
                        }
                        showDialog = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
