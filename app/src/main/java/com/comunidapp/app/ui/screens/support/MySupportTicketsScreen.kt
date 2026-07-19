package com.comunidapp.app.ui.screens.support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(onClick = onCreateClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Nuevo ticket")
                }
            }
            items(uiState.tickets, key = { it.id }) { t ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTicketClick(t.id) }
                ) {
                    Text(
                        text = t.subject,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    )
                    Text(
                        text = "${t.status} · ${t.category}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.padding(bottom = 16.dp))
                }
            }
        }
    }
}
