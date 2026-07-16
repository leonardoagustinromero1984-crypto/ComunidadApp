package com.comunidapp.app.ui.screens.organization

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.comunidapp.app.domain.organization.OrganizationType
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.viewmodel.CreateOrganizationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrganizationScreen(
    onNavigateBack: () -> Unit,
    onCreated: (organizationId: String) -> Unit,
    viewModel: CreateOrganizationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var typeExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.createdOrganizationId) {
        uiState.createdOrganizationId?.let { id ->
            viewModel.clearCreated()
            onCreated(id)
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Crear organización",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.publicName,
                onValueChange = viewModel::onPublicNameChange,
                label = { Text("Nombre público") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.legalName,
                onValueChange = viewModel::onLegalNameChange,
                label = { Text("Razón social (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.slug,
                onValueChange = viewModel::onSlugChange,
                label = { Text("Identificador público (slug)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving,
                supportingText = { Text("Solo minúsculas, números y guiones") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { if (!uiState.isSaving) typeExpanded = it }
            ) {
                OutlinedTextField(
                    value = typeLabel(uiState.type),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    enabled = !uiState.isSaving
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    OrganizationType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(typeLabel(type)) },
                            onClick = {
                                viewModel.onTypeChange(type)
                                typeExpanded = false
                            }
                        )
                    }
                }
            }
            if (uiState.type == OrganizationType.OTHER) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.typeDescription,
                    onValueChange = viewModel::onTypeDescriptionChange,
                    label = { Text("Descripción del tipo") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.city,
                onValueChange = viewModel::onCityChange,
                label = { Text("Ciudad (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.province,
                onValueChange = viewModel::onProvinceChange,
                label = { Text("Provincia (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.countryCode,
                onValueChange = viewModel::onCountryCodeChange,
                label = { Text("País ISO (opcional, ej. AR)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )
            uiState.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = viewModel::submit,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Crear")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun typeLabel(type: OrganizationType): String = when (type) {
    OrganizationType.SHELTER -> "Refugio"
    OrganizationType.RESCUE_GROUP -> "Grupo de rescate"
    OrganizationType.NGO -> "ONG"
    OrganizationType.VETERINARY_CLINIC -> "Clínica veterinaria"
    OrganizationType.PET_SHOP -> "Pet shop"
    OrganizationType.TRAINING_CENTER -> "Centro de adiestramiento"
    OrganizationType.WALKER_AGENCY -> "Agencia de paseadores"
    OrganizationType.OTHER -> "Otro"
}
