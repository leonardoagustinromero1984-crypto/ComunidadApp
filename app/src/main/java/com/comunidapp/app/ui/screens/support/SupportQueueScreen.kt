package com.comunidapp.app.ui.screens.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.support.SupportTicketStatus
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.support.SupportAdminQueueViewModel

private enum class SupportQueueFilter {
    ALL, OPEN, IN_PROGRESS, CLOSED
}

@Composable
fun SupportQueueScreen(
    onNavigateBack: () -> Unit,
    onTicketClick: (String) -> Unit = {},
    viewModel: SupportAdminQueueViewModel = viewModel(factory = SupportAdminQueueViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf(SupportQueueFilter.ALL) }
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    AdministrativePhaseHost(
        title = "Soporte",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Cola vacía",
        emptyMessage = "No hay casos de soporte en LeoVer.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar la cola.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        val tickets = uiState.tickets.filter { ticket ->
            when (filter) {
                SupportQueueFilter.ALL -> true
                SupportQueueFilter.OPEN -> ticket.status == SupportTicketStatus.OPEN
                SupportQueueFilter.IN_PROGRESS ->
                    ticket.status == SupportTicketStatus.IN_PROGRESS ||
                        ticket.status == SupportTicketStatus.WAITING_USER ||
                        ticket.status == SupportTicketStatus.WAITING_INTERNAL
                SupportQueueFilter.CLOSED ->
                    ticket.status == SupportTicketStatus.RESOLVED ||
                        ticket.status == SupportTicketStatus.CLOSED
            }
        }
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceXs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
                    ) {
                        LeoFilterChip(
                            label = "Todos",
                            selected = filter == SupportQueueFilter.ALL,
                            onClick = { filter = SupportQueueFilter.ALL }
                        )
                        LeoFilterChip(
                            label = "Abiertos",
                            selected = filter == SupportQueueFilter.OPEN,
                            onClick = { filter = SupportQueueFilter.OPEN }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
                    ) {
                        LeoFilterChip(
                            label = "En curso",
                            selected = filter == SupportQueueFilter.IN_PROGRESS,
                            onClick = { filter = SupportQueueFilter.IN_PROGRESS }
                        )
                        LeoFilterChip(
                            label = "Cerrados",
                            selected = filter == SupportQueueFilter.CLOSED,
                            onClick = { filter = SupportQueueFilter.CLOSED }
                        )
                    }
                }
            }
            if (tickets.isEmpty()) {
                item {
                    Text(
                        "No hay casos en este filtro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(tickets, key = { it.id }) { t ->
                LeoListRow(
                    title = t.subject,
                    subtitle = "${supportStatusLabel(t.status)} · ${t.category.name}",
                    onClick = { onTicketClick(t.id) }
                )
            }
        }
    }
}

private fun supportStatusLabel(status: SupportTicketStatus): String = when (status) {
    SupportTicketStatus.OPEN -> "Abierto"
    SupportTicketStatus.IN_PROGRESS -> "En progreso"
    SupportTicketStatus.WAITING_USER -> "Esperando usuario"
    SupportTicketStatus.WAITING_INTERNAL -> "Esperando interno"
    SupportTicketStatus.RESOLVED -> "Resuelto"
    SupportTicketStatus.CLOSED -> "Cerrado"
}
