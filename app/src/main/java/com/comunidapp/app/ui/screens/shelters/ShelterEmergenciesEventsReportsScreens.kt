package com.comunidapp.app.ui.screens.shelters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.ShelterEmergencyCategory
import com.comunidapp.app.data.model.ShelterEmergencySeverity
import com.comunidapp.app.data.model.ShelterEmergencyStatus
import com.comunidapp.app.data.model.ShelterEmergencyVisibility
import com.comunidapp.app.data.model.ShelterEventRegistrationStatus
import com.comunidapp.app.data.model.ShelterEventStatus
import com.comunidapp.app.data.model.ShelterEventType
import com.comunidapp.app.data.model.ShelterEventVisibility
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.ShelterEmergencyDetailUiState
import com.comunidapp.app.viewmodel.ShelterEmergencyDetailViewModel
import com.comunidapp.app.viewmodel.ShelterEmergencyFormViewModel
import com.comunidapp.app.viewmodel.ShelterEmergenciesViewModel
import com.comunidapp.app.viewmodel.ShelterEventDetailUiState
import com.comunidapp.app.viewmodel.ShelterEventDetailViewModel
import com.comunidapp.app.viewmodel.ShelterEventFormViewModel
import com.comunidapp.app.viewmodel.ShelterEventRegistrationsViewModel
import com.comunidapp.app.viewmodel.ShelterEventsViewModel
import com.comunidapp.app.viewmodel.ShelterPublicEmergenciesUiState
import com.comunidapp.app.viewmodel.ShelterPublicEmergenciesViewModel
import com.comunidapp.app.viewmodel.ShelterPublicEventsUiState
import com.comunidapp.app.viewmodel.ShelterPublicEventsViewModel
import com.comunidapp.app.viewmodel.ShelterReportsUiState
import com.comunidapp.app.viewmodel.ShelterReportsViewModel

private const val MS_PER_DAY = 86_400_000L

@Composable
fun ShelterPublicEmergenciesScreen(
    onNavigateBack: () -> Unit,
    onEmergencyClick: (String) -> Unit,
    viewModel: ShelterPublicEmergenciesViewModel = viewModel(factory = ShelterPublicEmergenciesViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Urgencias públicas", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            when (val s = state) {
                ShelterPublicEmergenciesUiState.Loading -> LoadingState()
                ShelterPublicEmergenciesUiState.Empty -> EmptyState(title = "No hay urgencias activas.")
                is ShelterPublicEmergenciesUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::refresh)
                is ShelterPublicEmergenciesUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.items, key = { it.id }) { item ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onEmergencyClick(item.id) }.padding(8.dp)
                        ) {
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                            item.shelterDisplayName?.let { Text(it) }
                            Text("${item.category.name} · ${item.severity.name} · ${item.status.name}")
                            Text(item.description)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterEmergenciesScreen(
    onNavigateBack: () -> Unit,
    onCreate: () -> Unit,
    onDetail: (String) -> Unit,
    viewModel: ShelterEmergenciesViewModel
) {
    val emergencies by viewModel.emergencies.collectAsState()
    val error by viewModel.error.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Urgencias", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Nueva urgencia") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (emergencies.isEmpty()) EmptyState(title = "Sin urgencias.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(emergencies, key = { it.id }) { e ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onDetail(e.id) }.padding(8.dp)
                    ) {
                        Text("${e.title} · ${e.status.name}", fontWeight = FontWeight.SemiBold)
                        Text("${e.category.name} · ${e.severity.name} · ${e.visibility.name}")
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterEmergencyDetailScreen(
    onNavigateBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    viewModel: ShelterEmergencyDetailViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    var resolveNotes by remember { mutableStateOf("") }
    var showResolve by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Detalle de urgencia", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            ShelterEmergencyDetailUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is ShelterEmergencyDetailUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is ShelterEmergencyDetailUiState.Content -> {
                val e = s.emergency
                Column(
                    Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(e.title, style = MaterialTheme.typography.titleLarge)
                    Text(e.description)
                    Text("Estado: ${e.status.name} · ${e.category.name} · ${e.severity.name}")
                    Text("Visibilidad: ${e.visibility.name}")
                    e.petId?.let { Text("Mascota: $it") }
                    e.resolutionNotes?.let { Text("Resolución: $it") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (e.status == ShelterEmergencyStatus.DRAFT || e.status == ShelterEmergencyStatus.ACTIVE) {
                        OutlinedButton(
                            onClick = { onEdit(e.shelterProfileId, e.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Editar") }
                    }
                    when (e.status) {
                        ShelterEmergencyStatus.DRAFT -> OutlinedButton(
                            enabled = !busy,
                            onClick = { viewModel.changeStatus(ShelterEmergencyStatus.ACTIVE) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Activar") }
                        ShelterEmergencyStatus.ACTIVE -> {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { showResolve = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Resolver") }
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { viewModel.changeStatus(ShelterEmergencyStatus.CANCELLED) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Cancelar") }
                        }
                        else -> Unit
                    }
                    if (showResolve && e.status == ShelterEmergencyStatus.ACTIVE) {
                        OutlinedTextField(
                            resolveNotes,
                            { resolveNotes = it },
                            label = { Text("Notas de resolución") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = !busy && resolveNotes.isNotBlank(),
                            onClick = {
                                viewModel.resolve(resolveNotes)
                                showResolve = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Confirmar resolución") }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterEmergencyFormScreen(
    shelterId: String,
    editEmergencyId: String? = null,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ShelterEmergencyFormViewModel
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ShelterEmergencyCategory.OTHER) }
    var severity by remember { mutableStateOf(ShelterEmergencySeverity.MEDIUM) }
    var visibility by remember { mutableStateOf(ShelterEmergencyVisibility.PUBLIC) }
    var petId by remember { mutableStateOf("") }
    var evidenceRef by remember { mutableStateOf("") }
    var activate by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val existing by viewModel.existing.collectAsState()
    LaunchedEffect(existing) {
        existing?.let {
            title = it.title
            description = it.description
            category = it.category
            severity = it.severity
            visibility = it.visibility
            petId = it.petId.orEmpty()
            evidenceRef = it.evidenceRef.orEmpty()
        }
    }
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved(it) } }
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = if (editEmergencyId == null) "Nueva urgencia" else "Editar urgencia",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                description,
                { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                petId,
                { petId = it },
                label = { Text("ID mascota (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                evidenceRef,
                { evidenceRef = it },
                label = { Text("Evidencia M05 (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Categoría")
            ShelterEmergencyCategory.entries.filter { it != ShelterEmergencyCategory.UNKNOWN }.forEach { cat ->
                EerRowRadio(cat.name, category == cat) { category = cat }
            }
            Text("Severidad")
            ShelterEmergencySeverity.entries.filter { it != ShelterEmergencySeverity.UNKNOWN }.forEach { sev ->
                EerRowRadio(sev.name, severity == sev) { severity = sev }
            }
            Text("Visibilidad")
            ShelterEmergencyVisibility.entries.filter { it != ShelterEmergencyVisibility.UNKNOWN }.forEach { vis ->
                EerRowRadio(vis.name, visibility == vis) { visibility = vis }
            }
            if (editEmergencyId == null) {
                EerRowCheck("Activar al crear", activate) { activate = it }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !submitting,
                onClick = {
                    val pet = petId.trim().takeIf { it.isNotEmpty() }
                    val evidence = evidenceRef.trim().takeIf { it.isNotEmpty() }
                    if (editEmergencyId == null) {
                        viewModel.create(
                            title, description, category, severity, visibility,
                            now, null, pet, evidence, activate
                        )
                    } else {
                        viewModel.update(
                            title, description, category, severity, visibility,
                            now, null, pet, evidence
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Guardando…" else "Guardar") }
        }
    }
}

@Composable
fun ShelterPublicEventsScreen(
    onNavigateBack: () -> Unit,
    onEventClick: (String) -> Unit,
    viewModel: ShelterPublicEventsViewModel = viewModel(factory = ShelterPublicEventsViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Eventos públicos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            when (val s = state) {
                ShelterPublicEventsUiState.Loading -> LoadingState()
                ShelterPublicEventsUiState.Empty -> EmptyState(title = "No hay eventos publicados.")
                is ShelterPublicEventsUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::refresh)
                is ShelterPublicEventsUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.items, key = { it.id }) { item ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onEventClick(item.id) }.padding(8.dp)
                        ) {
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                            item.shelterDisplayName?.let { Text(it) }
                            Text("${item.eventType.name} · ${item.status.name}")
                            Text(item.description)
                            item.publicLocationText?.let { Text("Ubicación: $it") }
                            item.capacity?.let { cap ->
                                Text("Inscriptos: ${item.registeredCount} / $cap")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterEventsScreen(
    onNavigateBack: () -> Unit,
    onCreate: () -> Unit,
    onDetail: (String) -> Unit,
    viewModel: ShelterEventsViewModel
) {
    val events by viewModel.events.collectAsState()
    val error by viewModel.error.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Eventos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Nuevo evento") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (events.isEmpty()) EmptyState(title = "Sin eventos.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { it.id }) { ev ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onDetail(ev.id) }.padding(8.dp)
                    ) {
                        Text("${ev.title} · ${ev.status.name}", fontWeight = FontWeight.SemiBold)
                        Text("${ev.eventType.name} · ${ev.visibility.name}")
                        ev.capacity?.let { Text("Inscriptos: ${ev.registeredCount} / $it") }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterEventDetailScreen(
    onNavigateBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    onRegistrations: (String) -> Unit,
    viewModel: ShelterEventDetailViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    var registerNotes by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Detalle de evento", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            ShelterEventDetailUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is ShelterEventDetailUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is ShelterEventDetailUiState.Content -> {
                val ev = s.event
                Column(
                    Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(ev.title, style = MaterialTheme.typography.titleLarge)
                    Text(ev.description)
                    Text("Estado: ${ev.status.name} · ${ev.eventType.name} · ${ev.visibility.name}")
                    ev.publicLocationText?.let { Text("Ubicación pública: $it") }
                    ev.privateLocationText?.let { Text("Ubicación privada: $it") }
                    ev.capacity?.let { Text("Inscriptos: ${ev.registeredCount} / $it") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (ev.status == ShelterEventStatus.DRAFT ||
                        ev.status == ShelterEventStatus.PUBLISHED ||
                        ev.status == ShelterEventStatus.FULL
                    ) {
                        OutlinedButton(
                            onClick = { onEdit(ev.shelterProfileId, ev.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Editar") }
                    }
                    OutlinedButton(
                        onClick = { onRegistrations(ev.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Inscripciones") }
                    if (ev.status == ShelterEventStatus.PUBLISHED || ev.status == ShelterEventStatus.FULL) {
                        OutlinedTextField(
                            registerNotes,
                            { registerNotes = it },
                            label = { Text("Notas de inscripción (opcional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                viewModel.register(registerNotes.trim().takeIf { it.isNotEmpty() })
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Inscribirme") }
                    }
                    when (ev.status) {
                        ShelterEventStatus.DRAFT -> OutlinedButton(
                            enabled = !busy,
                            onClick = { viewModel.changeStatus(ShelterEventStatus.PUBLISHED) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Publicar") }
                        ShelterEventStatus.PUBLISHED, ShelterEventStatus.FULL -> {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { viewModel.changeStatus(ShelterEventStatus.COMPLETED) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Completar") }
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { viewModel.changeStatus(ShelterEventStatus.CANCELLED) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Cancelar evento") }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterEventFormScreen(
    shelterId: String,
    editEventId: String? = null,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ShelterEventFormViewModel
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf(ShelterEventType.OTHER) }
    var visibility by remember { mutableStateOf(ShelterEventVisibility.PUBLIC) }
    var capacityText by remember { mutableStateOf("") }
    var publicLocation by remember { mutableStateOf("") }
    var privateLocation by remember { mutableStateOf("") }
    var coverRef by remember { mutableStateOf("") }
    var publish by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }
    val endsAt = remember { now + MS_PER_DAY }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val existing by viewModel.existing.collectAsState()
    LaunchedEffect(existing) {
        existing?.let {
            title = it.title
            description = it.description
            eventType = it.eventType
            visibility = it.visibility
            capacityText = it.capacity?.toString().orEmpty()
            publicLocation = it.publicLocationText.orEmpty()
            privateLocation = it.privateLocationText.orEmpty()
            coverRef = it.coverAssetRef.orEmpty()
        }
    }
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved(it) } }
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = if (editEventId == null) "Nuevo evento" else "Editar evento",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                description,
                { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                capacityText,
                { capacityText = it },
                label = { Text("Cupo (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                publicLocation,
                { publicLocation = it },
                label = { Text("Ubicación pública") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                privateLocation,
                { privateLocation = it },
                label = { Text("Ubicación privada") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                coverRef,
                { coverRef = it },
                label = { Text("Portada M05 (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Tipo")
            ShelterEventType.entries.filter { it != ShelterEventType.UNKNOWN }.forEach { type ->
                EerRowRadio(type.name, eventType == type) { eventType = type }
            }
            Text("Visibilidad")
            ShelterEventVisibility.entries.filter { it != ShelterEventVisibility.UNKNOWN }.forEach { vis ->
                EerRowRadio(vis.name, visibility == vis) { visibility = vis }
            }
            if (editEventId == null) {
                EerRowCheck("Publicar al crear", publish) { publish = it }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !submitting,
                onClick = {
                    val cap = capacityText.trim().toIntOrNull()
                    val pubLoc = publicLocation.trim().takeIf { it.isNotEmpty() }
                    val privLoc = privateLocation.trim().takeIf { it.isNotEmpty() }
                    val cover = coverRef.trim().takeIf { it.isNotEmpty() }
                    if (editEventId == null) {
                        viewModel.create(
                            title, description, eventType, visibility,
                            now, endsAt, cap, pubLoc, privLoc, cover, publish
                        )
                    } else {
                        viewModel.update(
                            title, description, eventType, visibility,
                            now, endsAt, cap, pubLoc, privLoc, cover
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Guardando…" else "Guardar") }
        }
    }
}

@Composable
fun ShelterEventRegistrationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShelterEventRegistrationsViewModel
) {
    val registrations by viewModel.registrations.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Inscripciones", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (registrations.isEmpty()) EmptyState(title = "Sin inscripciones.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(registrations, key = { it.id }) { reg ->
                    Column(Modifier.padding(8.dp)) {
                        Text("${reg.userId} · ${reg.status.name}", fontWeight = FontWeight.SemiBold)
                        reg.notes?.let { Text("Notas: $it") }
                        if (reg.status == ShelterEventRegistrationStatus.REGISTERED ||
                            reg.status == ShelterEventRegistrationStatus.WAITLISTED
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = { viewModel.markAttendance(reg.id, true) }
                                ) { Text("Asistió") }
                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = { viewModel.markAttendance(reg.id, false) }
                                ) { Text("No asistió") }
                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = { viewModel.cancel(reg.id) }
                                ) { Text("Cancelar") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterReportsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShelterReportsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val exporting by viewModel.exporting.collectAsState()
    val error by viewModel.error.collectAsState()
    var daysBackText by remember { mutableStateOf("30") }
    val now = remember { System.currentTimeMillis() }
    LaunchedEffect(Unit) {
        val days = daysBackText.toIntOrNull() ?: 30
        viewModel.loadMetrics(now - days * MS_PER_DAY, now)
    }
    var lastExportName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.exported.collect { lastExportName = it.fileName }
    }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Reportes operativos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                daysBackText,
                { daysBackText = it },
                label = { Text("Días hacia atrás") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val days = daysBackText.toIntOrNull() ?: 30
                        viewModel.loadMetrics(now - days * MS_PER_DAY, now)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Actualizar") }
                OutlinedButton(
                    enabled = !exporting,
                    onClick = {
                        val days = daysBackText.toIntOrNull() ?: 30
                        viewModel.exportCsv(now - days * MS_PER_DAY, now)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (exporting) "Exportando…" else "Exportar CSV") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            lastExportName?.let { Text("Exportación lista: $it") }
            when (val s = state) {
                ShelterReportsUiState.Loading -> LoadingState()
                is ShelterReportsUiState.Error -> ErrorState(message = s.message, onRetry = {
                    val days = daysBackText.toIntOrNull() ?: 30
                    viewModel.loadMetrics(now - days * MS_PER_DAY, now)
                })
                is ShelterReportsUiState.Content -> {
                    val summary = s.summary
                    Text("Capacidad", fontWeight = FontWeight.SemiBold)
                    Text("Total: ${summary.capacity.totalCapacity} · Ocupación: ${summary.capacity.currentOccupancy}")
                    Text("Reservas: ${summary.capacity.reservedCapacity} · Libres: ${summary.capacity.freeSlots}")
                    Spacer(Modifier.height(8.dp))
                    Text("Mascotas", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Activas: ${summary.pets.activeCount} · Cuarentena: ${summary.pets.quarantineCount} · " +
                            "Médica: ${summary.pets.medicalCareCount}"
                    )
                    Text("Altas: ${summary.pets.releasesCount} · Adopciones: ${summary.pets.adoptionsCount}")
                    Spacer(Modifier.height(8.dp))
                    Text("Voluntarios", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Activos: ${summary.volunteers.activeCount} · Pausados: ${summary.volunteers.pausedCount} · " +
                            "Finalizados: ${summary.volunteers.endedCount}"
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Campañas", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Activas: ${summary.campaigns.activeCount} · " +
                            "Completadas: ${summary.campaigns.completedCount}"
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Insumos", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Pedidos abiertos: ${summary.supplies.openRequestsCount} · " +
                            "Cumplidos: ${summary.supplies.fulfilledRequestsCount}"
                    )
                    Text("Cantidad recibida: ${summary.supplies.quantityReceivedTotal}")
                    Spacer(Modifier.height(8.dp))
                    Text("Urgencias", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Activas: ${summary.emergencies.activeCount} · " +
                            "Críticas: ${summary.emergencies.criticalCount} · " +
                            "Resueltas: ${summary.emergencies.resolvedCount}"
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Eventos", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Próximos: ${summary.events.upcomingCount} · " +
                            "Completados: ${summary.events.completedCount} · " +
                            "Inscripciones: ${summary.events.registrationsCount}"
                    )
                }
            }
        }
    }
}

@Composable
private fun EerRowRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

@Composable
private fun EerRowCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
