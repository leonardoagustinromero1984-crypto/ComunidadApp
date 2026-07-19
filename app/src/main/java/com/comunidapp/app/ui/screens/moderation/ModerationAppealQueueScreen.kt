package com.comunidapp.app.ui.screens.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.appeals, key = { it.id }) { a ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppealClick(a.id) }
                ) {
                    Text(
                        text = a.status.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    )
                    Text(
                        text = a.statement.take(120),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.padding(bottom = 16.dp))
                }
            }
        }
    }
}
