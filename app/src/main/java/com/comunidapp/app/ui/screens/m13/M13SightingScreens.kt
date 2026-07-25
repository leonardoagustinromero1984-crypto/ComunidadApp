package com.comunidapp.app.ui.screens.m13

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M13MatchDecisionType
import com.comunidapp.app.data.model.M13MatchNextStep
import com.comunidapp.app.data.model.M13MatchReason
import com.comunidapp.app.data.model.M13MatchStatus
import com.comunidapp.app.data.model.nextStep
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M13CaseMatchesUiState
import com.comunidapp.app.viewmodel.M13CaseMatchesViewModel
import com.comunidapp.app.viewmodel.M13MatchDetailViewModel
import com.comunidapp.app.viewmodel.M13MetricsUiState
import com.comunidapp.app.viewmodel.M13MetricsViewModel
import com.comunidapp.app.viewmodel.M13SightingCreateViewModel
import com.comunidapp.app.viewmodel.M13SightingDetailUiState
import com.comunidapp.app.viewmodel.M13SightingDetailViewModel
import com.comunidapp.app.viewmodel.M13SightingListUiState
import com.comunidapp.app.viewmodel.M13SightingListViewModel

@Composable
fun M13SightingListScreen(
    onNavigateBack: () -> Unit,
    onSightingClick: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenMetrics: (() -> Unit)? = null,
    viewModel: M13SightingListViewModel = viewModel(factory = M13SightingListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Avistamientos",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text("Reportar avistamiento")
            }
            if (onOpenMetrics != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenMetrics, modifier = Modifier.fillMaxWidth()) {
                    Text("Métricas operativas (sin PII)")
                }
            }
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                M13SightingListUiState.Loading -> LoadingState()
                M13SightingListUiState.Empty -> EmptyState(
                    title = "Sin avistamientos",
                    message = "Todavía no hay reportes públicos activos."
                )
                is M13SightingListUiState.Error -> ErrorState(message = s.message)
                is M13SightingListUiState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.items, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSightingClick(item.id) },
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "${item.species.name} · ${item.primaryColor}",
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Zona: ${item.zoneText}")
                                Text(item.descriptionPreview, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (item.hasApproximateLocation) {
                                        "Ubicación aproximada disponible"
                                    } else {
                                        "Sin coordenadas públicas"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M13SightingCreateScreen(
    caseId: String? = null,
    onNavigateBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: M13SightingCreateViewModel = viewModel(factory = M13SightingCreateViewModel.factory())
) {
    val message by viewModel.message.collectAsState()
    val createdId by viewModel.createdId.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var color by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var mediaRef by remember { mutableStateOf("") }

    LaunchedEffect(createdId) {
        createdId?.let(onCreated)
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Nuevo avistamiento",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (!caseId.isNullOrBlank()) {
                Text("Caso vinculado: $caseId", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = color,
                onValueChange = { color = it },
                label = { Text("Color principal") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = zone,
                onValueChange = { zone = it },
                label = { Text("Zona aproximada") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mediaRef,
                onValueChange = { mediaRef = it },
                label = { Text("Media M05 (opcional, m05:...)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.create(
                        caseId = caseId,
                        species = PetSpecies.DOG,
                        primaryColor = color,
                        zoneText = zone,
                        description = description,
                        mediaRefs = mediaRef.trim().takeIf { it.isNotEmpty() }?.let { listOf(it) }
                            .orEmpty()
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Guardando…" else "Publicar avistamiento")
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "No se publican coordenadas exactas ni contacto privado.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
fun M13SightingDetailScreen(
    sightingId: String,
    onNavigateBack: () -> Unit,
    viewModel: M13SightingDetailViewModel = viewModel(
        factory = M13SightingDetailViewModel.factory(sightingId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Detalle de avistamiento",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            when (val s = state) {
                M13SightingDetailUiState.Loading -> LoadingState()
                is M13SightingDetailUiState.Error -> ErrorState(message = s.message)
                is M13SightingDetailUiState.Public -> {
                    Text("${s.item.species.name} · ${s.item.primaryColor}", fontWeight = FontWeight.Bold)
                    Text("Zona: ${s.item.zoneText}")
                    Text(s.item.descriptionPreview)
                    Text("Estado: ${s.item.status}")
                }
                is M13SightingDetailUiState.Owner -> {
                    Text("${s.item.species.name} · ${s.item.primaryColor}", fontWeight = FontWeight.Bold)
                    Text("Zona: ${s.item.zoneText}")
                    Text(s.item.description)
                    Text("Estado: ${s.item.status}")
                    if (s.item.status.name == "ACTIVE") {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { viewModel.withdraw() }) {
                            Text("Retirar mi avistamiento")
                        }
                    }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun M13CaseMatchesScreen(
    caseId: String,
    onNavigateBack: () -> Unit,
    onMatchClick: (String) -> Unit,
    viewModel: M13CaseMatchesViewModel = viewModel(
        factory = M13CaseMatchesViewModel.factory(caseId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Coincidencias del caso",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            when (val s = state) {
                M13CaseMatchesUiState.Loading -> LoadingState()
                M13CaseMatchesUiState.Empty -> EmptyState(
                    title = "Sin coincidencias",
                    message = "Aún no hay candidatos explicables para este caso."
                )
                is M13CaseMatchesUiState.Error -> ErrorState(message = s.message)
                is M13CaseMatchesUiState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.items, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMatchClick(item.id) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Score ${item.score} · ${item.level}",
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Estado: ${item.status}")
                                Text(
                                    item.reasons.joinToString { it.labelEs },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M13MatchDetailScreen(
    candidateId: String,
    onNavigateBack: () -> Unit,
    viewModel: M13MatchDetailViewModel = viewModel(
        factory = M13MatchDetailViewModel.factory(candidateId)
    )
) {
    val candidate by viewModel.candidate.collectAsState()
    val decisions by viewModel.decisions.collectAsState()
    val history by viewModel.history.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Revisión de coincidencia",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            val c = candidate
            if (c == null) {
                LoadingState()
            } else {
                Text("Score ${c.score} · ${c.level}", fontWeight = FontWeight.Bold)
                Text("Estado: ${c.status}")
                val next = when (c.status.nextStep()) {
                    M13MatchNextStep.OPEN_REVIEW -> "Próximo paso: abrir revisión humana."
                    M13MatchNextStep.DECIDE -> "Próximo paso: confirmar, rechazar o marcar inconclusa."
                    M13MatchNextStep.TERMINAL -> "Estado final: no se reabre. Nueva revisión requiere otro candidato."
                    M13MatchNextStep.EXPIRE_ELIGIBLE -> "Elegible a expiración por política local."
                    M13MatchNextStep.NONE -> ""
                }
                if (next.isNotBlank()) {
                    Text(next, style = MaterialTheme.typography.bodySmall)
                }
                Text("Razones:")
                c.reasons.forEach { r: M13MatchReason ->
                    Text("• ${r.labelEs}")
                }
                Text(
                    "Sin autoconfirmación: se requiere decisión humana. " +
                        "La confirmación no cierra automáticamente el caso Lost/Found. " +
                        "Notas privadas solo para autoridad.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                val canOpen = c.status == M13MatchStatus.PROPOSED && !busy
                val canDecide = c.status == M13MatchStatus.UNDER_REVIEW && !busy
                val canWithdraw =
                    (c.status == M13MatchStatus.PROPOSED || c.status == M13MatchStatus.UNDER_REVIEW) &&
                        !busy
                if (c.status.isTerminal) {
                    Text(
                        "Acciones deshabilitadas (estado final).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.openReview() },
                    enabled = canOpen,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Abrir revisión") }
                Button(
                    onClick = {
                        viewModel.decide(M13MatchDecisionType.CONFIRMED, "HUMAN_CONFIRM")
                    },
                    enabled = canDecide,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Confirmar") }
                OutlinedButton(
                    onClick = {
                        viewModel.decide(M13MatchDecisionType.REJECTED, "HUMAN_REJECT")
                    },
                    enabled = canDecide,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Rechazar") }
                OutlinedButton(
                    onClick = {
                        viewModel.decide(M13MatchDecisionType.INCONCLUSIVE, "HUMAN_INCONCLUSIVE")
                    },
                    enabled = canDecide,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Inconclusa") }
                OutlinedButton(
                    onClick = { viewModel.withdraw() },
                    enabled = canWithdraw,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Retirar coincidencia") }

                if (history.isNotEmpty()) {
                    Text(
                        "Historial",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    history.forEach { h ->
                        val from = h.fromStatus?.name ?: "—"
                        Text("• $from → ${h.toStatus.name}${h.reason?.let { " ($it)" } ?: ""}")
                    }
                }
                if (decisions.isNotEmpty()) {
                    Text(
                        "Decisiones",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    decisions.forEach { d ->
                        Text("• ${d.decision.name} · ${d.reasonCode} · ${d.actorAuthority}")
                    }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun M13MetricsScreen(
    onNavigateBack: () -> Unit,
    viewModel: M13MetricsViewModel = viewModel(factory = M13MetricsViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Métricas M13",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Agregados sin PII (sin nombres, contactos, coords ni notas).",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                M13MetricsUiState.Loading -> LoadingState()
                is M13MetricsUiState.Error -> ErrorState(
                    message = s.message,
                    onRetry = { viewModel.refresh() }
                )
                is M13MetricsUiState.Content -> {
                    val m = s.metrics
                    Text("Zona horaria: ${m.zoneIdName}", style = MaterialTheme.typography.labelSmall)
                    Text("Avistamientos por estado", fontWeight = FontWeight.SemiBold)
                    m.sightingsByStatus.forEach { (k, v) -> Text("- $k: $v") }
                    Spacer(Modifier.height(8.dp))
                    Text("Candidatos por nivel", fontWeight = FontWeight.SemiBold)
                    m.candidatesByLevel.forEach { (k, v) -> Text("- $k: $v") }
                    Spacer(Modifier.height(8.dp))
                    Text("Candidatos por estado", fontWeight = FontWeight.SemiBold)
                    m.candidatesByStatus.forEach { (k, v) -> Text("- $k: $v") }
                    Spacer(Modifier.height(8.dp))
                    val rate = m.confirmationRate?.let { pct -> "${(pct * 100).toInt()}%" } ?: "-"
                    val avgReview = m.avgMinutesToReview?.let { v -> "%.1f".format(v) } ?: "-"
                    val avgDecision = m.avgMinutesToDecision?.let { v -> "%.1f".format(v) } ?: "-"
                    Text("Tasa confirmación: $rate")
                    Text("Media min. a revisión: $avgReview")
                    Text("Media min. a decisión: $avgDecision")
                    Text(
                        "Expirados — avistamientos: ${m.expiredSightings}, matches: ${m.expiredMatches}"
                    )
                    if (m.reasonDistribution.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Razones de coincidencia", fontWeight = FontWeight.SemiBold)
                        m.reasonDistribution.forEach { (k, v) -> Text("- $k: $v") }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualizar")
                    }
                }
            }
        }
    }
}
