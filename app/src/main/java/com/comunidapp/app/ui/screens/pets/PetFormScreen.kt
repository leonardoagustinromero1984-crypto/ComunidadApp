package com.comunidapp.app.ui.screens.pets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comunidapp.app.R
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetHealthFormSection
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.SpeciesDropdown
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.PetFormViewModel

@Composable
fun AddPetScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: PetFormViewModel
) {
    PetFormScreen(
        title = "Agregar mascota",
        onNavigateBack = onNavigateBack,
        onSaveSuccess = onSaveSuccess,
        onDeleteSuccess = onNavigateBack,
        viewModel = viewModel
    )
}

@Composable
fun EditPetScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    onDeleteSuccess: () -> Unit,
    viewModel: PetFormViewModel
) {
    PetFormScreen(
        title = "Editar mascota",
        onNavigateBack = onNavigateBack,
        onSaveSuccess = onSaveSuccess,
        onDeleteSuccess = onDeleteSuccess,
        viewModel = viewModel
    )
}

@Composable
private fun PetFormScreen(
    title: String,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    onDeleteSuccess: () -> Unit,
    viewModel: PetFormViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.onImageSelected(uri) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.clearSaveSuccess()
            onSaveSuccess()
        }
    }
    LaunchedEffect(uiState.deleteSuccess) {
        if (uiState.deleteSuccess) {
            viewModel.clearDeleteSuccess()
            onDeleteSuccess()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar mascota") },
            text = { Text("¿Estás seguro? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deletePet()
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = title, showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                PetImage(
                    imageUrl = uiState.pendingImageUri?.toString() ?: uiState.photoUrl,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape),
                    cornerRadius = 56.dp,
                    contentDescription = uiState.name
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !uiState.isSaving && !uiState.isDeleting
                ) {
                    Text(stringResource(R.string.change_photo))
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                SpeciesDropdown(
                    selected = uiState.species,
                    onSelected = viewModel::onSpeciesChange,
                    enabled = !uiState.isSaving && !uiState.isDeleting
                )
                Spacer(modifier = Modifier.height(8.dp))
                EnumChipRowSex(uiState.sex, viewModel::onSexChange)
                Spacer(modifier = Modifier.height(8.dp))
                EnumChipRowSize(uiState.size, viewModel::onSizeChange)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.ageYears.toString(),
                        onValueChange = { viewModel.onAgeYearsChange(it.toIntOrNull() ?: 0) },
                        label = { Text("Años") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.ageMonths.toString(),
                        onValueChange = { viewModel.onAgeMonthsChange(it.toIntOrNull() ?: 0) },
                        label = { Text("Meses") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))
                PetHealthFormSection(
                    species = uiState.species,
                    sterilized = uiState.sterilized,
                    microchipId = uiState.microchipId,
                    lastVetVisit = uiState.lastVetVisit,
                    vaccinations = uiState.vaccinations,
                    pendingVaccineName = uiState.pendingVaccineName,
                    pendingVaccineDate = uiState.pendingVaccineDate,
                    pendingVaccineNextDate = uiState.pendingVaccineNextDate,
                    dewormingProduct = uiState.dewormingProduct,
                    lastDeworming = uiState.lastDeworming,
                    fleaTreatmentProduct = uiState.fleaTreatmentProduct,
                    lastFleaTreatment = uiState.lastFleaTreatment,
                    healthNotes = uiState.healthNotes,
                    enabled = !uiState.isSaving && !uiState.isDeleting,
                    onSterilizedChange = viewModel::onSterilizedChange,
                    onMicrochipChange = viewModel::onMicrochipChange,
                    onLastVetVisitChange = viewModel::onLastVetVisitChange,
                    onPendingVaccineNameChange = viewModel::onPendingVaccineNameChange,
                    onPendingVaccineDateChange = viewModel::onPendingVaccineDateChange,
                    onPendingVaccineNextDateChange = viewModel::onPendingVaccineNextDateChange,
                    onAddVaccination = viewModel::addPendingVaccination,
                    onRemoveVaccination = viewModel::removeVaccination,
                    onDewormingProductChange = viewModel::onDewormingProductChange,
                    onLastDewormingChange = viewModel::onLastDewormingChange,
                    onFleaProductChange = viewModel::onFleaProductChange,
                    onLastFleaTreatmentChange = viewModel::onLastFleaTreatmentChange,
                    onHealthNotesChange = viewModel::onHealthNotesChange
                )

                uiState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = viewModel::savePet,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving && !uiState.isDeleting
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.save_profile))
                    }
                }

                if (uiState.isEditMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving && !uiState.isDeleting
                    ) {
                        Text("Eliminar mascota", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EnumChipRowSex(selected: PetSex, onSelect: (PetSex) -> Unit) {
    Text(text = "Sexo", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PetSex.entries.forEach { entry ->
            FilterChip(
                selected = selected == entry,
                onClick = { onSelect(entry) },
                label = { Text(entry.toDisplayName()) }
            )
        }
    }
}

@Composable
private fun EnumChipRowSize(selected: PetSize, onSelect: (PetSize) -> Unit) {
    Text(text = "Tamaño", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PetSize.entries.forEach { entry ->
            FilterChip(
                selected = selected == entry,
                onClick = { onSelect(entry) },
                label = { Text(entry.toDisplayName()) }
            )
        }
    }
}
