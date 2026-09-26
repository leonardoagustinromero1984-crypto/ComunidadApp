package com.comunidapp.app.ui.screens.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.support.MySupportTicketsViewModel

@Composable
fun MySupportTicketsScreen(
    onNavigateBack: () -> Unit,
    onTicketClick: (String) -> Unit = {},
    onCreateClick: () -> Unit = {},
    viewModel: MySupportTicketsViewModel = viewModel(factory = MySupportTicketsViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    VisualDirectionPilot {
    AdministrativePhaseHost(
        title = "Soporte",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin tickets",
        emptyMessage = "Todavía no abriste tickets en LeoVer.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar tus tickets.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = LeoDimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                LeoPrimaryButton(
                    text = "Nuevo ticket",
                    onClick = onCreateClick,
                    modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceS)
                )
            }
            items(uiState.tickets, key = { it.id }) { t ->
                LeoListRow(
                    title = t.subject,
                    subtitle = "${t.status} · ${t.category}",
                    onClick = { onTicketClick(t.id) }
                )
            }
        }
    }
    }
}
