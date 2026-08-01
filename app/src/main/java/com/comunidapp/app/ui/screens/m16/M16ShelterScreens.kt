package com.comunidapp.app.ui.screens.m16

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.comunidapp.app.data.model.M16MockOrganizations
import com.comunidapp.app.data.model.M16OpeningHours
import com.comunidapp.app.data.model.M16OpeningPeriod
import com.comunidapp.app.data.model.M16PublicContactChannel
import com.comunidapp.app.data.model.M16PublicContactChannelType
import com.comunidapp.app.data.model.M16PublicShelter
import com.comunidapp.app.data.model.M16ShelterOperationalStatus
import com.comunidapp.app.data.model.M16ShelterService
import com.comunidapp.app.data.model.M16ShelterVerificationFilter
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M16ShelterDetailViewModel
import com.comunidapp.app.viewmodel.M16ShelterManageViewModel
import com.comunidapp.app.viewmodel.M16ShelterManageDraft
import com.comunidapp.app.viewmodel.M16ShelterManageUiState
import com.comunidapp.app.data.model.M16ShelterOperationsFilter
import com.comunidapp.app.data.model.M16ShelterPetOperationalItem
import com.comunidapp.app.viewmodel.M16ShelterOperationsUiState
import com.comunidapp.app.viewmodel.M16SheltersListUiState
import com.comunidapp.app.viewmodel.M16SheltersListViewModel
import com.comunidapp.app.viewmodel.m16ContactTypeLabel
import com.comunidapp.app.viewmodel.m16DayLabel

@Composable
fun M16SheltersListScreen(
    onNavigateBack: () -> Unit,
    onShelterClick: (String) -> Unit,
    onManage: () -> Unit,
    viewModel: M16SheltersListViewModel = viewModel(factory = M16SheltersListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var query by remember(filter.query) { mutableStateOf(filter.query) }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Refugios",
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
                "Directorio público M16 — sin datos personales.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setQuery(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar refugio") },
                singleLine = true
            )
            M16ListFilterRow(
                filter = filter,
                onOperational = viewModel::setOperationalStatus,
                onVerification = viewModel::setVerificationFilter,
                onService = viewModel::setService,
                onSpecies = viewModel::setSpecies,
                onClear = viewModel::clearFilters
            )
            Button(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                Text("Administrar refugio (mock)")
            }
            when (val s = state) {
                M16SheltersListUiState.Loading -> LoadingState()
                M16SheltersListUiState.Empty -> EmptyState(
                    title = "Sin refugios",
                    message = "No hay refugios publicados que coincidan."
                )
                is M16SheltersListUiState.Error -> ErrorState(message = s.message)
                is M16SheltersListUiState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.items, key = { it.id }) { item ->
                        M16PublicShelterCard(item = item, onClick = { onShelterClick(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun M16ListFilterRow(
    filter: com.comunidapp.app.data.model.M16ShelterSearchFilter,
    onOperational: (M16ShelterOperationalStatus?) -> Unit,
    onVerification: (M16ShelterVerificationFilter) -> Unit,
    onService: (M16ShelterService?) -> Unit,
    onSpecies: (String?) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Estado operativo", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter.operationalStatus == null,
                onClick = { onOperational(null) },
                label = { Text("Activos y pausados") }
            )
            FilterChip(
                selected = filter.operationalStatus == M16ShelterOperationalStatus.ACTIVE,
                onClick = {
                    onOperational(
                        if (filter.operationalStatus == M16ShelterOperationalStatus.ACTIVE) null
                        else M16ShelterOperationalStatus.ACTIVE
                    )
                },
                label = { Text("Activos") }
            )
            FilterChip(
                selected = filter.operationalStatus == M16ShelterOperationalStatus.PAUSED,
                onClick = {
                    onOperational(
                        if (filter.operationalStatus == M16ShelterOperationalStatus.PAUSED) null
                        else M16ShelterOperationalStatus.PAUSED
                    )
                },
                label = { Text("Pausados") }
            )
            FilterChip(
                selected = filter.operationalStatus == M16ShelterOperationalStatus.PERMANENTLY_CLOSED,
                onClick = {
                    onOperational(
                        if (filter.operationalStatus == M16ShelterOperationalStatus.PERMANENTLY_CLOSED) {
                            null
                        } else {
                            M16ShelterOperationalStatus.PERMANENTLY_CLOSED
                        }
                    )
                },
                label = { Text("Cerrados") }
            )
        }
        Text("Verificación", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter.verificationFilter == M16ShelterVerificationFilter.ALL,
                onClick = { onVerification(M16ShelterVerificationFilter.ALL) },
                label = { Text("Todos") }
            )
            FilterChip(
                selected = filter.verificationFilter == M16ShelterVerificationFilter.VERIFIED_ONLY,
                onClick = { onVerification(M16ShelterVerificationFilter.VERIFIED_ONLY) },
                label = { Text("Verificados") }
            )
            FilterChip(
                selected = filter.verificationFilter == M16ShelterVerificationFilter.UNVERIFIED_OR_PENDING,
                onClick = { onVerification(M16ShelterVerificationFilter.UNVERIFIED_OR_PENDING) },
                label = { Text("No verificados") }
            )
        }
        Text("Servicio", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter.service == null,
                onClick = { onService(null) },
                label = { Text("Todos") }
            )
            M16ShelterService.entries.forEach { service ->
                FilterChip(
                    selected = filter.service == service,
                    onClick = { onService(if (filter.service == service) null else service) },
                    label = { Text(service.name) }
                )
            }
        }
        OutlinedTextField(
            value = filter.species.orEmpty(),
            onValueChange = { onSpecies(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Especie (DOG, CAT…)") },
            singleLine = true
        )
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text("Limpiar filtros")
        }
    }
}

@Composable
private fun M16PublicShelterCard(item: M16PublicShelter, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(item.displayName, fontWeight = FontWeight.Bold)
            Text("${item.publicZoneText} · ${item.operationalStatus}")
            Text("Servicios: ${item.services.joinToString { it.name }}")
            Text("Disponibilidad: ${item.availability}")
            Text("Verificación: ${item.verificationStatus}")
        }
    }
}

@Composable
fun M16ShelterDetailScreen(
    shelterId: String,
    onNavigateBack: () -> Unit,
    viewModel: M16ShelterDetailViewModel = viewModel(
        factory = M16ShelterDetailViewModel.factory(shelterId)
    )
) {
    val shelter by viewModel.shelter.collectAsState()
    val message by viewModel.message.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Detalle del refugio",
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
                .verticalScroll(rememberScrollState())
        ) {
            when {
                message != null -> ErrorState(message = message!!)
                shelter == null -> LoadingState()
                else -> M16PublicShelterDetailContent(shelter!!)
            }
        }
    }
}

@Composable
private fun M16PublicShelterDetailContent(s: M16PublicShelter) {
    if (s.operationalStatus == M16ShelterOperationalStatus.PERMANENTLY_CLOSED) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                "Este refugio cerró permanentemente.",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
    }
    if (s.operationalStatus == M16ShelterOperationalStatus.PAUSED) {
        Text(
            "Refugio pausado temporalmente.",
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
    }
    Text(s.displayName, style = MaterialTheme.typography.headlineSmall)
    Text("Zona: ${s.publicZoneText}")
    s.description?.let { Text(it) }
    Spacer(Modifier.height(8.dp))
    Text("Estado: ${s.operationalStatus} · Verificación: ${s.verificationStatus}")
    Text("Capacidad agregada: ${s.freeSlotsApproximate} libres de ${s.totalCapacity}")
    Text("Especies: ${s.acceptedSpecies.joinToString().ifBlank { "—" }}")
    Text("Servicios: ${s.services.joinToString { it.name }.ifBlank { "—" }}")
    if (s.needs.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Necesidades", fontWeight = FontWeight.Bold)
        s.needs.forEach { Text("· ${it.category}: ${it.description}") }
    }
    Spacer(Modifier.height(12.dp))
    Text("Horarios de atención", fontWeight = FontWeight.Bold)
    M16OpeningHoursReadOnly(s.openingHours)
    if (s.publicContacts.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Contacto público", fontWeight = FontWeight.Bold)
        s.publicContacts.forEach { contact ->
            Text("${m16ContactTypeLabel(contact.type)}: ${contact.value}")
        }
    }
}

@Composable
private fun M16OpeningHoursReadOnly(hours: M16OpeningHours) {
    if (hours.periods.isEmpty()) {
        Text("Sin horarios publicados.")
        return
    }
    val grouped = hours.periods.groupBy { it.dayOfWeek }.toSortedMap()
    grouped.forEach { (day, periods) ->
        val label = periods.joinToString("; ") { period ->
            if (period.closed) "Cerrado"
            else "${period.openTime.orEmpty()} – ${period.closeTime.orEmpty()}"
        }
        Text("${m16DayLabel(day)}: $label")
    }
    Text(
        "Zona horaria: ${hours.zoneIdName}",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
fun M16ShelterManageScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPet: (String) -> Unit = {},
    onNavigateToAdoption: (String) -> Unit = {},
    onNavigateToFoster: (String) -> Unit = {},
    viewModel: M16ShelterManageViewModel = viewModel(factory = M16ShelterManageViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    val orgId by viewModel.organizationId.collectAsState()
    val operationsState by viewModel.operationsState.collectAsState()
    val operationsFilter by viewModel.operationsFilter.collectAsState()
    var showCloseConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearFeedback()
        }
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("Cerrar permanentemente este refugio") },
            text = {
                Text(
                    "Esta operación es terminal. El refugio no podrá reactivarse " +
                        "mediante acciones normales de M16."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCloseConfirm = false
                    viewModel.closePermanently()
                }) { Text("Confirmar cierre") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Administrar refugio",
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            feedback?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("Organización mock", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                M16MockOrganizations.MANAGE_ORGANIZATION_IDS.forEach { id ->
                    FilterChip(
                        selected = orgId == id,
                        onClick = { viewModel.selectOrganization(id) },
                        label = { Text(id.removePrefix("org_")) }
                    )
                }
            }
            when (val state = uiState) {
                M16ShelterManageUiState.Loading -> LoadingState()
                is M16ShelterManageUiState.Error -> ErrorState(message = state.message)
                M16ShelterManageUiState.PermissionDenied -> ErrorState(
                    message = "No tenés permiso para administrar esta organización."
                )
                is M16ShelterManageUiState.NoProfile -> M16NoProfileContent(
                    draft = draft,
                    saving = state.saving,
                    onDraftChange = viewModel::updateDraft,
                    onCreate = viewModel::createProfile
                )
                is M16ShelterManageUiState.ProfileContent -> M16ProfileManageContent(
                    profile = state.profile,
                    draft = draft,
                    saving = state.saving,
                    operationsState = operationsState,
                    operationsFilter = operationsFilter,
                    onOperationsFilterChange = viewModel::setOperationsFilter,
                    onRefreshOperations = { viewModel.refreshOperations(state.profile.id) },
                    onSyncOccupancySnapshot = { viewModel.syncOccupancySnapshot() },
                    onNavigateToPet = onNavigateToPet,
                    onNavigateToAdoption = onNavigateToAdoption,
                    onNavigateToFoster = onNavigateToFoster,
                    onDraftChange = viewModel::updateDraft,
                    onSavePublic = viewModel::savePublicData,
                    onSaveCapacity = viewModel::saveCapacity,
                    onSaveHours = viewModel::saveOpeningHours,
                    onSaveContacts = viewModel::saveContacts,
                    onSaveServices = viewModel::saveServices,
                    onSaveNeeds = viewModel::saveNeeds,
                    onPublish = viewModel::publish,
                    onPause = viewModel::pause,
                    onActivate = viewModel::activate,
                    onRequestVerification = viewModel::requestVerification,
                    onClosePermanently = { showCloseConfirm = true }
                )
            }
        }
    }
}

@Composable
private fun M16NoProfileContent(
    draft: M16ShelterManageDraft,
    saving: Boolean,
    onDraftChange: ((M16ShelterManageDraft) -> M16ShelterManageDraft) -> Unit,
    onCreate: () -> Unit
) {
    Text("Sin perfil M16 para esta organización elegible.", fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = draft.displayName,
        onValueChange = { v -> onDraftChange { it.copy(displayName = v) } },
        label = { Text("Nombre público") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = draft.publicZoneText,
        onValueChange = { v -> onDraftChange { it.copy(publicZoneText = v) } },
        label = { Text("Zona pública") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = draft.totalCapacity,
        onValueChange = { v -> onDraftChange { it.copy(totalCapacity = v) } },
        label = { Text("Capacidad total") },
        modifier = Modifier.fillMaxWidth()
    )
    Button(onClick = onCreate, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
        Text(if (saving) "Creando…" else "Crear perfil M16")
    }
}

@Composable
private fun M16ProfileManageContent(
    profile: com.comunidapp.app.data.model.M16ShelterProfile,
    draft: M16ShelterManageDraft,
    saving: Boolean,
    operationsState: M16ShelterOperationsUiState,
    operationsFilter: com.comunidapp.app.data.model.M16ShelterOperationsFilter,
    onOperationsFilterChange: (com.comunidapp.app.data.model.M16ShelterOperationsFilter) -> Unit,
    onRefreshOperations: () -> Unit,
    onSyncOccupancySnapshot: () -> Unit,
    onNavigateToPet: (String) -> Unit,
    onNavigateToAdoption: (String) -> Unit,
    onNavigateToFoster: (String) -> Unit,
    onDraftChange: ((M16ShelterManageDraft) -> M16ShelterManageDraft) -> Unit,
    onSavePublic: () -> Unit,
    onSaveCapacity: () -> Unit,
    onSaveHours: () -> Unit,
    onSaveContacts: () -> Unit,
    onSaveServices: () -> Unit,
    onSaveNeeds: () -> Unit,
    onPublish: () -> Unit,
    onPause: () -> Unit,
    onActivate: () -> Unit,
    onRequestVerification: () -> Unit,
    onClosePermanently: () -> Unit
) {
    val isTerminal = profile.operationalStatus == M16ShelterOperationalStatus.PERMANENTLY_CLOSED
    Text(profile.displayName, fontWeight = FontWeight.Bold)
    Text("Operativo: ${profile.operationalStatus}")
    Text("Publicación: ${profile.publicationStatus}")
    Text("Verificación: ${profile.verificationStatus}")
    if (profile.verificationStatus == com.comunidapp.app.data.model.M16ShelterVerificationStatus.PENDING) {
        Text(
            "Verificación pendiente — aprobación final vía administración M04.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    Text("Datos públicos", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    OutlinedTextField(
        value = draft.displayName,
        onValueChange = { v -> onDraftChange { it.copy(displayName = v) } },
        label = { Text("Nombre público") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isTerminal
    )
    OutlinedTextField(
        value = draft.description,
        onValueChange = { v -> onDraftChange { it.copy(description = v) } },
        label = { Text("Descripción") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isTerminal
    )
    OutlinedTextField(
        value = draft.publicZoneText,
        onValueChange = { v -> onDraftChange { it.copy(publicZoneText = v) } },
        label = { Text("Zona pública") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isTerminal
    )
    Button(onClick = onSavePublic, enabled = !saving && !isTerminal, modifier = Modifier.fillMaxWidth()) {
        Text("Guardar datos públicos")
    }

    Text("Capacidad", fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = draft.totalCapacity,
        onValueChange = { v -> onDraftChange { it.copy(totalCapacity = v) } },
        label = { Text("Capacidad total") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isTerminal
    )
    OutlinedTextField(
        value = draft.currentOccupancy,
        onValueChange = { v -> onDraftChange { it.copy(currentOccupancy = v) } },
        label = { Text("Ocupación manual (snapshot)") },
        supportingText = { Text("La UI operativa usa ocupación calculada desde M08/M11.") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isTerminal
    )
    Button(onClick = onSaveCapacity, enabled = !saving && !isTerminal, modifier = Modifier.fillMaxWidth()) {
        Text("Guardar capacidad")
    }

    Text("Horarios (HH:mm)", fontWeight = FontWeight.Bold)
    (1..7).forEach { day ->
        val period = draft.openingHours.periods.find { it.dayOfWeek == day }
            ?: M16OpeningPeriod(dayOfWeek = day, closed = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(m16DayLabel(day), modifier = Modifier.weight(0.35f))
            OutlinedTextField(
                value = if (period.closed) "" else period.openTime.orEmpty(),
                onValueChange = { v ->
                    onDraftChange { d ->
                        d.copy(openingHours = d.openingHours.updateDay(day, open = v, close = period.closeTime))
                    }
                },
                label = { Text("Abre") },
                modifier = Modifier.weight(0.3f),
                enabled = !isTerminal && !period.closed
            )
            OutlinedTextField(
                value = if (period.closed) "" else period.closeTime.orEmpty(),
                onValueChange = { v ->
                    onDraftChange { d ->
                        d.copy(openingHours = d.openingHours.updateDay(day, open = period.openTime, close = v))
                    }
                },
                label = { Text("Cierra") },
                modifier = Modifier.weight(0.3f),
                enabled = !isTerminal && !period.closed
            )
        }
        FilterChip(
            selected = period.closed,
            onClick = {
                if (!isTerminal) {
                    onDraftChange { d ->
                        d.copy(openingHours = d.openingHours.toggleClosed(day))
                    }
                }
            },
            label = { Text(if (period.closed) "Cerrado" else "Abierto") },
            enabled = !isTerminal
        )
    }
    Button(onClick = onSaveHours, enabled = !saving && !isTerminal, modifier = Modifier.fillMaxWidth()) {
        Text("Guardar horarios")
    }

    Text("Contactos públicos declarados", fontWeight = FontWeight.Bold)
    draft.contacts.forEachIndexed { index, contact ->
        Text("${m16ContactTypeLabel(contact.type)}: ${contact.value}")
        if (!isTerminal) {
            TextButton(onClick = {
                onDraftChange { d -> d.copy(contacts = d.contacts.filterIndexed { i, _ -> i != index }) }
            }) { Text("Eliminar contacto") }
        }
    }
    if (!isTerminal) {
        var newContactValue by remember { mutableStateOf("") }
        OutlinedTextField(
            value = newContactValue,
            onValueChange = { newContactValue = it },
            label = { Text("Nuevo email institucional (@)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (newContactValue.isNotBlank()) {
                    onDraftChange { d ->
                        d.copy(
                            contacts = d.contacts + M16PublicContactChannel(
                                type = M16PublicContactChannelType.INSTITUTIONAL_EMAIL,
                                value = newContactValue.trim()
                            )
                        )
                    }
                    newContactValue = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Agregar contacto público") }
    }
    Button(onClick = onSaveContacts, enabled = !saving && !isTerminal, modifier = Modifier.fillMaxWidth()) {
        Text("Guardar contactos")
    }

    Text("Servicios", fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        M16ShelterService.entries.forEach { service ->
            FilterChip(
                selected = draft.services.contains(service),
                onClick = {
                    if (!isTerminal) {
                        onDraftChange { d ->
                            d.copy(
                                services = if (service in d.services) d.services - service else d.services + service
                            )
                        }
                    }
                },
                label = { Text(service.name) },
                enabled = !isTerminal
            )
        }
    }
    Button(onClick = onSaveServices, enabled = !saving && !isTerminal, modifier = Modifier.fillMaxWidth()) {
        Text("Guardar servicios")
    }

    Text("Necesidades (categoría|descripción por línea)", fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = draft.needsText,
        onValueChange = { v -> onDraftChange { it.copy(needsText = v) } },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isTerminal
    )
    Button(onClick = onSaveNeeds, enabled = !saving && !isTerminal, modifier = Modifier.fillMaxWidth()) {
        Text("Guardar necesidades")
    }

    if (!isTerminal) {
        Text("Acciones operativas", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onPublish, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Publicar") }
        Button(onClick = onPause, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Pausar") }
        Button(onClick = onActivate, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Reactivar") }
        Button(onClick = onRequestVerification, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text("Solicitar verificación")
        }
        OutlinedButton(onClick = onClosePermanently, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text("Cerrar permanentemente")
        }
    } else {
        Text(
            "Este refugio está cerrado permanentemente. No hay acciones operativas disponibles.",
            color = MaterialTheme.colorScheme.error
        )
        Button(onClick = onActivate, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text("Intentar reactivar (debe fallar)")
        }
    }

    M16OperationsSection(
        operationsState = operationsState,
        operationsFilter = operationsFilter,
        onFilterChange = onOperationsFilterChange,
        onRefresh = onRefreshOperations,
        onSyncOccupancySnapshot = onSyncOccupancySnapshot,
        onNavigateToPet = onNavigateToPet,
        onNavigateToAdoption = onNavigateToAdoption,
        onNavigateToFoster = onNavigateToFoster
    )
}

private fun M16OpeningHours.updateDay(day: Int, open: String?, close: String?): M16OpeningHours {
    val others = periods.filterNot { it.dayOfWeek == day }
    val updated = M16OpeningPeriod(
        dayOfWeek = day,
        closed = false,
        openTime = open?.ifBlank { null },
        closeTime = close?.ifBlank { null }
    )
    return copy(periods = others + updated)
}

private fun M16OpeningHours.toggleClosed(day: Int): M16OpeningHours {
    val existing = periods.find { it.dayOfWeek == day }
    val others = periods.filterNot { it.dayOfWeek == day }
    val toggled = if (existing?.closed == true) {
        M16OpeningPeriod(dayOfWeek = day, openTime = "09:00", closeTime = "18:00")
    } else {
        M16OpeningPeriod(dayOfWeek = day, closed = true)
    }
    return copy(periods = others + toggled)
}

@Composable
private fun M16OperationsSection(
    operationsState: M16ShelterOperationsUiState,
    operationsFilter: M16ShelterOperationsFilter,
    onFilterChange: (M16ShelterOperationsFilter) -> Unit,
    onRefresh: () -> Unit,
    onSyncOccupancySnapshot: () -> Unit,
    onNavigateToPet: (String) -> Unit,
    onNavigateToAdoption: (String) -> Unit,
    onNavigateToFoster: (String) -> Unit
) {
    Text("Operación del refugio", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        M16ShelterOperationsFilter.entries.forEach { filter ->
            FilterChip(
                selected = operationsFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.name.lowercase().replace('_', ' ')) }
            )
        }
    }
    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
        Text("Actualizar operación")
    }
    when (operationsState) {
        M16ShelterOperationsUiState.Loading -> LoadingState()
        M16ShelterOperationsUiState.PermissionDenied -> ErrorState(
            message = "Sin permiso para ver operación interna."
        )
        is M16ShelterOperationsUiState.Error -> ErrorState(message = operationsState.message)
        M16ShelterOperationsUiState.Empty -> Text("Sin mascotas operativas vinculadas.")
        is M16ShelterOperationsUiState.Partial -> {
            Text(
                buildPartialSourcesMessage(operationsState.summary.partialFlags),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall
            )
            M16OperationsSummaryBody(
                operationsState.summary,
                onNavigateToPet,
                onNavigateToAdoption,
                onNavigateToFoster,
                onSyncOccupancySnapshot
            )
        }
        is M16ShelterOperationsUiState.Content -> {
            M16OperationsSummaryBody(
                operationsState.summary,
                onNavigateToPet,
                onNavigateToAdoption,
                onNavigateToFoster,
                onSyncOccupancySnapshot
            )
        }
    }
}

private fun buildPartialSourcesMessage(
    flags: com.comunidapp.app.data.model.M16ShelterOperationsPartialFlags
): String {
    val parts = mutableListOf<String>()
    if (flags.petsSourceUnavailable) parts += "M08"
    if (flags.adoptionsSourceUnavailable) parts += "M09"
    if (flags.fosterSourceUnavailable) parts += "M15"
    if (flags.shelterOpsSourceUnavailable) parts += "M11"
    if (flags.adoptionCompletionDatesUnavailable) parts += "fechas adopción"
    if (flags.fosterOrgQueryLimited) parts += "M15 org (RLS limitada)"
    return "Datos parciales — fuentes pendientes: ${parts.joinToString(", ")}."
}

@Composable
private fun M16OperationsSummaryBody(
    summary: com.comunidapp.app.data.model.M16ShelterOperationsSummary,
    onNavigateToPet: (String) -> Unit,
    onNavigateToAdoption: (String) -> Unit,
    onNavigateToFoster: (String) -> Unit,
    onSyncOccupancySnapshot: () -> Unit
) {
    val b = summary.breakdown
    Text("Capacidad total: ${b.totalCapacity}")
    Text("Ocupación física: ${b.physicalOccupancy}")
    Text("Cupos reservados (sin ingreso): ${b.reservedCapacity}")
    Text("Capacidad comprometida: ${b.committedCapacity}")
    Text("Cupos disponibles: ${b.availableCapacity}")
    if (b.isOverCapacity) {
        Text(
            "Exceso de capacidad: ${b.overCapacityBy}",
            color = MaterialTheme.colorScheme.error
        )
    }
    Text("En tránsito activo: ${b.inActiveFosterCount}")
    Text("Adopción activa: ${b.activeAdoptionCount}")
    Text("Adoptadas últimos ${com.comunidapp.app.domain.m16.M16_RECENT_ADOPTION_WINDOW_DAYS} días: ${b.recentlyAdoptedCount}")
    Text("Inconsistencias: ${summary.pets.count { it.status == com.comunidapp.app.data.model.M16ShelterPetOperationalStatus.INCONSISTENT }}")
    b.configuredOccupancySnapshot?.let {
        Text("Snapshot manual M16: $it", style = MaterialTheme.typography.bodySmall)
    }
    if (b.snapshotDiffersFromCalculated) {
        Text(
            "El snapshot manual difiere de la ocupación física calculada.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = onSyncOccupancySnapshot, modifier = Modifier.fillMaxWidth()) {
            Text("Actualizar snapshot de ocupación")
        }
    }
    b.warnings.forEach { w ->
        Text("• $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    summary.pets.forEach { item ->
        M16OperationalPetRow(item, onNavigateToPet, onNavigateToAdoption, onNavigateToFoster)
    }
}

@Composable
private fun M16OperationalPetRow(
    item: M16ShelterPetOperationalItem,
    onNavigateToPet: (String) -> Unit,
    onNavigateToAdoption: (String) -> Unit,
    onNavigateToFoster: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onNavigateToPet(item.petId) }
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(item.displayName, fontWeight = FontWeight.SemiBold)
            Text("${item.species} · ${item.status.name}")
            if (item.reservedSlot) {
                Text("Cupo reservado (sin ingreso físico)", style = MaterialTheme.typography.bodySmall)
            }
            item.adoptionStatusLabel?.let { Text("Adopción: $it", style = MaterialTheme.typography.bodySmall) }
            item.fosterStatusLabel?.let { Text("Tránsito: $it", style = MaterialTheme.typography.bodySmall) }
            item.warning?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onNavigateToPet(item.petId) }) { Text("M08") }
                item.adoptionPostId?.let { id ->
                    TextButton(onClick = { onNavigateToAdoption(id) }) { Text("M09") }
                }
                item.fosterPlacementId?.let { id ->
                    TextButton(onClick = { onNavigateToFoster(id) }) { Text("M15") }
                }
            }
        }
    }
}
