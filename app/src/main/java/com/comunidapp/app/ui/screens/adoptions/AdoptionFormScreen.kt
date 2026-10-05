package com.comunidapp.app.ui.screens.adoptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.domain.adoption.AdoptionPublishEligibility
import com.comunidapp.app.domain.adoption.AdoptionRequirements
import com.comunidapp.app.viewmodel.AdoptionFormState
import com.comunidapp.app.viewmodel.AdoptionFormViewModel

/**
 * Formulario de adopción (M09).
 * Evita ExposedDropdownMenu (Material3 reciente puede fallar en runtime con menuAnchor).
 * Abre con estado vacío: sin mascota / sin org no debe crashear.
 */
@Composable
fun AdoptionFormScreen(
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    onNavigateToCreatePet: () -> Unit = {},
    viewModel: AdoptionFormViewModel = viewModel(
        factory = AdoptionFormViewModel.factory()
    )
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved, state.adoptionId) {
        if (state.saved && !state.adoptionId.isNullOrBlank()) {
            onSaved(state.adoptionId!!)
        }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = if (state.adoptionId == null) "Nueva adopción" else "Editar adopción",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        if (!state.errorMessage.isNullOrBlank() && state.adoptionId == null && state.selectablePets.isEmpty() &&
            state.title.isBlank()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No se pudo abrir el formulario",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                LeoPrimaryButton(text = "Reintentar", onClick = viewModel::load)
                TextButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Volver")
                }
            }
            return@Scaffold
        }

        AdoptionFormBody(
            state = state,
            padding = padding,
            onNavigateBack = onNavigateBack,
            onNavigateToCreatePet = onNavigateToCreatePet,
            onPetSelected = viewModel::onPetSelected,
            onTitleChange = viewModel::onTitleChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onRequirementsChange = viewModel::onRequirementsChange,
            onMatchRequirementsChange = viewModel::onMatchRequirementsChange,
            onLocationChange = viewModel::onLocationChange,
            onSaveDraft = viewModel::saveDraft,
            onPublish = viewModel::publish
        )
    }
}

@Composable
private fun AdoptionFormBody(
    state: AdoptionFormState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToCreatePet: () -> Unit,
    onPetSelected: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRequirementsChange: (String) -> Unit,
    onMatchRequirementsChange: (AdoptionRequirements) -> Unit,
    onLocationChange: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.adoptionId == null) {
            if (state.selectablePets.isEmpty()) {
                Text(
                    text = "Primero creá el perfil de la mascota",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = if (state.excludedFound) {
                        AdoptionPublishEligibility.FOUND_NOT_ELIGIBLE
                    } else {
                        "Necesitás un perfil de mascota para publicar la adopción."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                LeoPrimaryButton(text = "Crear mascota", onClick = onNavigateToCreatePet)
                TextButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar")
                }
                return@Column
            }

            Text(
                text = "Elegí la mascota que querés publicar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            state.selectablePets.forEach { pet ->
                LeoFilterChip(
                    label = pet.name,
                    selected = state.selectedPetId == pet.id,
                    onClick = { onPetSelected(pet.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TextButton(
                onClick = onNavigateToCreatePet,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Crear nueva mascota") }
            Text(
                AdoptionPublishEligibility.FOUND_NOT_ELIGIBLE,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                text = "Mascota vinculada: ${state.selectedPetId ?: "—"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text("Título") },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.editable && !state.saving,
            singleLine = true
        )
        OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = { Text("Descripción") },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.editable && !state.saving,
            minLines = 3
        )
        AdoptionRequirementFields(
            value = state.matchRequirements,
            species = state.selectablePets.firstOrNull { it.id == state.selectedPetId }?.species,
            onChange = onMatchRequirementsChange
        )
        OutlinedTextField(
            value = state.requirements,
            onValueChange = onRequirementsChange,
            label = { Text("Información adicional") },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.editable && !state.saving,
            minLines = 2
        )
        V2LocationStringPicker(
            value = state.location,
            onValueChange = onLocationChange,
            enabled = state.editable && !state.saving
        )

        state.fieldError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (state.editable) {
            LeoOutlinedButton(
                text = if (state.adoptionId == null) "Guardar borrador" else "Guardar cambios",
                onClick = onSaveDraft,
                enabled = !state.saving && (state.adoptionId != null || !state.selectedPetId.isNullOrBlank())
            )
            LeoPrimaryButton(
                text = "Publicar",
                onClick = onPublish,
                enabled = !state.saving && (state.adoptionId != null || !state.selectedPetId.isNullOrBlank())
            )
            TextButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar")
            }
        }
    }
}

@Preview(showBackground = true, name = "AdoptionFormNoPetPreview")
@Composable
private fun AdoptionFormNoPetPreview() {
    ComunidappTheme {
        AdoptionFormBody(
            state = AdoptionFormState(
                loading = false,
                selectablePets = emptyList(),
                selectedPetId = null
            ),
            padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            onNavigateBack = {},
            onNavigateToCreatePet = {},
            onPetSelected = {},
            onTitleChange = {},
            onDescriptionChange = {},
            onRequirementsChange = {},
            onMatchRequirementsChange = {},
            onLocationChange = {},
            onSaveDraft = {},
            onPublish = {}
        )
    }
}
