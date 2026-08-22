package com.comunidapp.app.ui.screens.organization

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.comunidapp.app.domain.onboarding.onb02.ProductOrganizationCategory
import com.comunidapp.app.domain.organization.OrganizationCreatePolicy
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.viewmodel.CreateOrganizationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrganizationScreen(
    onNavigateBack: () -> Unit,
    onCreated: (organizationId: String) -> Unit,
    preselect: String? = null,
    welfare: Boolean = false,
    viewModel: CreateOrganizationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var typeExpanded by remember { mutableStateOf(false) }
    val categories = if (welfare) {
        ProductOrganizationCategory.welfareVisible
    } else {
        OrganizationCreatePolicy.COMMERCIAL_CATEGORIES
    }
    val typeLocked = !preselect.isNullOrBlank() && !welfare

    LaunchedEffect(preselect, welfare) {
        viewModel.applyEntry(preselect, welfare)
    }

    LaunchedEffect(uiState.createdOrganizationId) {
        uiState.createdOrganizationId?.let { id ->
            viewModel.clearCreated()
            onCreated(id)
        }
    }

    VisualDirectionPilot {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(
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
            if (!typeLocked) {
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { if (!uiState.isSaving) typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = uiState.category.visibleLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("¿Qué representás?") },
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
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.visibleLabel) },
                                onClick = {
                                    viewModel.onCategoryChange(category)
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = uiState.publicName,
                onValueChange = viewModel::onPublicNameChange,
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )
            uiState.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = msg, color = leoVisual().error)
            }
            Spacer(modifier = Modifier.height(24.dp))
            LeoPrimaryButton(
                text = if (uiState.isSaving) "Creando…" else "Crear",
                onClick = viewModel::submit,
                enabled = !uiState.isSaving
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    }
}
