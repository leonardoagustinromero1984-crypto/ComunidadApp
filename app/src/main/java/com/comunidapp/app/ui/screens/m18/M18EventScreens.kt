package com.comunidapp.app.ui.screens.m18

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import com.comunidapp.app.data.model.M18EventType
import com.comunidapp.app.data.model.M18MockOrganizations
import com.comunidapp.app.data.model.M18PublicEvent
import com.comunidapp.app.data.model.M18RegistrationStatus
import com.comunidapp.app.data.repository.M18EventValidators
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M18EventDetailViewModel
import com.comunidapp.app.viewmodel.M18EventEditUiState
import com.comunidapp.app.viewmodel.M18EventEditViewModel
import com.comunidapp.app.viewmodel.M18EventManageUiState
import com.comunidapp.app.viewmodel.M18EventManageViewModel
import com.comunidapp.app.viewmodel.M18EventOperationsUiState
import com.comunidapp.app.viewmodel.M18EventOperationsViewModel
import com.comunidapp.app.viewmodel.M18EventParticipationUiState
import com.comunidapp.app.viewmodel.M18EventsListUiState
import com.comunidapp.app.viewmodel.M18EventsListViewModel
import com.comunidapp.app.viewmodel.m18EventStatusLabel
import com.comunidapp.app.viewmodel.m18EventTypeLabel
import com.comunidapp.app.viewmodel.m18RegistrationStatusLabel

@Composable
fun M18EventsListScreen(
    onNavigateBack: () -> Unit,
    onEventClick: (String) -> Unit,
    onManage: () -> Unit,
    onCreate: () -> Unit,
    viewModel: M18EventsListViewModel = viewModel(factory = M18EventsListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var query by remember(filter.query) { mutableStateOf(filter.query) }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Eventos comunitarios", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Directorio público de eventos — cupos e inscripciones sin PII.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.setQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar evento") },
                singleLine = true
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter.withOpenSpotsOnly,
                    onClick = { viewModel.setWithOpenSpotsOnly(!filter.withOpenSpotsOnly) },
                    label = { Text("Con cupos") }
                )
                FilterChip(
                    selected = filter.completedOnly,
                    onClick = { viewModel.setCompletedOnly(!filter.completedOnly) },
                    label = { Text("Completados") }
                )
                FilterChip(
                    selected = filter.type == M18EventType.ADOPTION_FAIR,
                    onClick = {
                        viewModel.setType(
                            if (filter.type == M18EventType.ADOPTION_FAIR) null else M18EventType.ADOPTION_FAIR
                        )
                    },
                    label = { Text("Adopciones") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.clearFilters() }) { Text("Limpiar filtros") }
                OutlinedButton(onClick = onManage) { Text("Administrar") }
                Button(onClick = onCreate) { Text("Nuevo") }
            }
            when (val s = state) {
                M18EventsListUiState.Loading -> LoadingState()
                M18EventsListUiState.Empty -> EmptyState(
                    title = "Sin eventos",
                    message = "No hay eventos publicados con estos filtros."
                )
                is M18EventsListUiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.load() })
                is M18EventsListUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.items, key = { it.id }) { item ->
                        M18EventCard(item, onClick = { onEventClick(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun M18EventCard(event: M18PublicEvent, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(event.organizationDisplayName, style = MaterialTheme.typography.bodySmall)
            Text(m18EventTypeLabel(event.eventType), style = MaterialTheme.typography.labelMedium)
            Text(
                M18EventValidators.formatEventDateRange(event.startsAt, event.endsAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Cupos: ${event.registeredCount}/${event.maxCapacity}" +
                    if (event.waitlistCount > 0) " (+${event.waitlistCount} en espera)" else "",
                style = MaterialTheme.typography.bodyMedium
            )
            if (event.maxCapacity > 0) {
                LinearProgressIndicator(
                    progress = { (event.registeredCount.toFloat() / event.maxCapacity).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            event.reference.publicLocationText?.let {
                Text("📍 $it", style = MaterialTheme.typography.bodySmall)
            }
            if (event.isRegistrationOpen) {
                Text("Inscripciones abiertas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun M18EventDetailScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    viewModel: M18EventDetailViewModel = viewModel(factory = M18EventDetailViewModel.factory(eventId))
) {
    val event by viewModel.event.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val myRegistration by viewModel.myRegistration.collectAsState()
    val participation by viewModel.participation.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        if (message != null) viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Detalle evento", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when {
            loading -> LoadingState(contentModifier = Modifier.padding(padding))
            event == null -> ErrorState(
                message = "Evento no disponible",
                contentModifier = Modifier.padding(padding)
            )
            else -> {
                val e = event!!
                Column(
                    Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(e.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(e.organizationDisplayName, style = MaterialTheme.typography.bodyMedium)
                    Text(e.description)
                    Text("Estado: ${m18EventStatusLabel(e.status)}")
                    Text("Tipo: ${m18EventTypeLabel(e.eventType)}")
                    Text(M18EventValidators.formatEventDateRange(e.startsAt, e.endsAt))
                    e.venueName?.let { Text("Lugar: $it", style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "Cupos: ${e.registeredCount}/${e.maxCapacity} · Disponibles: ${e.availableSpots}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    stats?.let { s ->
                        Text(
                            "Check-ins: ${s.checkedInCount} (solo agregados, sin PII)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    e.reference.petPublicName?.let { Text("Mascota: $it", style = MaterialTheme.typography.bodySmall) }
                    e.reference.publicLocationText?.let { Text("📍 $it", style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "Eventos gratuitos — sin venta de entradas ni pagos en Bloque 1.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        "La lista de participantes no es pública. Solo ves tu estado de inscripción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    myRegistration?.let {
                        Text("Tu inscripción: ${m18RegistrationStatusLabel(it)}")
                    }
                    when (participation) {
                        M18EventParticipationUiState.Loading -> Unit
                        M18EventParticipationUiState.NotAuthenticated -> {
                            Text("Iniciá sesión para inscribirte.")
                        }
                        M18EventParticipationUiState.Available -> {
                            Button(onClick = { viewModel.register() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Inscribirme")
                            }
                        }
                        M18EventParticipationUiState.Registered -> {
                            OutlinedButton(onClick = { viewModel.cancelRegistration() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Cancelar inscripción")
                            }
                            OutlinedButton(onClick = { viewModel.scheduleReminder() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Programar recordatorio")
                            }
                        }
                        M18EventParticipationUiState.Waitlisted -> {
                            Text("Estás en lista de espera.", color = MaterialTheme.colorScheme.primary)
                            OutlinedButton(onClick = { viewModel.cancelRegistration() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Salir de la lista de espera")
                            }
                        }
                        M18EventParticipationUiState.Cancelled -> {
                            if (e.isRegistrationOpen) {
                                Button(onClick = { viewModel.register() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Volver a inscribirme")
                                }
                            }
                        }
                        M18EventParticipationUiState.EventFull -> {
                            Text("Evento completo — sin lista de espera disponible.")
                        }
                        M18EventParticipationUiState.EventClosed -> {
                            Text("Inscripciones cerradas para este evento.")
                        }
                        is M18EventParticipationUiState.Error -> {
                            Text((participation as M18EventParticipationUiState.Error).message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
fun M18EventOperationsScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    viewModel: M18EventOperationsViewModel = viewModel(factory = M18EventOperationsViewModel.factory(eventId))
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        if (message != null) viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Panel operativo",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Panel organizador — alias permitidos, sin emails ni teléfonos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            when (val s = state) {
                M18EventOperationsUiState.Loading -> LoadingState()
                M18EventOperationsUiState.PermissionDenied ->
                    ErrorState(message = "No tenés permiso para operar este evento.")
                is M18EventOperationsUiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.refresh() })
                is M18EventOperationsUiState.Content -> {
                    val summary = s.summary
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Resumen operativo", fontWeight = FontWeight.Bold)
                            Text("Capacidad: ${summary.registeredCount}/${summary.maxCapacity}")
                            Text("Lista de espera: ${summary.waitlistCount}")
                            Text("Cancelados: ${summary.cancelledCount}")
                            Text("Check-ins: ${summary.checkedInCount}")
                            Text("Asistentes: ${summary.attendedCount}")
                            Text("No-shows: ${summary.noShowCount}")
                            Text("Cupos disponibles: ${summary.availableSpots}")
                            Text("Ocupación: ${summary.occupancyPercent}%")
                            if (summary.hasCapacityInconsistency) {
                                Text("⚠ Inconsistencia de capacidad detectada", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    OutlinedButton(onClick = { viewModel.promoteWaitlist() }) {
                        Text("Promover lista de espera (manual)")
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.participants, key = { it.registrationId }) { p ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(p.displayAlias, fontWeight = FontWeight.Medium)
                                    Text(m18RegistrationStatusLabel(p.status), style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (p.canCheckIn) {
                                            OutlinedButton(onClick = { viewModel.checkIn(p.registrationId) }) {
                                                Text("Check-in")
                                            }
                                        }
                                        if (p.canMarkAttendance) {
                                            OutlinedButton(onClick = { viewModel.markAttendance(p.registrationId) }) {
                                                Text("Asistió")
                                            }
                                        }
                                        if (p.canMarkNoShow) {
                                            OutlinedButton(onClick = { viewModel.markNoShow(p.registrationId) }) {
                                                Text("No-show")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun M18EventManageScreen(
    onNavigateBack: () -> Unit,
    onEditEvent: (String) -> Unit,
    onOperations: (String) -> Unit,
    onCreate: () -> Unit,
    viewModel: M18EventManageViewModel = viewModel(factory = M18EventManageViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val selectedOrg by viewModel.selectedOrg.collectAsState()
    val message by viewModel.message.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Administrar eventos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                M18MockOrganizations.MANAGE_ORGANIZATION_IDS.forEach { orgId ->
                    FilterChip(
                        selected = selectedOrg == orgId,
                        onClick = { viewModel.selectOrganization(orgId) },
                        label = { Text(orgId.removePrefix("org_")) }
                    )
                }
            }
            Button(onClick = onCreate) { Text("Nuevo evento") }
            when (val s = state) {
                M18EventManageUiState.Loading -> LoadingState()
                M18EventManageUiState.PermissionDenied -> ErrorState(message = "No tenés permiso para administrar esta organización.")
                M18EventManageUiState.NoEvents -> EmptyState(
                    title = "Sin eventos",
                    message = "No hay eventos para esta organización."
                )
                is M18EventManageUiState.Error -> ErrorState(message = s.message)
                is M18EventManageUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.events, key = { it.id }) { ev ->
                        val summary = s.summaryById[ev.id]
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(ev.title, fontWeight = FontWeight.Bold)
                                Text("${m18EventStatusLabel(ev.status)} · ${m18EventTypeLabel(ev.eventType)}")
                                summary?.let {
                                    Text("Inscriptos: ${it.registeredCount}/${it.maxCapacity}")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onEditEvent(ev.id) }) { Text("Editar") }
                                    OutlinedButton(onClick = { onOperations(ev.id) }) { Text("Operaciones") }
                                    if (ev.status == com.comunidapp.app.data.model.M18EventStatus.DRAFT) {
                                        Button(onClick = { viewModel.publish(ev.id) }) { Text("Publicar") }
                                    }
                                    if (ev.status == com.comunidapp.app.data.model.M18EventStatus.PUBLISHED) {
                                        OutlinedButton(onClick = { viewModel.pause(ev.id) }) { Text("Pausar") }
                                        OutlinedButton(onClick = { viewModel.complete(ev.id) }) { Text("Completar") }
                                    }
                                    if (!ev.status.isTerminal) {
                                        OutlinedButton(onClick = { viewModel.cancel(ev.id) }) { Text("Cancelar") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun M18EventEditScreen(
    eventId: String?,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: M18EventEditViewModel = viewModel(factory = M18EventEditViewModel.factory(eventId))
) {
    val draft by viewModel.draft.collectAsState()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state) {
        if (state is M18EventEditUiState.Saved) {
            onSaved((state as M18EventEditUiState.Saved).eventId)
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = if (eventId == null) "Nuevo evento" else "Editar evento",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { viewModel.updateDraft { d -> d.copy(title = it) } },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { viewModel.updateDraft { d -> d.copy(description = it) } },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            OutlinedTextField(
                value = draft.maxCapacity.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { cap ->
                        viewModel.updateDraft { d -> d.copy(maxCapacity = cap) }
                    }
                },
                label = { Text("Cupo máximo") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.venueName,
                onValueChange = { viewModel.updateDraft { d -> d.copy(venueName = it) } },
                label = { Text("Nombre del lugar (público)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.publicLocationText,
                onValueChange = { viewModel.updateDraft { d -> d.copy(publicLocationText = it) } },
                label = { Text("Ubicación pública aproximada") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.petPublicName,
                onValueChange = { viewModel.updateDraft { d -> d.copy(petPublicName = it) } },
                label = { Text("Mascota (opcional, nombre público)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.durationHours.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { h ->
                        viewModel.updateDraft { d -> d.copy(durationHours = h.coerceAtLeast(1)) }
                    }
                },
                label = { Text("Duración (horas)") },
                modifier = Modifier.fillMaxWidth()
            )
            if (state is M18EventEditUiState.Error) {
                Text((state as M18EventEditUiState.Error).message, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { viewModel.save() },
                enabled = state !is M18EventEditUiState.Saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state is M18EventEditUiState.Saving) "Guardando…" else "Guardar borrador")
            }
        }
    }
}
