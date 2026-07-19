package com.comunidapp.app.ui.screens.verification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.verification.OrganizationVerificationQueueViewModel

@Composable
fun OrganizationVerificationQueueScreen(
    onNavigateBack: () -> Unit,
    onReviewClick: (String) -> Unit = {},
    viewModel: OrganizationVerificationQueueViewModel = viewModel(
        factory = OrganizationVerificationQueueViewModel.factory()
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    AdministrativePhaseHost(
        title = "Verificación",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin solicitudes",
        emptyMessage = "No hay verificaciones pendientes en LeoVer. Los documentos físicos pertenecen a M05.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar la cola.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.reviews, key = { it.id }) { r ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReviewClick(r.id) }
                ) {
                    Text(
                        text = r.organizationId,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    )
                    Text(
                        text = r.status.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.padding(bottom = 16.dp))
                }
            }
        }
    }
}
