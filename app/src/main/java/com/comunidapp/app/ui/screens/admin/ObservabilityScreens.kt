package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.comunidapp.app.viewmodel.observability.ObservabilityHealthViewModel
import com.comunidapp.app.viewmodel.observability.ObservabilityIncidentsViewModel
import com.comunidapp.app.viewmodel.observability.ObservabilityMetricsViewModel
import com.comunidapp.app.viewmodel.observability.ObservabilityOverviewViewModel

@Composable
fun ObservabilityOverviewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMetrics: () -> Unit = {},
    onNavigateToHealth: () -> Unit = {},
    onNavigateToIncidents: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToErrors: () -> Unit = {},
    onNavigateToExports: () -> Unit = {},
    viewModel: ObservabilityOverviewViewModel = viewModel(factory = ObservabilityOverviewViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    AdministrativePhaseHost(
        title = "Observabilidad",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin datos",
        emptyMessage = "No hay resumen operativo disponible.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar el overview.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        val summary = uiState.summary
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Estado general", fontWeight = FontWeight.SemiBold)
                        Text(summary?.overallStatus?.name ?: "UNKNOWN")
                        Text("HEALTHY: ${summary?.healthyCount ?: 0} · DEGRADED: ${summary?.degradedCount ?: 0}")
                        Text("UNHEALTHY: ${summary?.unhealthyCount ?: 0} · UNKNOWN: ${summary?.unknownCount ?: 0}")
                        Text("Incidentes abiertos: ${summary?.openIncidents ?: 0}")
                        Text("Dead letters: ${summary?.deadLetterCount?.toString() ?: "—"}")
                        Text("Backlog M06: ${summary?.outboxBacklog?.toString() ?: "—"}")
                        Text("Errores (fingerprints): ${summary?.uniqueErrorFingerprints?.toString() ?: "—"}")
                        Text("Denegaciones: ${summary?.authorizationDenials?.toString() ?: "—"}")
                        Text("Uploads fallidos: ${summary?.uploadFailures?.toString() ?: "—"}")
                        Text("Casos abiertos: ${summary?.openModerationCases?.toString() ?: "—"}")
                        Text("Tickets abiertos: ${summary?.openSupportTickets?.toString() ?: "—"}")
                        Text("Última actualización: ${summary?.lastUpdatedAt ?: "—"}")
                        Text("Staging: ${summary?.stagingStatus ?: "PENDIENTE"}")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onNavigateToMetrics) { Text("Métricas") }
                    OutlinedButton(onClick = onNavigateToHealth) { Text("Health") }
                    OutlinedButton(onClick = onNavigateToIncidents) { Text("Incidentes") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onNavigateToAudit) { Text("Auditoría") }
                    TextButton(onClick = onNavigateToErrors) { Text("Errores") }
                    TextButton(onClick = onNavigateToExports) { Text("Exportaciones") }
                }
            }
        }
    }
}

@Composable
fun ObservabilityMetricsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ObservabilityMetricsViewModel = viewModel(factory = ObservabilityMetricsViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    AdministrativePhaseHost(
        title = "Métricas operativas",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin métricas",
        emptyMessage = "No hay métricas agregadas para los filtros actuales.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar las métricas.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.filters.module?.name.orEmpty(),
                    onValueChange = viewModel::setModule,
                    label = { Text("Módulo") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.filters.metricKey,
                    onValueChange = viewModel::setMetricKey,
                    label = { Text("Métrica") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                TextButton(onClick = viewModel::clearFilters) { Text("Limpiar filtros") }
            }
            items(uiState.metrics, key = { it.id }) { metric ->
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text(metric.metricKey, fontWeight = FontWeight.SemiBold)
                        Text("${metric.valueNumeric} ${metric.unit}")
                        Text(
                            "${metric.module} · ${metric.metricType} · ${metric.source}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(metric.recordedAt.toString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ObservabilityHealthScreen(
    onNavigateBack: () -> Unit,
    viewModel: ObservabilityHealthViewModel = viewModel(factory = ObservabilityHealthViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    AdministrativePhaseHost(
        title = "Health checks",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin checks",
        emptyMessage = "No hay evidencias de health checks.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar health checks.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.checks, key = { it.id }) { check ->
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(check.checkKey, fontWeight = FontWeight.SemiBold)
                        Text("Status: ${check.status}")
                        Text("Latencia: ${check.latencyMs?.toString() ?: "—"} ms")
                        Text("Checked: ${check.checkedAt}")
                        Text("Expires: ${check.expiresAt ?: "—"}")
                        Text(
                            "Detalle: ${check.details["reason"] ?: "sanitized"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (uiState.canRunManual) {
                            Button(onClick = { viewModel.runManual(check.checkKey) }) {
                                Text("Ejecutar manual")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ObservabilityIncidentsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ObservabilityIncidentsViewModel = viewModel(factory = ObservabilityIncidentsViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    AdministrativePhaseHost(
        title = "Incidentes",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin incidentes",
        emptyMessage = "No hay incidentes para los filtros actuales.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar incidentes.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.filters.incidentStatus,
                    onValueChange = viewModel::setStatusFilter,
                    label = { Text("Estado (OPEN/ACKNOWLEDGED/RESOLVED)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                TextButton(onClick = viewModel::clearFilters) { Text("Limpiar filtros") }
            }
            items(uiState.incidents, key = { it.id }) { incident ->
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(incident.titleCode, fontWeight = FontWeight.SemiBold)
                        Text("${incident.state} · ${incident.severity}")
                        Text("Occurrences: ${incident.occurrenceCount}")
                        Text(incident.summary, style = MaterialTheme.typography.bodySmall)
                        if (uiState.canManage && incident.state.name == "OPEN") {
                            Button(onClick = { viewModel.acknowledge(incident.id) }) {
                                Text("Acknowledge")
                            }
                        }
                        if (uiState.canManage &&
                            (incident.state.name == "OPEN" || incident.state.name == "ACKNOWLEDGED")
                        ) {
                            OutlinedButton(onClick = { viewModel.resolve(incident.id) }) {
                                Text("Resolve")
                            }
                        }
                    }
                }
            }
        }
    }
}
