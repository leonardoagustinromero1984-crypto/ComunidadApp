package com.comunidapp.app.ui.screens.verification

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.verification.OrganizationVerificationQueueViewModel

@Composable
fun OrganizationVerificationQueueScreen(
    onNavigateBack: () -> Unit,
    onReviewClick: (String) -> Unit = {},
    onShelterVerificationClick: (String) -> Unit = {},
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
        LazyColumn(modifier = contentModifier.fillMaxSize()) {
            items(uiState.reviews, key = { "org_${it.id}" }) { r ->
                LeoListRow(
                    title = "Organización · ${r.organizationId}",
                    subtitle = r.status.name,
                    onClick = { onReviewClick(r.id) }
                )
            }
            items(uiState.shelterRequests, key = { "m16_${it.id}" }) { req ->
                LeoListRow(
                    title = "Refugio · ${req.shelterDisplayName}",
                    subtitle = "${req.status.name} · org ${req.organizationId}",
                    onClick = { onShelterVerificationClick(req.id) }
                )
            }
        }
    }
}
