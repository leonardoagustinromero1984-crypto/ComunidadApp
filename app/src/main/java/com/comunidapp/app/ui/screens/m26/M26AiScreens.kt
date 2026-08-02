package com.comunidapp.app.ui.screens.m26

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M26PublicDuplicateCandidate
import com.comunidapp.app.data.model.M26PublicRecommendation
import com.comunidapp.app.data.model.M26PublicVisualMatch
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M26AssistanceUiState
import com.comunidapp.app.viewmodel.M26AssistanceViewModel
import com.comunidapp.app.viewmodel.M26DuplicatesUiState
import com.comunidapp.app.viewmodel.M26DuplicatesViewModel
import com.comunidapp.app.viewmodel.M26HubUiState
import com.comunidapp.app.viewmodel.M26HubViewModel
import com.comunidapp.app.viewmodel.M26RecommendationsUiState
import com.comunidapp.app.viewmodel.M26RecommendationsViewModel
import com.comunidapp.app.viewmodel.M26VisualMatchingUiState
import com.comunidapp.app.viewmodel.M26VisualMatchingViewModel

@Composable
fun M26HubScreen(
    onNavigateBack: () -> Unit,
    onOpenVisualMatching: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenAssistance: () -> Unit,
    onOpenRecommendations: () -> Unit,
    viewModel: M26HubViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Inteligencia asistida", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                M26HubUiState.Loading -> LoadingState()
                M26HubUiState.Empty -> EmptyState(title = "Sin sugerencias", message = "Todavía no hay resultados de inteligencia asistida.")
                is M26HubUiState.Error -> ErrorState(message = s.message)
                is M26HubUiState.Content -> {
                    Text("LeoVer M26 · Matching, duplicados, asistencia y recomendaciones evaluadas.", color = MaterialTheme.colorScheme.primary)
                    Text("${s.matchCount} matches · ${s.duplicateCount} duplicados · ${s.recommendationCount} recomendaciones aptas")
                    Button(onClick = onOpenVisualMatching, modifier = Modifier.fillMaxWidth()) { Text("Matching visual") }
                    OutlinedButton(onClick = onOpenDuplicates, modifier = Modifier.fillMaxWidth()) { Text("Detección de duplicados") }
                    OutlinedButton(onClick = onOpenAssistance, modifier = Modifier.fillMaxWidth()) { Text("Asistencia (stub)") }
                    OutlinedButton(onClick = onOpenRecommendations, modifier = Modifier.fillMaxWidth()) { Text("Recomendaciones evaluadas") }
                }
            }
        }
    }
}

@Composable
fun M26VisualMatchingScreen(onNavigateBack: () -> Unit, viewModel: M26VisualMatchingViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Matching visual", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M26VisualMatchingUiState.Loading -> LoadingState()
                M26VisualMatchingUiState.Empty -> EmptyState(title = "Sin matches", message = "No hay sugerencias de matching visual.")
                is M26VisualMatchingUiState.Error -> ErrorState(message = s.message)
                is M26VisualMatchingUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.items, key = { "${it.sourceLabel}-${it.targetLabel}" }) { M26VisualMatchCard(it) }
                }
            }
        }
    }
}

@Composable
fun M26DuplicatesScreen(onNavigateBack: () -> Unit, viewModel: M26DuplicatesViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Duplicados", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M26DuplicatesUiState.Loading -> LoadingState()
                M26DuplicatesUiState.Empty -> EmptyState(title = "Sin candidatos", message = "No hay duplicados pendientes.")
                is M26DuplicatesUiState.Error -> ErrorState(message = s.message)
                is M26DuplicatesUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.items, key = { "${it.primaryLabel}-${it.duplicateLabel}" }) { M26DuplicateCard(it) }
                }
            }
        }
    }
}

@Composable
fun M26AssistanceScreen(onNavigateBack: () -> Unit, viewModel: M26AssistanceViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Asistencia", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Stub de asistencia — no reemplaza moderación M04.", style = MaterialTheme.typography.bodyMedium)
            when (val s = state) {
                M26AssistanceUiState.Loading -> LoadingState()
                M26AssistanceUiState.Empty -> {
                    EmptyState(title = "Sin sesiones", message = "Iniciá una sesión de asistencia stub.")
                    Button(onClick = { viewModel.startStubSession() }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión stub") }
                }
                is M26AssistanceUiState.Error -> ErrorState(message = s.message)
                is M26AssistanceUiState.Content -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        items(s.sessions, key = { "${it.topic}-${it.summary}" }) { session ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${session.topic} · ${session.status}", fontWeight = FontWeight.Bold)
                                    Text(session.summary)
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { viewModel.startStubSession() }, modifier = Modifier.fillMaxWidth()) { Text("Nueva sesión stub") }
                }
            }
        }
    }
}

@Composable
fun M26RecommendationsScreen(onNavigateBack: () -> Unit, viewModel: M26RecommendationsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Recomendaciones", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Solo se muestran recomendaciones con revisión humana aprobada.", style = MaterialTheme.typography.bodyMedium)
            when (val s = state) {
                M26RecommendationsUiState.Loading -> LoadingState()
                M26RecommendationsUiState.Empty -> {
                    EmptyState(title = "Sin recomendaciones aptas", message = "No hay recomendaciones evaluadas para mostrar.")
                    OutlinedButton(onClick = { viewModel.submitSample() }, modifier = Modifier.fillMaxWidth()) { Text("Enviar muestra a revisión") }
                }
                is M26RecommendationsUiState.Error -> ErrorState(message = s.message)
                is M26RecommendationsUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.items, key = { it.title }) { M26RecommendationCard(it) }
                }
            }
        }
    }
}

@Composable
private fun M26VisualMatchCard(item: M26PublicVisualMatch) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${item.sourceLabel} ↔ ${item.targetLabel}", fontWeight = FontWeight.Bold)
            Text("Score ${"%.0f".format(item.score * 100)}% · ${item.confidenceBand} · ${item.status}")
        }
    }
}

@Composable
private fun M26DuplicateCard(item: M26PublicDuplicateCandidate) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${item.primaryLabel} / ${item.duplicateLabel}", fontWeight = FontWeight.Bold)
            Text("Similitud ${"%.0f".format(item.similarityScore * 100)}% · ${item.status}")
        }
    }
}

@Composable
private fun M26RecommendationCard(item: M26PublicRecommendation) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, fontWeight = FontWeight.Bold)
            Text("${item.kind} · revisada=${item.humanReviewed} · apta=${item.approvedForDisplay}")
            Text(item.rationale)
        }
    }
}
