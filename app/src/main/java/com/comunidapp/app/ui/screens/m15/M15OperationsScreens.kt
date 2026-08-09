package com.comunidapp.app.ui.screens.m15

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import com.comunidapp.app.data.model.M15OperationalMetrics
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M15OperationsUiState
import com.comunidapp.app.viewmodel.M15OperationsViewModel

private val tabLabels = listOf("Resumen", "Métricas", "Privacidad", "Smoke")

@Composable
fun M15OperationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: M15OperationsViewModel = viewModel(factory = M15OperationsViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val tab by viewModel.selectedTab.collectAsState()
    val hooks by viewModel.preparedHooks.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Operaciones de tránsito",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabLabels.forEachIndexed { index, label ->
                    FilterChip(
                        selected = tab == index,
                        onClick = { viewModel.selectTab(index) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                M15OperationsUiState.Loading -> LoadingState()
                is M15OperationsUiState.Error -> ErrorState(message = s.message)
                is M15OperationsUiState.Content -> Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        s.noPersonalDataNotice,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    M15StatusCard(
                        m06Status = s.m06Status,
                        remotePending = s.remotePending,
                        hookCount = hooks.size
                    )
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    when (tab) {
                        0 -> M15SummaryTab(s.metrics)
                        1 -> M15MetricsTab(s.metrics)
                        2 -> M15PrivacyTab()
                        3 -> M15SmokeTab()
                    }
                    Button(onClick = { viewModel.loadDefaultMetrics() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Actualizar métricas (30 días)")
                    }
                }
            }
        }
    }
}

@Composable
private fun M15StatusCard(m06Status: String, remotePending: Boolean, hookCount: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Infraestructura de notificaciones", fontWeight = FontWeight.Bold)
            Text("Estado: $m06Status")
            Text("Hooks preparados: $hookCount")
            if (remotePending) {
                Text(
                    "Fallback remoto activo — validación externa pendiente.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun M15SummaryTab(metrics: M15OperationalMetrics?) {
    if (metrics == null) {
        Text("Sin métricas locales en este modo. Fallback remoto o rango vacío.")
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Hogares", fontWeight = FontWeight.Bold)
            Text("Capacidad total: ${metrics.totalCapacity}")
            Text("Ocupadas: ${metrics.occupiedSlots} · Reservadas: ${metrics.reservedSlots}")
            Text("Disponibles: ${metrics.availableSlots}")
            Spacer(Modifier.height(8.dp))
            Text("Solicitudes", fontWeight = FontWeight.Bold)
            Text("Enviadas: ${metrics.requestsSubmitted} · Aceptadas: ${metrics.requestsAccepted}")
            Text("Rechazadas: ${metrics.requestsRejected} · Canceladas: ${metrics.requestsCancelled}")
            Spacer(Modifier.height(8.dp))
            Text("Placements", fontWeight = FontWeight.Bold)
            Text("Activos: ${metrics.placementsActive} · Completados: ${metrics.placementsCompleted}")
            Text("Interrumpidos: ${metrics.placementsInterrupted}")
            Spacer(Modifier.height(8.dp))
            Text("Calidad operativa", fontWeight = FontWeight.Bold)
            Text("Conflictos: ${metrics.conflicts} · Reintentos idempotentes: ${metrics.idempotentRetries}")
            Text("Fallbacks remotos: ${metrics.remoteFallbacks}")
        }
    }
}

@Composable
private fun M15MetricsTab(metrics: M15OperationalMetrics?) {
    if (metrics == null) {
        Text("Métricas no disponibles.")
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Rango", fontWeight = FontWeight.Bold)
            Text("TZ: ${metrics.zoneIdName}")
            Text("Desde: ${metrics.fromInclusive} → Hasta: ${metrics.toExclusive}")
            Spacer(Modifier.height(8.dp))
            Text("Evolución agregada", fontWeight = FontWeight.Bold)
            metrics.evolutionByType.forEach { (type, count) ->
                if (count > 0) Text("$type: $count")
            }
            Text("Alertas salud: ${metrics.evolutionHealthAlerts}")
            Text("Incidentes: ${metrics.evolutionIncidents}")
            Spacer(Modifier.height(8.dp))
            Text("Gastos por categoría", fontWeight = FontWeight.Bold)
            metrics.expensesByCategory.forEach { (cat, count) ->
                if (count > 0) Text("$cat: $count")
            }
            Text("Suma por moneda (minor units):", fontWeight = FontWeight.Bold)
            metrics.expenseSumByCurrency.forEach { (cur, sum) ->
                Text("$cur: $sum")
            }
            Spacer(Modifier.height(8.dp))
            Text("Ayuda", fontWeight = FontWeight.Bold)
            Text("Abiertas: ${metrics.helpOpen} · En curso: ${metrics.helpInProgress}")
            Text("Resueltas: ${metrics.helpResolved}")
            metrics.avgMinutesToResolution?.let {
                Text("Tiempo medio resolución solicitudes: ${"%.1f".format(it)} min")
            }
            if (metrics.errorsByCode.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Errores por código", fontWeight = FontWeight.Bold)
                metrics.errorsByCode.forEach { (code, count) ->
                    Text("$code: $count")
                }
            }
        }
    }
}

@Composable
private fun M15PrivacyTab() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Privacidad de tránsito", fontWeight = FontWeight.Bold)
            Text("Permitido en vistas públicas:")
            Text("· Alias/nombre público del hogar")
            Text("· Zona aproximada, especies, capacidad agregada, disponibilidad")
            Text("Prohibido:")
            Text("· Dirección exacta, coordenadas, teléfono, correo")
            Text("· IDs internos, notas privadas, evolución detallada")
            Text("· Gastos, comprobantes, solicitudes de ayuda privadas")
            Text("· Identidad completa del cuidador")
            Text(
                "Las métricas y este panel nunca muestran PII ni identificadores.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun M15SmokeTab() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Smoke funcional remoto", fontWeight = FontWeight.Bold)
            Text("Smoke funcional remoto pendiente externo")
            Text("Checklist manual (no ejecutado desde Cursor):")
            listOf(
                "Abrir hub de tránsito",
                "Consultar hogares de tránsito",
                "Crear solicitud → aceptar/rechazar",
                "Reservar → iniciar placement",
                "Agregar evolución, gasto, ayuda",
                "Egresar y verificar capacidad + custodia revocada",
                "Verificar privacidad pública",
                "Verificar eventos M06 o fallback",
                "Verificar métricas agregadas",
                "Confirmar sin duplicación con hogares de acogida"
            ).forEachIndexed { i, step ->
                Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Próxima acción: ejecutar checklist en entorno remoto con auth real.",
                fontWeight = FontWeight.Medium
            )
        }
    }
}
