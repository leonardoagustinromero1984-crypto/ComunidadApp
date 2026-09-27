package com.comunidapp.app.ui.screens.moderation

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
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.moderation.ModerationCaseQueueViewModel

@Composable
fun ModerationCaseQueueScreen(
    onNavigateBack: () -> Unit,
    onCaseClick: (String) -> Unit = {},
    viewModel: ModerationCaseQueueViewModel = viewModel(factory = ModerationCaseQueueViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    AdministrativePhaseHost(
        title = "Casos de moderación",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin casos",
        emptyMessage = "No hay casos en LeoVer.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar los casos.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        LazyColumn(modifier = contentModifier.fillMaxSize()) {
            items(uiState.cases, key = { it.id }) { c ->
                LeoListRow(
                    title = c.title,
                    subtitle = "${c.status} · ${c.assignedToUserId ?: "sin asignar"}",
                    onClick = { onCaseClick(c.id) }
                )
            }
        }
    }
}
