package com.comunidapp.app.ui.screens.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.comunidapp.app.domain.moderation.ModerationActionType
import com.comunidapp.app.domain.moderation.ModerationCaseStatus
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import com.comunidapp.app.domain.moderation.ModerationTargetType
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.moderation.ModerationCaseDetailViewModel

@Composable
fun ModerationCaseDetailScreen(
    caseId: String,
    onNavigateBack: () -> Unit,
    viewModel: ModerationCaseDetailViewModel = viewModel(
        factory = ModerationCaseDetailViewModel.factory(caseId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var note by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf("") }
    var showApplyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    if (showApplyDialog || uiState.confirmApplyAction) {
        AlertDialog(
            onDismissRequest = {
                showApplyDialog = false
                viewModel.requestApplyActionConfirmation(false)
            },
            title = { Text("Confirmar medida") },
            text = { Text("Vas a aplicar una medida de moderación en LeoVer. Esta acción se registra en auditoría.") },
            confirmButton = {
                TextButton(onClick = {
                    showApplyDialog = false
                    viewModel.applyAction(
                        target = ModerationTargetRef(ModerationTargetType.USER_PROFILE, targetId),
                        actionType = ModerationActionType.WARNING,
                        reasonCode = "policy_violation",
                        reasonDetail = null,
                        expiresAtEpochMs = null,
                        confirmed = true
                    )
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showApplyDialog = false
                    viewModel.requestApplyActionConfirmation(false)
                }) { Text("Cancelar") }
            }
        )
    }

    AdministrativePhaseHost(
        title = "Caso",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        errorMessage = uiState.errorMessage ?: "No pudimos cargar el caso.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        val detail = uiState.detail ?: return@AdministrativePhaseHost
        Column(
            modifier = contentModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SnackbarHost(snackbar)
            Text(detail.case.title, style = MaterialTheme.typography.titleLarge)
            Text("Estado: ${detail.case.status}")
            Text("Asignado: ${detail.case.assignedToUserId ?: "-"}")
            Text("Reportes: ${detail.reports.size}")
            Text("Medidas: ${detail.actions.size}")
            if (uiState.canViewSensitive) {
                Text("Notas internas: ${detail.notes.size}")
                detail.notes.forEach { n ->
                    Text("• ${n.body.take(120)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (uiState.canManageCases) {
                Button(onClick = { viewModel.assignToMe() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Asignarme")
                }
                Button(
                    onClick = { viewModel.changeStatus(ModerationCaseStatus.IN_REVIEW) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Marcar en revisión") }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota interna") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.addNote(note); note = "" },
                    enabled = note.isNotBlank() && uiState.canViewSensitive,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Agregar nota") }
            }
            if (uiState.canApplyActions) {
                OutlinedTextField(
                    value = targetId,
                    onValueChange = { targetId = it },
                    label = { Text("Target user id") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { showApplyDialog = true },
                    enabled = targetId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Aplicar advertencia") }
            }
        }
    }
}
