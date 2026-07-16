package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
import com.comunidapp.app.viewmodel.moderation.AdministrativeAuditViewModel
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase

@Composable
fun AdministrativeAuditScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdministrativeAuditViewModel = viewModel(factory = AdministrativeAuditViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    AdministrativePhaseHost(
        title = "Auditoría",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin eventos",
        emptyMessage = "No hay eventos de auditoría visibles en LeoVer.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar la auditoría.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.actorFilter,
                    onValueChange = viewModel::setActorFilter,
                    label = { Text("Actor") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.entityFilter,
                    onValueChange = viewModel::setEntityFilter,
                    label = { Text("Entidad") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.actionFilter,
                    onValueChange = viewModel::setActionFilter,
                    label = { Text("Acción") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(uiState.filtered, key = { it.id }) { e ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(e.action, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${e.resourceType} / ${e.resourceId}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("motivo: ${e.reasonCode}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
