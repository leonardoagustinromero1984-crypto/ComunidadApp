package com.comunidapp.app.ui.screens.pets

import com.comunidapp.app.ui.theme.BrandBackground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.remote.supabase.m08.PetStatusHistoryM08Row
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
    val subtitle = listOfNotNull(
        entry.createdAt?.takeIf { it.isNotBlank() }?.let { "Fecha: $it" },
        entry.reasonCode?.takeIf { it.isNotBlank() }?.let { "Motivo: ${petStatusReasonLabel(it)}" },
        entry.actorUserId?.takeIf { it.isNotBlank() }?.let { "Por: $it" }
    ).joinToString(" · ").ifBlank { null }
    LeoListRow(
        title = statusTransitionLabel(
            entry.previousStatus,
            entry.newStatus,
            entry.reasonCode
        ),
        subtitle = subtitle
    )
}

private fun statusTransitionLabel(
    previous: String?,
    next: String,
    reasonCode: String? = null
): String {
    val from = previous?.let { petStatusLabel(it) } ?: "—"
    return "$from → ${petStatusLabel(next, reasonCode)}"
}

/** M08+M09: ARCHIVED + reason ADOPTED se muestra como Adoptada (sin ciclo ADOPTED en M08). */
internal fun petStatusLabel(status: String, reasonCode: String? = null): String {
    if (status.equals("ARCHIVED", ignoreCase = true) &&
        reasonCode.equals("ADOPTED", ignoreCase = true)
    ) {
        return "Adoptada"
    }
    return when (status.uppercase()) {
        "ACTIVE" -> "Activa"
        "ARCHIVED" -> "Archivada"
        "DECEASED" -> "Fallecida"
        else -> status
    }
}

internal fun petStatusReasonLabel(reasonCode: String): String = when (reasonCode.uppercase()) {
    "ADOPTED" -> "Adopción finalizada"
    "RESTORED" -> "Restauración"
    "DECEASED" -> "Fallecimiento"
    "ARCHIVED" -> "Archivo"
    else -> reasonCode
}
