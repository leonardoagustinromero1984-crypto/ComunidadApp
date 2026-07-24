package com.comunidapp.app.ui.screens.veterinary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.VeterinaryClinicHoursViewModel
import com.comunidapp.app.viewmodel.VeterinaryClinicManageActionsViewModel
import com.comunidapp.app.viewmodel.VeterinaryClinicProfessionalsViewModel
import com.comunidapp.app.viewmodel.VeterinaryClinicServicesViewModel
import com.comunidapp.app.viewmodel.VeterinaryManageListUiState

@Composable
fun VeterinaryClinicProfessionalsScreen(
    clinicId: String,
    onNavigateBack: () -> Unit,
    viewModel: VeterinaryClinicProfessionalsViewModel = viewModel(
        factory = VeterinaryClinicProfessionalsViewModel.factory(clinicId)
    )
) {
    ManageListScaffold(
        title = "Profesionales",
        onNavigateBack = onNavigateBack,
        state = viewModel.uiState.collectAsState().value
    )
}

@Composable
fun VeterinaryClinicServicesScreen(
    clinicId: String,
    onNavigateBack: () -> Unit,
    viewModel: VeterinaryClinicServicesViewModel = viewModel(
        factory = VeterinaryClinicServicesViewModel.factory(clinicId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Servicios", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Sin precios ni turnos en este bloque.", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = viewModel::createQuickService,
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Guardando…" else "Agregar consulta") }
            Spacer(Modifier.height(8.dp))
            ManageListBody(state)
        }
    }
}

@Composable
fun VeterinaryClinicHoursScreen(
    clinicId: String,
    onNavigateBack: () -> Unit,
    viewModel: VeterinaryClinicHoursViewModel = viewModel(
        factory = VeterinaryClinicHoursViewModel.factory(clinicId)
    )
) {
    ManageListScaffold(
        title = "Horarios",
        onNavigateBack = onNavigateBack,
        state = viewModel.uiState.collectAsState().value
    )
}

@Composable
fun VeterinaryClinicManageHubScreen(
    clinicId: String,
    onNavigateBack: () -> Unit,
    onProfessionals: () -> Unit,
    onServices: () -> Unit,
    onHours: () -> Unit,
    viewModel: VeterinaryClinicManageActionsViewModel = viewModel(
        factory = VeterinaryClinicManageActionsViewModel.factory(clinicId)
    )
) {
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Gestionar veterinaria",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Persistencia remota vía RPC M12 (Bloque 2).")
            OutlinedButton(onClick = onProfessionals, modifier = Modifier.fillMaxWidth()) {
                Text("Profesionales")
            }
            OutlinedButton(onClick = onServices, modifier = Modifier.fillMaxWidth()) {
                Text("Servicios")
            }
            OutlinedButton(onClick = onHours, modifier = Modifier.fillMaxWidth()) {
                Text("Horarios")
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::requestVerification,
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Solicitar verificación") }
            OutlinedButton(
                onClick = { viewModel.changeStatus(VeterinaryClinicStatus.ACTIVE) },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Activar") }
            OutlinedButton(
                onClick = { viewModel.changeStatus(VeterinaryClinicStatus.PAUSED) },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Pausar") }
            OutlinedButton(
                onClick = { viewModel.changeStatus(VeterinaryClinicStatus.ARCHIVED) },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Archivar") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            message?.let { Text(it) }
        }
    }
}

@Composable
private fun ManageListScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    state: VeterinaryManageListUiState
) {
    Scaffold(
        topBar = {
            ComunidappTopBar(title = title, showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            ManageListBody(state)
        }
    }
}

@Composable
private fun ManageListBody(state: VeterinaryManageListUiState) {
    when (state) {
        VeterinaryManageListUiState.Loading, VeterinaryManageListUiState.Saving -> LoadingState()
        VeterinaryManageListUiState.Empty -> EmptyState(title = "Sin datos.")
        is VeterinaryManageListUiState.Error -> ErrorState(message = state.message)
        is VeterinaryManageListUiState.Content -> LazyColumn {
            items(state.lines) { line -> Text(line, modifier = Modifier.padding(vertical = 4.dp)) }
        }
    }
}
