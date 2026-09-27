package com.comunidapp.app.ui.screens.m26

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
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
import com.comunidapp.app.viewmodel.M26HistoryUiState
import com.comunidapp.app.viewmodel.M26HistoryViewModel
import com.comunidapp.app.viewmodel.M26ReviewQueueUiState
import com.comunidapp.app.viewmodel.M26ReviewQueueViewModel
import com.comunidapp.app.viewmodel.M26VisualMatchingUiState
import com.comunidapp.app.viewmodel.M26VisualMatchingViewModel

@Composable
fun M26HubScreen(
    onNavigateBack: () -> Unit,
    onOpenVisualMatching: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenAssistance: () -> Unit,
    onOpenRecommendations: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReviewQueue: () -> Unit,
    viewModel: M26HubViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Inteligencia asistida", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                M26HubUiState.Loading -> LoadingState()
                M26HubUiState.Empty -> EmptyState(title = "Sin sugerencias", message = "Todavía no hay resultados de inteligencia asistida.")
                is M26HubUiState.Error -> ErrorState(message = s.message)
                is M26HubUiState.Content -> {
                    Text("Sugerencias estimativas; requieren revisión humana cuando corresponda.", color = MaterialTheme.colorScheme.primary)
                    Text("${s.matchCount} matches · ${s.duplicateCount} duplicados · ${s.recommendationCount} recomendaciones aptas · ${s.jobCount} ejecuciones")
                    LeoPrimaryButton(text = "Matching visual", onClick = onOpenVisualMatching)
                    LeoOutlinedButton(text = "Detección de duplicados", onClick = onOpenDuplicates)
                    LeoOutlinedButton(text = "Asistencia (stub)", onClick = onOpenAssistance)
                    LeoOutlinedButton(text = "Recomendaciones evaluadas", onClick = onOpenRecommendations)
                    LeoOutlinedButton(text = "Historial personal", onClick = onOpenHistory)
                    LeoOutlinedButton(text = "Cola de revisión", onClick = onOpenReviewQueue)
                }
            }
        }
    }
}

@Composable
fun M26VisualMatchingScreen(onNavigateBack: () -> Unit, viewModel: M26VisualMatchingViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Matching visual", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
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
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Duplicados", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
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
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Asistencia", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Asistencia orientativa — no reemplaza la moderación humana.", style = MaterialTheme.typography.bodyMedium)
            when (val s = state) {
                M26AssistanceUiState.Loading -> LoadingState()
                M26AssistanceUiState.Empty -> {
                    EmptyState(title = "Sin sesiones", message = "Iniciá una sesión de asistencia stub.")
                    LeoPrimaryButton(text = "Iniciar sesión stub", onClick = { viewModel.startStubSession() })
                }
                is M26AssistanceUiState.Error -> ErrorState(message = s.message)
                is M26AssistanceUiState.Content -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        items(s.sessions, key = { "${it.topic}-${it.summary}" }) { session ->
                            LeoListRow(
                                title = "${session.topic} · ${session.status}",
                                subtitle = session.summary
                            )
                        }
                    }
                    LeoOutlinedButton(text = "Nueva sesión stub", onClick = { viewModel.startStubSession() })
                }
            }
        }
    }
}

@Composable
fun M26RecommendationsScreen(onNavigateBack: () -> Unit, viewModel: M26RecommendationsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Recomendaciones", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Solo se muestran recomendaciones con revisión humana aprobada.", style = MaterialTheme.typography.bodyMedium)
            when (val s = state) {
                M26RecommendationsUiState.Loading -> LoadingState()
                M26RecommendationsUiState.Empty -> {
                    EmptyState(title = "Sin recomendaciones aptas", message = "No hay recomendaciones evaluadas para mostrar.")
                    LeoOutlinedButton(text = "Enviar muestra a revisión", onClick = { viewModel.submitSample() })
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
fun M26HistoryScreen(onNavigateBack: () -> Unit, viewModel: M26HistoryViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Historial de asistencia", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text("Resultados personales — no constituyen verdad garantizada.", style = MaterialTheme.typography.bodyMedium)
            when (val s = state) {
                M26HistoryUiState.Loading -> LoadingState()
                M26HistoryUiState.Empty -> EmptyState(title = "Sin historial", message = "Todavía no solicitaste análisis.")
                is M26HistoryUiState.Error -> ErrorState(message = s.message)
                is M26HistoryUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(s.items, key = { it.summary }) { item ->
                        LeoListRow(
                            title = item.summary,
                            subtitle = "${item.resultType} · ${item.status} · modelo ${item.modelName}@${item.modelVersion}" +
                                if (item.isEstimate) " · Sugerencia estimativa — requiere revisión si aplica." else ""
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun M26ReviewQueueScreen(onNavigateBack: () -> Unit, viewModel: M26ReviewQueueViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Revisión humana", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cola de calidad IA — distinta de la moderación de contenido.", style = MaterialTheme.typography.bodyMedium)
            when (val s = state) {
                M26ReviewQueueUiState.Loading -> LoadingState()
                M26ReviewQueueUiState.Empty -> EmptyState(title = "Sin pendientes", message = "No hay resultados en revisión o no tenés permiso.")
                is M26ReviewQueueUiState.Error -> ErrorState(message = s.message)
                is M26ReviewQueueUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.items, key = { it.resultId }) { item ->
                        Column(Modifier.fillMaxWidth()) {
                            LeoListRow(
                                title = item.summary,
                                subtitle = "${item.resultType} · v${item.modelVersion}",
                                showDivider = false
                            )
                            LeoPrimaryButton(text = "Aprobar", onClick = { viewModel.approve(item.resultId) })
                            LeoOutlinedButton(text = "Rechazar", onClick = { viewModel.reject(item.resultId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun M26VisualMatchCard(item: M26PublicVisualMatch) {
    LeoListRow(
        title = "Posible coincidencia (estimación)",
        subtitle = "${item.sourceLabel} ↔ ${item.targetLabel} · Similitud estimada ${"%.0f".format(item.score * 100)} · ${item.confidenceBand} · ${item.status}"
    )
}

@Composable
private fun M26DuplicateCard(item: M26PublicDuplicateCandidate) {
    LeoListRow(
        title = "${item.primaryLabel} / ${item.duplicateLabel}",
        subtitle = "Similitud ${"%.0f".format(item.similarityScore * 100)}% · ${item.status}"
    )
}

@Composable
private fun M26RecommendationCard(item: M26PublicRecommendation) {
    LeoListRow(
        title = item.title,
        subtitle = "${item.kind} · revisada=${item.humanReviewed} · apta=${item.approvedForDisplay} · ${item.rationale}"
    )
}
