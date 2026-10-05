package com.comunidapp.app.ui.screens.m13

import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream

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
import androidx.compose.material3.MaterialTheme
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
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker
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
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.m13.ContributeSpecies
import com.comunidapp.app.ui.media.rememberLeoVerPhotoSourcePicker
import com.comunidapp.app.ui.theme.LeoDimens

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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Avistamientos",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            LeoOutlinedButton(
                text = "Reportar avistamiento",
                onClick = onCreate
            )
            if (onOpenMetrics != null) {
                Spacer(Modifier.height(8.dp))
                LeoOutlinedButton(
                    text = "Métricas operativas (sin PII)",
                    onClick = onOpenMetrics
                )
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSightingClick(item.id) }
                        ) {
                            Column(Modifier.padding(LeoDimens.SpaceCompact)) {
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
                            LeoHairline()
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
    val cases by DataProvider.lostFoundRepository.observeLostFoundPosts().collectAsState()
    val caseSpecies = cases.firstOrNull { it.id == caseId }?.species
    var species by remember(caseId, caseSpecies) {
        mutableStateOf(ContributeSpecies.fromCase(caseSpecies))
    }
    var color by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var mediaRef by remember { mutableStateOf("") }
    val initialMoment = remember { System.currentTimeMillis() }
    var observedDate by remember {
        mutableStateOf(com.comunidapp.app.domain.lostfound.IncidentMoment.dateText(initialMoment))
    }
    var observedTime by remember {
        mutableStateOf(com.comunidapp.app.domain.lostfound.IncidentMoment.timeText(initialMoment))
    }
    val pickPhoto = rememberLeoVerPhotoSourcePicker(
        sheetTitle = "Foto del avistamiento",
        onSourceSelected = { uri -> mediaRef = uri.toString() }
    )

    LaunchedEffect(createdId) {
        createdId?.let(onCreated)
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
            V2LocationStringPicker(
                value = zone,
                onValueChange = { zone = it }
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = observedDate,
                onValueChange = { observedDate = it },
                label = { Text("Fecha del avistamiento") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = observedTime,
                onValueChange = { observedTime = it },
                label = { Text("Hora del avistamiento") },
                modifier = Modifier.fillMaxWidth()
            )
            LeoOutlinedButton(
                text = if (mediaRef.isBlank()) "Agregar foto" else "Cambiar foto",
                onClick = pickPhoto
            )
            if (mediaRef.isNotBlank()) {
                Text("Foto seleccionada", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            LeoPrimaryButton(
                text = if (busy) "Guardando…" else "Publicar avistamiento",
                onClick = {
                    viewModel.create(
                        caseId = caseId,
                        species = species,
                        primaryColor = color,
                        zoneText = zone,
                        description = description,
                        mediaRefs = mediaRef.trim().takeIf { it.isNotEmpty() }?.let { listOf(it) }
                            .orEmpty(),
                        observedAt = com.comunidapp.app.domain.lostfound.IncidentMoment.combine(
                            observedDate,
                            observedTime,
                            initialMoment
                        )
                    )
                },
                enabled = !busy
            )
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                        LeoOutlinedButton(
                            text = "Retirar mi avistamiento",
                            onClick = { viewModel.withdraw() }
                        )
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                        LeoListRow(
                            title = "Score ${item.score} · ${item.level}",
                            subtitle = "Estado: ${item.status} · ${item.reasons.joinToString { it.labelEs }}",
                            onClick = { onMatchClick(item.id) }
                        )
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                LeoOutlinedButton(
                    text = "Abrir revisión",
                    onClick = { viewModel.openReview() },
                    enabled = canOpen
                )
                LeoPrimaryButton(
                    text = "Confirmar",
                    onClick = {
                        viewModel.decide(M13MatchDecisionType.CONFIRMED, "HUMAN_CONFIRM")
                    },
                    enabled = canDecide
                )
                LeoOutlinedButton(
                    text = "Rechazar",
                    onClick = {
                        viewModel.decide(M13MatchDecisionType.REJECTED, "HUMAN_REJECT")
                    },
                    enabled = canDecide
                )
                LeoOutlinedButton(
                    text = "Inconclusa",
                    onClick = {
                        viewModel.decide(M13MatchDecisionType.INCONCLUSIVE, "HUMAN_INCONCLUSIVE")
                    },
                    enabled = canDecide
                )
                LeoOutlinedButton(
                    text = "Retirar coincidencia",
                    onClick = { viewModel.withdraw() },
                    enabled = canWithdraw
                )

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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Métricas de avistamientos",
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
                    LeoOutlinedButton(
                        text = "Actualizar",
                        onClick = { viewModel.refresh() }
                    )
                }
            }
        }
    }
}
