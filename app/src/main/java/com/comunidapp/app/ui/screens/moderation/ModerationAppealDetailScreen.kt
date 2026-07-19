package com.comunidapp.app.ui.screens.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.comunidapp.app.domain.moderation.ModerationAppealStatus
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.moderation.ModerationAppealQueueViewModel

@Composable
fun ModerationAppealDetailScreen(
    appealId: String,
    onNavigateBack: () -> Unit,
    viewModel: ModerationAppealQueueViewModel = viewModel(factory = ModerationAppealQueueViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var reason by remember { mutableStateOf("") }
    val appeal = uiState.appeals.firstOrNull { it.id == appealId } ?: uiState.selected

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    AdministrativePhaseHost(
        title = "Revisar apelación",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        errorMessage = uiState.errorMessage ?: "No pudimos cargar la apelación.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        Column(
            modifier = contentModifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SnackbarHost(snackbar)
            if (appeal == null) {
                Text("Apelación no encontrada en la cola.")
            } else {
                Text("Estado: ${appeal.status}")
                Text(appeal.statement)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo de decisión") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.review(appealId, ModerationAppealStatus.UPHELD, reason)
                    },
                    enabled = reason.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Aceptar (upheld)") }
                Button(
                    onClick = {
                        viewModel.review(appealId, ModerationAppealStatus.REJECTED, reason)
                    },
                    enabled = reason.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Rechazar") }
            }
        }
    }
}
