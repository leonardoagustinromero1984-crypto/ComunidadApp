package com.comunidapp.app.ui.screens.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.LocationSelection
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.displayOf
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.v2.V2LocationPicker
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagedPetPickerSheet(
    pets: List<Pet>,
    selectedPetId: String?,
    onSelect: (Pet?) -> Unit,
    onDismiss: () -> Unit,
    selectedPetIds: Set<String> = emptySet(),
    onConfirmSelection: ((Set<String>) -> Unit)? = null
) {
    val multi = onConfirmSelection != null
    var draft by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            if (multi) selectedPetIds else setOfNotNull(selectedPetId)
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm)) {
            Text(
                if (multi) "Mascotas" else "Mascota",
                style = LeoCardTitle,
                color = BrandText
            )
            if (pets.isEmpty()) {
                Text("No hay mascotas que puedas asociar.", modifier = Modifier.padding(vertical = 12.dp))
            } else {
                LazyColumn {
                    items(pets, key = { it.id }) { pet ->
                        val selected = if (multi) pet.id in draft else selectedPetId == pet.id
                        LeoListRow(
                            title = pet.name,
                            subtitle = listOfNotNull(
                                com.comunidapp.app.domain.pets.PetSpeciesCatalog.displayLabel(pet.species),
                                if (selected) "Seleccionada" else null
                            ).joinToString(" · "),
                            leading = {
                                PetImage(
                                    imageUrl = pet.photoUrl,
                                    modifier = Modifier.size(40.dp),
                                    cornerRadius = 20.dp,
                                    contentDescription = pet.name
                                )
                            },
                            onClick = {
                                if (multi) {
                                    draft = if (pet.id in draft) draft - pet.id else draft + pet.id
                                } else {
                                    onSelect(pet)
                                }
                            }
                        )
                    }
                }
            }
            if (multi) {
                LeoOutlinedButton(
                    text = "Confirmar",
                    onClick = { onConfirmSelection.invoke(draft); onDismiss() }
                )
                LeoOutlinedButton(
                    text = "Quitar selección",
                    onClick = { onConfirmSelection.invoke(emptySet()); onDismiss() }
                )
            } else {
                LeoOutlinedButton(text = "Quitar selección", onClick = { onSelect(null) })
            }
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
            Text("Ubicación", style = LeoCardTitle, color = BrandText)
            V2LocationPicker(
                selection = selection,
                onSelectionChange = onSelectionChange
            )
            selection.localityId?.let { id ->
                val label = nodes.displayOf(selection).label
                Text("Seleccionada: ${label.ifBlank { id }}", modifier = Modifier.padding(top = 8.dp))
            }
            LeoOutlinedButton(text = "Quitar", onClick = { onSelectionChange(LocationSelection()) })
            LeoOutlinedButton(text = "Listo", onClick = onDismiss)
        }
    }
}





