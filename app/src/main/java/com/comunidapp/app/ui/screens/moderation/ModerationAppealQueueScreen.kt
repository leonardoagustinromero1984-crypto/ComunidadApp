package com.comunidapp.app.ui.screens.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.moderation.ModerationAppealQueueViewModel

@Composable
fun ModerationAppealQueueScreen(
    onNavigateBack: () -> Unit,
    onAppealClick: (String) -> Unit = {},
    viewModel: ModerationAppealQueueViewModel = viewModel(factory = ModerationAppealQueueViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    AdministrativePhaseHost(
        title = "Apelaciones",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin apelaciones",
        emptyMessage = "No hay apelaciones pendientes en LeoVer.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar apelaciones.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            items(uiState.appeals, key = { it.id }) { a ->
                LeoListRow(
                    title = a.status.name,
                    subtitle = a.statement.take(120),
                    onClick = { onAppealClick(a.id) }
                )
            }
        }
    }
}
