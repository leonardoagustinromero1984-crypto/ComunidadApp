package com.comunidapp.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.comunidapp.app.data.repository.CatalogSpecies

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesDropdown(
    selectedCode: String,
    onSelected: (CatalogSpecies) -> Unit,
    options: List<CatalogSpecies>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = com.comunidapp.app.ui.components.leo.LeoRequiredField.label("Especie / tipo de animal")
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.code.equals(selectedCode, true) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { species ->
                DropdownMenuItem(
                    text = { Text(species.name) },
                    onClick = {
                        onSelected(species)
                        expanded = false
                    }
                )
            }
        }
    }
}
