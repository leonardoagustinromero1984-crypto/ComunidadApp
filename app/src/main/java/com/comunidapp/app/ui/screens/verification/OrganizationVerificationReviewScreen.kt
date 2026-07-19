package com.comunidapp.app.ui.screens.verification

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
import com.comunidapp.app.domain.verification.OrganizationVerificationDecision
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.verification.OrganizationVerificationReviewViewModel

@Composable
fun OrganizationVerificationReviewScreen(
    reviewId: String,
    onNavigateBack: () -> Unit,
    viewModel: OrganizationVerificationReviewViewModel = viewModel(
        factory = OrganizationVerificationReviewViewModel.factory(reviewId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    AdministrativePhaseHost(
        title = "Revisar verificación",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        errorMessage = uiState.errorMessage ?: "No pudimos cargar la revisión.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        val review = uiState.review
        Column(
            modifier = contentModifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SnackbarHost(snackbar)
            Text("Los documentos físicos se gestionan en M05. Acá solo referencias lógicas.")
            if (review == null) {
                Text("Revisión no encontrada.")
            } else {
                Text("Org: ${review.organizationId}")
                Text("Estado: ${review.status}")
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota de revisión") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { viewModel.assignToMe() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Asignarme")
                }
                Button(
                    onClick = { viewModel.decide(OrganizationVerificationDecision.APPROVE, note) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Aprobar") }
                Button(
                    onClick = { viewModel.decide(OrganizationVerificationDecision.REJECT, note) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Rechazar") }
                Button(
                    onClick = {
                        viewModel.decide(OrganizationVerificationDecision.REQUEST_MORE_INFORMATION, note)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Pedir más información") }
                if (uiState.canRevoke) {
                    Button(
                        onClick = { viewModel.decide(OrganizationVerificationDecision.REVOKE, note) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Revocar") }
                }
            }
        }
    }
}
