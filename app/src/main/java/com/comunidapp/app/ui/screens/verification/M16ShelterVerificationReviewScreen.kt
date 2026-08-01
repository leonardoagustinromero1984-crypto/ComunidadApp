package com.comunidapp.app.ui.screens.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M16ShelterVerificationDecision
import com.comunidapp.app.data.model.M16ShelterVerificationRequestStatus
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.verification.M16ShelterVerificationReviewViewModel

@Composable
fun M16ShelterVerificationReviewScreen(
    requestId: String,
    onNavigateBack: () -> Unit,
    viewModel: M16ShelterVerificationReviewViewModel = viewModel(
        factory = M16ShelterVerificationReviewViewModel.factory(requestId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    AdministrativePhaseHost(
        title = "Verificación refugio M16",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin solicitud",
        emptyMessage = "No encontramos la solicitud de verificación.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar la solicitud.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        val req = uiState.request ?: return@AdministrativePhaseHost
        Column(
            modifier = contentModifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(req.shelterDisplayName, fontWeight = FontWeight.Bold)
            Text("Estado solicitud: ${req.status.name}", style = MaterialTheme.typography.bodySmall)
            Text("Refugio: ${req.shelterProfileId}", style = MaterialTheme.typography.bodySmall)
            uiState.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            val pending = req.status == M16ShelterVerificationRequestStatus.PENDING ||
                req.status == M16ShelterVerificationRequestStatus.UNDER_REVIEW
            if (pending && uiState.canDecide) {
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text("Motivo / notas (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.decide(M16ShelterVerificationDecision.VERIFIED) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Aprobar verificación") }
                OutlinedButton(
                    onClick = { viewModel.decide(M16ShelterVerificationDecision.REJECTED) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Rechazar") }
            } else if (!pending) {
                Text("Decisión terminal registrada.", style = MaterialTheme.typography.bodySmall)
                req.decisionNotes?.let { Text("Notas: $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
