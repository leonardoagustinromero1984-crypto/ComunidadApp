package com.comunidapp.app.ui.screens.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.LocationSelection
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.displayOf
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.v2.V2LocationPicker
import com.comunidapp.app.ui.theme.LeoDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagedPetPickerSheet(
    pets: List<Pet>,
    selectedPetId: String?,
    onSelect: (Pet?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("🐾 Mascota")
            if (pets.isEmpty()) {
                Text("No hay mascotas que puedas asociar.", modifier = Modifier.padding(vertical = 12.dp))
            } else {
                LazyColumn {
                    items(pets, key = { it.id }) { pet ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(pet) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PetImage(
                                imageUrl = pet.photoUrl,
                                modifier = Modifier.size(48.dp),
                                contentDescription = pet.name
                            )
                            Text(if (selectedPetId == pet.id) "✓ ${pet.name}" else pet.name)
                        }
                    }
                }
            }
            TextButton(onClick = { onSelect(null) }) { Text("Quitar selección") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerSheet(
    selection: LocationSelection,
    onSelectionChange: (LocationSelection) -> Unit,
    onDismiss: () -> Unit
) {
    val nodes = DataProvider.locationCatalogRepository.snapshot()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(LeoDimens.SpaceMd)) {
            Text("📍 Ubicación")
            V2LocationPicker(
                selection = selection,
                onSelectionChange = onSelectionChange
            )
            selection.localityId?.let { id ->
                val label = nodes.displayOf(selection).label
                Text("Seleccionada: ${label.ifBlank { id }}", modifier = Modifier.padding(top = 8.dp))
            }
            TextButton(onClick = { onSelectionChange(LocationSelection()) }) { Text("Quitar") }
            TextButton(onClick = onDismiss) { Text("Listo") }
        }
    }
}
