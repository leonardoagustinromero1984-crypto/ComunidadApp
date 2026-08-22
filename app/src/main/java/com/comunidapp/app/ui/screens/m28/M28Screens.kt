package com.comunidapp.app.ui.screens.m28

import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M28ProposalDecision
import com.comunidapp.app.data.model.M28ProposalStatus
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
        topBar = { LeoTopAppBar(title = "Acceso profesional", showBackButton = true, onBackClick = onNavigateBack) }
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
                is M28GrantsUiState.Content -> if (s.grants.isEmpty()) {
                    EmptyState(
                        title = "Sin accesos activos",
                        message = "Todavía no autorizaste a ningún profesional o entidad."
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.grants) { g ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        g.clinicName ?: "Profesional o entidad autorizada",
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                    )
                                    Text("Permisos: ${humanGrantPurposes(g.purposes)}")
                                    Text("Estado: ${grantStatusLabel(g.status)}")
                                    if (g.status.name == "ACTIVE") {
                                        TextButton(onClick = { viewModel.revoke(g.id) }) {
                                            Text("Revocar acceso")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M28PassportProposalsScreen(
    petId: String,
    onNavigateBack: () -> Unit,
    viewModel: M28PassportProposalsViewModel = viewModel(factory = M28PassportProposalsViewModel.factory(petId))
) {
    val state by viewModel.uiState.collectAsState()
    var note by remember { mutableStateOf("") }
    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Propuestas VitaCora", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "Un profesional o persona autorizada puede proponer cambios. Vos revisás y decidís qué se aplica en la VitaCora.",
                style = MaterialTheme.typography.bodyMedium
            )
            when (val s = state) {
                M28ProposalsUiState.Loading -> LoadingState()
                is M28ProposalsUiState.Error -> ErrorState(message = s.message)
                is M28ProposalsUiState.Content -> if (s.proposals.isEmpty()) {
                    EmptyState(
                        title = "Sin propuestas",
                        message = "Cuando alguien proponga un cambio, lo vas a ver acá."
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.proposals) { p ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        p.professionalName ?: "Propuesta recibida",
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                    )
                                    Text("Cambio: ${proposalTypeLabel(p.proposalType)}")
                                    Text("Estado: ${proposalStatusLabel(p.status)}")
                                    Text("Detalle: ${p.proposedValueJson.take(160)}")
                                    if (p.status == M28ProposalStatus.PENDING) {
                                        OutlinedTextField(
                                            value = note,
                                            onValueChange = { note = it },
                                            label = { Text("Nota para el profesional (opcional)") },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                        )
                                        Button(onClick = { viewModel.decide(p.id, M28ProposalDecision.ACCEPT, note) }) {
                                            Text("Aceptar")
                                        }
                                        TextButton(onClick = { viewModel.decide(p.id, M28ProposalDecision.REJECT, note) }) {
                                            Text("Rechazar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun humanGrantPurposes(purposes: List<com.comunidapp.app.data.model.M28GrantPurpose>): String =
    purposes.joinToString { purpose ->
        when (purpose) {
            com.comunidapp.app.data.model.M28GrantPurpose.HISTORICAL_READ -> "Ver historial"
            com.comunidapp.app.data.model.M28GrantPurpose.CURRENT_CARE -> "Agregar eventos"
            com.comunidapp.app.data.model.M28GrantPurpose.PASSPORT_PROPOSAL -> "Proponer cambios"
            com.comunidapp.app.data.model.M28GrantPurpose.DOCUMENTS -> "Ver documentos"
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
        topBar = { LeoTopAppBar(title = "Registrar atención", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Registro operativo LeoVer — no constituye historia clínica oficial.")
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Peso (kg)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { viewModel.createAndFinalize(reason, weight.toDoubleOrNull()) }) {
                Text("Finalizar atención")
            }
            message?.let { Text(it) }
        }
    }
}
