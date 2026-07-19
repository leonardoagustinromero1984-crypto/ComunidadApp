package com.comunidapp.app.ui.screens.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.organization.OrganizationBranch
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.viewmodel.OrganizationBranchesViewModel

@Composable
fun OrganizationBranchesScreen(
    onNavigateBack: () -> Unit,
    viewModel: OrganizationBranchesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Sucursales",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.errorMessage?.let { msg ->
                    item { Text(text = msg, color = MaterialTheme.colorScheme.error) }
                }
                uiState.successMessage?.let { msg ->
                    item { Text(text = msg, color = MaterialTheme.colorScheme.primary) }
                }
                if (uiState.canManage) {
                    item {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Nueva sucursal", fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = uiState.name,
                                    onValueChange = viewModel::onNameChange,
                                    label = { Text("Nombre") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = uiState.addressLine,
                                    onValueChange = viewModel::onAddressChange,
                                    label = { Text("Dirección") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = uiState.city,
                                    onValueChange = viewModel::onCityChange,
                                    label = { Text("Ciudad") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = uiState.phone,
                                    onValueChange = viewModel::onPhoneChange,
                                    label = { Text("Teléfono (privado por defecto)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                RowWithSwitch(
                                    label = "Teléfono público",
                                    checked = uiState.phonePublic,
                                    onCheckedChange = viewModel::onPhonePublicChange
                                )
                                Button(
                                    onClick = viewModel::createBranch,
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (uiState.isSaving) "Guardando…" else "Crear sucursal")
                                }
                            }
                        }
                    }
                }
                if (uiState.branches.isEmpty()) {
                    item {
                        Text(
                            text = "No hay sucursales registradas.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(uiState.branches, key = { it.id }) { branch ->
                        BranchCard(
                            branch = branch,
                            canManage = uiState.canManage,
                            onClose = { viewModel.closeBranch(branch.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowWithSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BranchCard(
    branch: OrganizationBranch,
    canManage: Boolean,
    onClose: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = branch.name, fontWeight = FontWeight.SemiBold)
            branch.addressLine?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            branch.city?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            if (branch.phonePublic) {
                branch.phone?.let { Text(text = "Tel: $it") }
            }
            Text(
                text = branch.status.name,
                style = MaterialTheme.typography.labelMedium
            )
            if (canManage && branch.status.name != "CLOSED") {
                TextButton(onClick = onClose) { Text("Cerrar sucursal") }
            }
        }
    }
}
