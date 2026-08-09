package com.comunidapp.app.ui.screens.m28

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
import com.comunidapp.app.ui.components.ComunidappTopBar
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
        topBar = { ComunidappTopBar(title = "Acceso profesional", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "Autorizá qué puede ver o registrar una veterinaria. Podés revocar en cualquier momento.",
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
                    EmptyState(title = "Sin autorizaciones activas.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.grants) { g ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Estado: ${g.status}")
                                    Text("Clínica: ${g.clinicId ?: "—"}")
                                    Text("Finalidades: ${g.purposes.joinToString()}")
                                    if (g.status.name == "ACTIVE") {
                                        TextButton(onClick = { viewModel.revoke(g.id) }) {
                                            Text("Revocar")
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
        topBar = { ComunidappTopBar(title = "Propuestas Pasaporte", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "Un profesional propone datos para tu Pasaporte. Vos decidís; LeoVer no actualiza el Pasaporte automáticamente.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota opcional") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            when (val s = state) {
                M28ProposalsUiState.Loading -> LoadingState()
                is M28ProposalsUiState.Error -> ErrorState(message = s.message)
                is M28ProposalsUiState.Content -> if (s.proposals.isEmpty()) {
                    EmptyState(title = "No hay propuestas pendientes.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.proposals) { p ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${p.proposalType} · ${p.status}", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    Text("Clínica: ${p.clinicId}")
                                    Text("Propuesto: ${p.proposedValueJson.take(120)}")
                                    if (p.status == M28ProposalStatus.PENDING) {
                                        Button(onClick = { viewModel.decide(p.id, M28ProposalDecision.ACCEPT, note) }) {
                                            Text("Aceptar")
                                        }
                                        TextButton(onClick = { viewModel.decide(p.id, M28ProposalDecision.REJECT, note) }) {
                                            Text("Rechazar")
                                        }
                                        TextButton(onClick = {
                                            viewModel.decide(p.id, M28ProposalDecision.CORRECTION_REQUESTED, note)
                                        }) { Text("Solicitar corrección") }
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
        topBar = { ComunidappTopBar(title = "Registrar atención", showBackButton = true, onBackClick = onNavigateBack) }
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
