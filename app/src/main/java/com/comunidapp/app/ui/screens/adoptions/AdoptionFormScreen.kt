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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.viewmodel.AdoptionFormViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdoptionFormScreen(
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: AdoptionFormViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var petMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved, state.adoptionId) {
        if (state.saved && !state.adoptionId.isNullOrBlank()) {
            onSaved(state.adoptionId!!)
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.adoptionId == null) {
                ExposedDropdownMenuBox(
                    expanded = petMenuExpanded,
                    onExpandedChange = { petMenuExpanded = it }
                ) {
                    val selected = state.selectablePets.find { it.id == state.selectedPetId }
                    TextField(
                        value = selected?.name ?: "Seleccioná una mascota",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mascota") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = petMenuExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = petMenuExpanded,
                        onDismissRequest = { petMenuExpanded = false }
                    ) {
                        state.selectablePets.forEach { pet ->
                            DropdownMenuItem(
                                text = { Text(pet.name) },
                                onClick = {
                                    viewModel.onPetSelected(pet.id)
                                    petMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                if (state.selectablePets.isEmpty()) {
                    Text(
                        "No tenés mascotas activas para publicar.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Text(
                    text = "Mascota vinculada: ${state.selectedPetId ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.editable && !state.saving,
                singleLine = true
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.editable && !state.saving,
                minLines = 3
            )
            OutlinedTextField(
                value = state.requirements,
                onValueChange = viewModel::onRequirementsChange,
                label = { Text("Requisitos de adopción") },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.editable && !state.saving,
                minLines = 2
            )
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::onLocationChange,
                label = { Text("Ubicación") },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.editable && !state.saving,
                singleLine = true
            )

            state.fieldError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (state.editable) {
                OutlinedButton(
                    onClick = viewModel::saveDraft,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving
                ) { Text(if (state.adoptionId == null) "Guardar borrador" else "Guardar cambios") }
                Button(
                    onClick = viewModel::publish,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving
                ) { Text("Publicar") }
            }
        }
    }
}
