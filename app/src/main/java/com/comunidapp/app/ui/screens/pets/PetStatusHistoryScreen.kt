package com.comunidapp.app.ui.screens.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.remote.supabase.m08.PetStatusHistoryM08Row
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.PetStatusHistoryViewModel

/**
 * LeoVer M08 Etapa 6 — historial de estados (previous/new/changed_at/reason/changed_by).
 */
@Composable
fun PetStatusHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: PetStatusHistoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Historial de estado",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(contentModifier = Modifier.padding(padding))
            state.loadErrorMessage != null -> ErrorState(
                message = state.loadErrorMessage.orEmpty(),
                contentModifier = Modifier.padding(padding),
                onRetry = viewModel::load
            )
            state.isEmpty -> EmptyState(
                title = "Sin cambios de estado",
                contentModifier = Modifier.padding(padding),
                message = "Todavía no hay historial para esta mascota."
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.entries, key = { it.id ?: "${it.newStatus}-${it.createdAt}" }) { entry ->
                    StatusHistoryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun StatusHistoryCard(entry: PetStatusHistoryM08Row) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = statusTransitionLabel(entry.previousStatus, entry.newStatus),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            entry.createdAt?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Fecha: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            entry.reasonCode?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Motivo: $it",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            entry.actorUserId?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Por: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun statusTransitionLabel(previous: String?, next: String): String {
    val from = previous?.let { petStatusLabel(it) } ?: "—"
    return "$from → ${petStatusLabel(next)}"
}

internal fun petStatusLabel(status: String): String = when (status.uppercase()) {
    "ACTIVE" -> "Activa"
    "ARCHIVED" -> "Archivada"
    "DECEASED" -> "Fallecida"
    else -> status
}
