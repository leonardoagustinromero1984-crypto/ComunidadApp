package com.comunidapp.app.ui.screens.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.filtered, key = { it.id }) { report ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReportClick(report.id) }
                ) {
                    Text(
                        text = report.reasonCode,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    )
                    Text(
                        text = "${report.status} · ${report.priority} · ${report.target.type}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(
                        text = if (report.caseId != null) "Con caso" else "Sin caso",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    SensitiveDataPresentation.reporterIdOrNull(report, uiState.canViewSensitive)
                        ?.let { rid ->
                            Text(
                                text = "Reporter: $rid",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}
