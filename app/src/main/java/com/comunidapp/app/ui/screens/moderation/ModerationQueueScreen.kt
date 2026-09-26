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
import com.comunidapp.app.viewmodel.moderation.ModerationQueueViewModel
import com.comunidapp.app.viewmodel.moderation.SensitiveDataPresentation

@Composable
fun ModerationQueueScreen(
    onNavigateBack: () -> Unit,
    onReportClick: (String) -> Unit = {},
    viewModel: ModerationQueueViewModel = viewModel(factory = ModerationQueueViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) {
            onNavigateBack()
        }
    }

    AdministrativePhaseHost(
        title = "Moderación",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin reportes",
        emptyMessage = "No hay reportes abiertos en LeoVer.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar la cola.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            items(uiState.filtered, key = { it.id }) { report ->
                val reporter = SensitiveDataPresentation.reporterIdOrNull(report, uiState.canViewSensitive)
                val caseLabel = if (report.caseId != null) "Con caso" else "Sin caso"
                LeoListRow(
                    title = report.reasonCode,
                    subtitle = buildString {
                        append("${report.status} · ${report.priority} · ${report.target.type} · $caseLabel")
                        if (reporter != null) append(" · Reporter: $reporter")
                    },
                    onClick = { onReportClick(report.id) }
                )
            }
        }
    }
}
