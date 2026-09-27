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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.publish.LocalDebugDiagnostic
import com.comunidapp.app.ui.components.v2.V2FormErrorBanner
import com.comunidapp.app.R
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.HealthOptionDropdown
import com.comunidapp.app.ui.components.PetHealthFormSection
import com.comunidapp.app.ui.components.SpeciesDropdown
import com.comunidapp.app.ui.components.v2.V2FormImagePreview
import com.comunidapp.app.ui.components.v2.v2KeepVisibleOnFocus
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.media.LeoVerAvatarCropKind
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import com.comunidapp.app.ui.media.rememberLeoVerAvatarCropLauncher
import com.comunidapp.app.ui.media.rememberLeoVerPhotoSourcePicker
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
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
    focusSection: String = "profile",
    viewModel: PetFormViewModel
) {
    val title = if (focusSection.equals("health", ignoreCase = true)) "Editar salud" else "Editar mascota"
    PetFormScreen(
        title = title,
        onNavigateBack = onNavigateBack,
        onSaveSuccess = onSaveSuccess,
        onDeleteSuccess = onDeleteSuccess,
        focusSection = focusSection,
        viewModel = viewModel
    )
}

@Composable
private fun PetFormScreen(
    title: String,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    onDeleteSuccess: () -> Unit,
    focusSection: String = "profile",
    viewModel: PetFormViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val cropPhoto = rememberLeoVerAvatarCropLauncher(
        kind = LeoVerAvatarCropKind.PET,
        onCropped = { viewModel.onImageSelected(it) },
        onCancel = {},
        onError = viewModel::onPhotoCropFailed
    )
    val pickPhoto = rememberLeoVerPhotoSourcePicker(onSourceSelected = cropPhoto)
    val healthBringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(focusSection, uiState.isLoading) {
        if (!uiState.isLoading && focusSection.equals("health", ignoreCase = true)) {
            healthBringIntoView.bringIntoView()
        }
    }

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
            title = { Text(com.comunidapp.app.domain.pets.PetCareTransferCopy.ARCHIVE_TITLE) },
            text = { Text(com.comunidapp.app.domain.pets.PetCareTransferCopy.ARCHIVE_BODY) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deletePet()
                }) {
                    Text(com.comunidapp.app.domain.pets.PetCareTransferCopy.ARCHIVE_TITLE)
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = title, showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(padding))
            uiState.editDecision ==
                com.comunidapp.app.domain.pets.PetEditAuthorization.Decision.NO_PERMISSION ||
                uiState.editDecision ==
                com.comunidapp.app.domain.pets.PetEditAuthorization.Decision.PET_NOT_FOUND -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    Text(
                        text = uiState.errorMessage ?: "No tenés permiso para editar esta mascota",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                V2FormImagePreview(
                    imageUrl = uiState.pendingImageUri?.toString() ?: uiState.photoUrl,
                    contentDescription = uiState.name
                )
                Spacer(modifier = Modifier.height(12.dp))
                LeoOutlinedButton(
                    text = stringResource(R.string.change_photo),
                    onClick = pickPhoto,
                    enabled = !uiState.isSaving && !uiState.isDeleting &&
                        uiState.canManageMedia && !uiState.mutationsLocked
                )
                if (!uiState.canManageMedia) {
                    Text(
                        text = "No tenés permiso para cambiar la foto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(com.comunidapp.app.ui.components.leo.LeoRequiredField.label("Nombre")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .v2KeepVisibleOnFocus(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                SpeciesDropdown(
                    selectedCode = uiState.speciesCode,
                    onSelected = viewModel::onSpeciesChange,
                    enabled = !uiState.isSaving && !uiState.isDeleting,
                    options = uiState.speciesOptions
                )
                if (uiState.secondaryClassificationEnabled && uiState.breedOptions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HealthOptionDropdown(
                        label = uiState.secondaryLabelSingular.ifBlank { "Clasificación" },
                        options = uiState.breedOptions,
                        selected = uiState.breed,
                        onSelected = viewModel::onBreedChange,
                        enabled = !uiState.isSaving && !uiState.isDeleting
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                EnumChipRowSex(uiState.sex, viewModel::onSexChange)
                Spacer(modifier = Modifier.height(8.dp))
                EnumChipRowSize(uiState.size, viewModel::onSizeChange)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.ageYearsInput,
                        onValueChange = viewModel::onAgeYearsInput,
                        label = { Text("Años") },
                        modifier = Modifier
                            .weight(1f)
                            .v2KeepVisibleOnFocus(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = uiState.ageMonthsInput,
                        onValueChange = viewModel::onAgeMonthsInput,
                        label = { Text("Meses") },
                        modifier = Modifier
                            .weight(1f)
                            .v2KeepVisibleOnFocus(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Descripción") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .v2KeepVisibleOnFocus(),
                    minLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier.bringIntoViewRequester(healthBringIntoView)
                ) {
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
                    nextDeworming = uiState.nextDeworming,
                    fleaTreatmentProduct = uiState.fleaTreatmentProduct,
                    lastFleaTreatment = uiState.lastFleaTreatment,
                    nextFleaTreatment = uiState.nextFleaTreatment,
                    healthNotes = uiState.healthNotes,
                    allergyName = uiState.allergyName,
                    medicationName = uiState.medicationName,
                    conditionName = uiState.conditionName,
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
                    onNextDewormingChange = viewModel::onNextDewormingChange,
                    onFleaProductChange = viewModel::onFleaProductChange,
                    onLastFleaTreatmentChange = viewModel::onLastFleaTreatmentChange,
                    onNextFleaTreatmentChange = viewModel::onNextFleaTreatmentChange,
                    onAllergyNameChange = viewModel::onAllergyNameChange,
                    onMedicationNameChange = viewModel::onMedicationNameChange,
                    onConditionNameChange = viewModel::onConditionNameChange,
                    onHealthNotesChange = viewModel::onHealthNotesChange,
                    vaccineOptions = uiState.vaccineOptions,
                    dewormerOptions = uiState.dewormerOptions,
                    fleaOptions = uiState.fleaOptions
                )
                }

                uiState.duplicateWarning?.let { warning ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = warning,
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                uiState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    val clipboard = LocalClipboardManager.current
                    val diagnostic = uiState.debugDiagnostic
                    V2FormErrorBanner(
                        title = error,
                        onCopyDiagnostic = if (
                            LocalDebugDiagnostic.isCopyEnabled() && !diagnostic.isNullOrBlank()
                        ) {
                            { clipboard.setText(AnnotatedString(diagnostic)) }
                        } else {
                            null
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                LeoPrimaryButton(
                    text = if (uiState.isSaving) "Guardando…" else stringResource(R.string.save_profile),
                    onClick = viewModel::savePet,
                    enabled = !uiState.isSaving && !uiState.isDeleting && !uiState.mutationsLocked
                )
                Spacer(modifier = Modifier.height(8.dp))
                LeoOutlinedButton(
                    text = "Descartar",
                    onClick = {
                        viewModel.discardDraft()
                        onNavigateBack()
                    },
                    enabled = !uiState.isSaving && !uiState.isDeleting
                )

                if (uiState.isEditMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LeoOutlinedButton(
                        text = com.comunidapp.app.domain.pets.PetCareTransferCopy.ARCHIVE_TITLE,
                        onClick = { showDeleteDialog = true },
                        enabled = !uiState.isSaving && !uiState.isDeleting
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EnumChipRowSex(selected: PetSex, onSelect: (PetSex) -> Unit) {
    com.comunidapp.app.ui.components.leo.LeoEnumChipRow(
        items = PetSex.entries,
        selected = selected,
        onSelect = onSelect,
        labelOf = { it.toDisplayName() },
        label = "Sexo",
        required = false
    )
}

@Composable
private fun EnumChipRowSize(selected: PetSize, onSelect: (PetSize) -> Unit) {
    com.comunidapp.app.ui.components.leo.LeoEnumChipRow(
        items = PetSize.entries,
        selected = selected,
        onSelect = onSelect,
        labelOf = { it.toDisplayName() },
        label = "Tamaño",
        required = false
    )
}
