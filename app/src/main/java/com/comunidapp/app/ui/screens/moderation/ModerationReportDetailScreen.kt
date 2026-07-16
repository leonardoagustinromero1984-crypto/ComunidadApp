package com.comunidapp.app.ui.screens.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.comunidapp.app.domain.moderation.ModerationReportStatus
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.moderation.ModerationReportDetailViewModel

@Composable
fun ModerationReportDetailScreen(
    reportId: String,
    onNavigateBack: () -> Unit,
    viewModel: ModerationReportDetailViewModel = viewModel(
        factory = ModerationReportDetailViewModel.factory(reportId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var duplicateId by remember { mutableStateOf("") }
    var caseTitle by remember { mutableStateOf("") }
    var attachCaseId by remember { mutableStateOf("") }

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    AdministrativePhaseHost(
        title = "Detalle de reporte",
        phase = if (uiState.submitting) AdministrativeScreenPhase.Submitting else uiState.phase,
        onNavigateBack = onNavigateBack,
        errorMessage = uiState.errorMessage ?: "No pudimos cargar el reporte.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        val report = uiState.report ?: return@AdministrativePhaseHost
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SnackbarHost(snackbar)
            Text("Estado: ${report.status}", style = MaterialTheme.typography.titleMedium)
            Text("Prioridad: ${report.priority}")
            Text("Target: ${report.target.type} / ${report.target.targetId}")
            Text("Motivo: ${report.reasonCode}")
            report.description?.let { Text("Descripción: $it") }
            uiState.visibleReporterId?.let { Text("Reporter: $it") }
            Text(if (report.caseId != null) "Caso: ${report.caseId}" else "Sin caso")

            if (uiState.canManageReports) {
                Button(
                    onClick = { viewModel.triage(ModerationReportStatus.TRIAGED) },
                    enabled = !uiState.submitting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Triager") }
                Button(
                    onClick = { viewModel.triage(ModerationReportStatus.DISMISSED) },
                    enabled = !uiState.submitting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Desestimar") }
                OutlinedTextField(
                    value = duplicateId,
                    onValueChange = { duplicateId = it },
                    label = { Text("Id reporte original") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.markDuplicate(duplicateId) },
                    enabled = !uiState.submitting && duplicateId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Marcar duplicado") }
            }
            if (uiState.canManageCases) {
                OutlinedTextField(
                    value = caseTitle,
                    onValueChange = { caseTitle = it },
                    label = { Text("Título de caso nuevo") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.createCaseAndAttach(caseTitle) },
                    enabled = !uiState.submitting && caseTitle.length >= 3,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Crear caso y adjuntar") }
                OutlinedTextField(
                    value = attachCaseId,
                    onValueChange = { attachCaseId = it },
                    label = { Text("Id de caso existente") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.attachToCase(attachCaseId) },
                    enabled = !uiState.submitting && attachCaseId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Adjuntar a caso") }
            }
        }
    }
}
