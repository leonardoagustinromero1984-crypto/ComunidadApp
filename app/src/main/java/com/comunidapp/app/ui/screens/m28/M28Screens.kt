package com.comunidapp.app.ui.screens.m28

import com.comunidapp.app.ui.theme.BrandBackground

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M28GrantPurpose
import com.comunidapp.app.data.model.M28ProposalDecision
import com.comunidapp.app.data.model.M28ProposalStatus
import com.comunidapp.app.data.model.VitacoraAccessTarget
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M28GrantsUiState
import com.comunidapp.app.viewmodel.M28PassportProposalsViewModel
import com.comunidapp.app.viewmodel.M28PetGrantsViewModel
import com.comunidapp.app.viewmodel.M28ProposalsUiState

@Composable
fun M28PetGrantsScreen(
    petId: String,
    clinicIdForGrant: String?,
    onNavigateBack: () -> Unit,
    viewModel: M28PetGrantsViewModel = viewModel(factory = M28PetGrantsViewModel.factory(petId))
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Gestionar accesos", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "Administrá qué profesionales o entidades pueden consultar o colaborar con la VitaCora de tu mascota.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (clinicIdForGrant != null) {
                Button(
                    onClick = { viewModel.grantClinicAccess(clinicIdForGrant) },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) { Text("Autorizar clínica vinculada") }
            }
            when (val s = state) {
                M28GrantsUiState.Loading -> LoadingState()
                is M28GrantsUiState.Error -> ErrorState(message = s.message)
                is M28GrantsUiState.Content -> {
                    OutlinedTextField(
                        value = s.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        label = { Text("Buscar veterinario, profesional o veterinaria") },
                        singleLine = true
                    )
                    if (s.searchInProgress) {
                        Text("Buscando…", style = MaterialTheme.typography.bodySmall)
                    }
                    if (s.searchResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(s.searchResults) { target ->
                                AccessTargetRow(target = target, onClick = { viewModel.selectTarget(target) })
                            }
                        }
                    }
                    s.actionMessage?.let { msg ->
                        Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Text(
                        "Accesos actuales",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    )
                    if (s.grants.isEmpty()) {
                        EmptyState(
                            title = "Sin accesos activos",
                            message = "Todavía no autorizaste a ningún profesional o entidad."
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(s.grants) { g ->
                                Column(Modifier.fillMaxWidth()) {
                                    LeoListRow(
                                        title = g.clinicName ?: "Profesional o entidad autorizada",
                                        subtitle = "Permisos: ${humanGrantPurposes(g.purposes)} · Estado: ${grantStatusLabel(g.status)}",
                                        showDivider = g.status.name != "ACTIVE"
                                    )
                                    if (g.status.name == "ACTIVE") {
                                        LeoOutlinedButton(text = "Quitar acceso", onClick = { viewModel.revoke(g.id) })
                                    }
                                }
                            }
                        }
                    }
                    s.selectedTarget?.let { target ->
                        GrantPermissionsDialog(
                            target = target,
                            onDismiss = { viewModel.selectTarget(null) },
                            onConfirm = { purposes -> viewModel.grantSelectedTarget(purposes) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessTargetRow(target: VitacoraAccessTarget, onClick: () -> Unit) {
    LeoListRow(
        title = target.displayName,
        subtitle = target.subtitle + if (target.verified) " · Verificado" else "",
        onClick = onClick
    )
}

@Composable
private fun GrantPermissionsDialog(
    target: VitacoraAccessTarget,
    onDismiss: () -> Unit,
    onConfirm: (List<M28GrantPurpose>) -> Unit
) {
    var viewVitacora by remember { mutableStateOf(true) }
    var viewHealth by remember { mutableStateOf(false) }
    var proposeChanges by remember { mutableStateOf(false) }
    var registerInfo by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dar acceso") },
        text = {
            Column {
                Text(target.displayName, fontWeight = FontWeight.SemiBold)
                Text(target.subtitle, style = MaterialTheme.typography.bodySmall)
                GrantPermissionRow("Ver VitaCora", viewVitacora) { viewVitacora = it }
                GrantPermissionRow("Ver información de salud", viewHealth) { viewHealth = it }
                GrantPermissionRow("Proponer cambios", proposeChanges) { proposeChanges = it }
                GrantPermissionRow("Registrar información", registerInfo) { registerInfo = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val purposes = buildList {
                    if (viewVitacora) add(M28GrantPurpose.HISTORICAL_READ)
                    if (viewHealth) add(M28GrantPurpose.DOCUMENTS)
                    if (proposeChanges) add(M28GrantPurpose.PASSPORT_PROPOSAL)
                    if (registerInfo) add(M28GrantPurpose.CURRENT_CARE)
                }
                onConfirm(purposes)
            }) { Text("Dar acceso") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun GrantPermissionRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label)
    }
}

@Composable
fun M28PassportProposalsScreen(
    petId: String,
    onNavigateBack: () -> Unit,
    viewModel: M28PassportProposalsViewModel = viewModel(factory = M28PassportProposalsViewModel.factory(petId))
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Propuestas VitaCora", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            when (val s = state) {
                M28ProposalsUiState.Loading -> LoadingState()
                is M28ProposalsUiState.Error -> ErrorState(message = s.message)
                is M28ProposalsUiState.Content -> if (s.proposals.isEmpty()) {
                    EmptyState(title = "Sin propuestas", message = "No hay propuestas pendientes.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.proposals) { p ->
                            Column(Modifier.fillMaxWidth()) {
                                LeoListRow(
                                    title = proposalTypeLabel(p.proposalType),
                                    subtitle = "Estado: ${proposalStatusLabel(p.status)}",
                                    showDivider = p.status != M28ProposalStatus.PENDING
                                )
                                if (p.status == M28ProposalStatus.PENDING) {
                                    LeoPrimaryButton(
                                        text = "Aceptar",
                                        onClick = { viewModel.decide(p.id, M28ProposalDecision.ACCEPT, null) }
                                    )
                                    LeoOutlinedButton(
                                        text = "Rechazar",
                                        onClick = { viewModel.decide(p.id, M28ProposalDecision.REJECT, null) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun humanGrantPurposes(purposes: List<M28GrantPurpose>): String =
    purposes.joinToString { purpose ->
        when (purpose) {
            M28GrantPurpose.HISTORICAL_READ -> "Ver VitaCora"
            M28GrantPurpose.CURRENT_CARE -> "Registrar información"
            M28GrantPurpose.PASSPORT_PROPOSAL -> "Proponer cambios"
            M28GrantPurpose.DOCUMENTS -> "Ver información de salud"
        }
    }

private fun grantStatusLabel(status: com.comunidapp.app.data.model.M28GrantStatus): String = when (status) {
    com.comunidapp.app.data.model.M28GrantStatus.ACTIVE -> "Activo"
    com.comunidapp.app.data.model.M28GrantStatus.REVOKED -> "Revocado"
    com.comunidapp.app.data.model.M28GrantStatus.EXPIRED -> "Expirado"
}

private fun proposalTypeLabel(type: com.comunidapp.app.data.model.M28ProposalType): String = when (type) {
    com.comunidapp.app.data.model.M28ProposalType.VACCINATION -> "Vacuna"
    com.comunidapp.app.data.model.M28ProposalType.WEIGHT -> "Peso"
    com.comunidapp.app.data.model.M28ProposalType.CONTROL_EVENT -> "Control"
    com.comunidapp.app.data.model.M28ProposalType.HEALTH_DOCUMENT -> "Salud"
    com.comunidapp.app.data.model.M28ProposalType.OTHER -> "Otro cambio"
}

private fun proposalStatusLabel(status: M28ProposalStatus): String = when (status) {
    M28ProposalStatus.PENDING -> "Pendiente"
    M28ProposalStatus.ACCEPTED -> "Aceptada"
    M28ProposalStatus.REJECTED -> "Rechazada"
    M28ProposalStatus.CANCELLED -> "Cancelada"
    M28ProposalStatus.SUPERSEDED -> "Reemplazada"
}

@Composable
fun M28ClinicCareScreen(
    clinicId: String,
    petId: String,
    appointmentId: String?,
    onNavigateBack: () -> Unit,
    viewModel: com.comunidapp.app.viewmodel.M28ClinicCareViewModel = viewModel(
        factory = com.comunidapp.app.viewmodel.M28ClinicCareViewModel.factory(clinicId, petId, appointmentId)
    )
) {
    val message by viewModel.message.collectAsState()
    var reason by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Atención clínica", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("La identidad de la mascota no se edita desde acá.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Peso (kg)") }, modifier = Modifier.fillMaxWidth())
            var notes by remember { mutableStateOf("") }
            var treatment by remember { mutableStateOf("") }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Observaciones / nota") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = treatment, onValueChange = { treatment = it }, label = { Text("Tratamiento / indicaciones") }, modifier = Modifier.fillMaxWidth())
            Text("Los adjuntos quedan en el historial privado de esta veterinaria hasta que el dueño acepte una propuesta.", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { viewModel.createAndFinalize(reason, weight.toDoubleOrNull()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Finalizar atención") }
            LeoOutlinedButton(
                text = "Proponer a VitaCora",
                onClick = { viewModel.proposeToVitacora(reason, notes) }
            )
            message?.let { Text(it) }
        }
    }
}
