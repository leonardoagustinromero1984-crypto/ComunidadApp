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
import androidx.compose.material3.MaterialTheme
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
import com.comunidapp.app.data.model.visibleLabel
import com.comunidapp.app.domain.verification.VerificationDisplayPolicy
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
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
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.theme.LeoDimens

@Composable
fun M16SheltersListScreen(
    onNavigateBack: () -> Unit,
    onShelterClick: (String) -> Unit,
    onManage: (() -> Unit)? = null,
    viewModel: M16SheltersListViewModel = viewModel(factory = M16SheltersListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var query by remember(filter.query) { mutableStateOf(filter.query) }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
            com.comunidapp.app.ui.components.ContextualFirstVisitHelp(
                helpId = com.comunidapp.app.domain.onboarding.ContextualHelpId.SHELTERS,
                message = com.comunidapp.app.ui.components.ContextualHelpMessages.SHELTERS
            )
            Text(
                "Directorio público de refugios — sin datos personales.",
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
            onManage?.let { manage ->
                LeoPrimaryButton(
                    text = "Administrar refugio",
                    onClick = manage
                )
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
            LeoFilterChip(
                label = "Activos y pausados",
                selected = filter.operationalStatus == null,
                onClick = { onOperational(null) }
            )
            LeoFilterChip(
                label = "Activos",
                selected = filter.operationalStatus == M16ShelterOperationalStatus.ACTIVE,
                onClick = {
                    onOperational(
                        if (filter.operationalStatus == M16ShelterOperationalStatus.ACTIVE) null
                        else M16ShelterOperationalStatus.ACTIVE
                    )
                }
            )
            LeoFilterChip(
                label = "Pausados",
                selected = filter.operationalStatus == M16ShelterOperationalStatus.PAUSED,
                onClick = {
                    onOperational(
                        if (filter.operationalStatus == M16ShelterOperationalStatus.PAUSED) null
                        else M16ShelterOperationalStatus.PAUSED
                    )
                }
            )
            LeoFilterChip(
                label = "Cerrados",
                selected = filter.operationalStatus == M16ShelterOperationalStatus.PERMANENTLY_CLOSED,
                onClick = {
                    onOperational(
                        if (filter.operationalStatus == M16ShelterOperationalStatus.PERMANENTLY_CLOSED) {
                            null
                        } else {
                            M16ShelterOperationalStatus.PERMANENTLY_CLOSED
                        }
                    )
                }
            )
        }
        if (VerificationDisplayPolicy.FILTERS_VISIBLE) {
        Text("Verificación", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeoFilterChip(
                label = "Todos",
                selected = filter.verificationFilter == M16ShelterVerificationFilter.ALL,
                onClick = { onVerification(M16ShelterVerificationFilter.ALL) }
            )
            LeoFilterChip(
                label = "Verificados",
                selected = filter.verificationFilter == M16ShelterVerificationFilter.VERIFIED_ONLY,
                onClick = { onVerification(M16ShelterVerificationFilter.VERIFIED_ONLY) }
            )
            LeoFilterChip(
                label = "No verificados",
                selected = filter.verificationFilter == M16ShelterVerificationFilter.UNVERIFIED_OR_PENDING,
                onClick = { onVerification(M16ShelterVerificationFilter.UNVERIFIED_OR_PENDING) }
            )
        }
        }
        Text("Servicio", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LeoFilterChip(
                label = "Todos",
                selected = filter.service == null,
                onClick = { onService(null) }
            )
            M16ShelterService.entries.forEach { service ->
                LeoFilterChip(
                    label = service.visibleLabel(),
                    selected = filter.service == service,
                    onClick = { onService(if (filter.service == service) null else service) }
                )
            }
        }
        OutlinedTextField(
            value = filter.species.orEmpty(),
            onValueChange = { onSpecies(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Especie") },
            singleLine = true
        )
        LeoOutlinedButton(
            text = "Limpiar filtros",
            onClick = onClear
        )
    }
}

@Composable
private fun M16PublicShelterCard(item: M16PublicShelter, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(LeoDimens.SpaceCompact)) {
            Text(item.displayName, fontWeight = FontWeight.Bold)
            Text("${item.publicZoneText} · ${item.operationalStatus}")
            Text("Servicios: ${item.services.joinToString { it.name }}")
            Text("Disponibilidad: ${item.availability}")
            Text("Verificación: ${item.verificationStatus}")
        }
        LeoHairline()
    }
}

@Composable
fun M16ShelterDetailScreen(
    shelterId: String,
    onNavigateBack: () -> Unit,
    onM17Hub: (() -> Unit)? = null,
    onM18Events: (() -> Unit)? = null,
    viewModel: M16ShelterDetailViewModel = viewModel(
        factory = M16ShelterDetailViewModel.factory(shelterId)
    )
) {
    val shelter by viewModel.shelter.collectAsState()
    val message by viewModel.message.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                else -> M16PublicShelterDetailContent(
                    shelter!!,
                    onM17Hub = onM17Hub,
                    onM18Events = onM18Events
                )
            }
        }
    }
}

@Composable
private fun M16PublicShelterDetailContent(
    s: M16PublicShelter,
    onM17Hub: (() -> Unit)? = null,
    onM18Events: (() -> Unit)? = null
) {
    if (s.operationalStatus == M16ShelterOperationalStatus.PERMANENTLY_CLOSED) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Este refugio cerró permanentemente.",
                modifier = Modifier.padding(LeoDimens.SpaceCompact),
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            LeoHairline()
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
    onM17Hub?.let { hub ->
        Spacer(Modifier.height(16.dp))
        LeoOutlinedButton(
            text = "Campañas",
            onClick = hub
        )
        Spacer(Modifier.height(8.dp))
        LeoOutlinedButton(
            text = "Voluntariado",
            onClick = hub
        )
        Spacer(Modifier.height(8.dp))
        LeoOutlinedButton(
            text = "Donar / Ayudar",
            onClick = hub
        )
        Text(
            "LeoVer no procesa pagos. Si el refugio publicó un alias o CBU, la transferencia se hace por fuera. 0% de comisión.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    onM18Events?.let { events ->
        Spacer(Modifier.height(8.dp))
        LeoOutlinedButton(
            text = "Eventos",
            onClick = events
        )
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
                        "mediante acciones normales del refugio."
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                    LeoFilterChip(
                        label = id.removePrefix("org_"),
                        selected = orgId == id,
                        onClick = { viewModel.selectOrganization(id) }
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
    Text("Sin perfil de refugio para esta organización elegible.", fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = draft.displayName,
        onValueChange = { v -> onDraftChange { it.copy(displayName = v) } },
        label = { Text("Nombre público") },
        modifier = Modifier.fillMaxWidth()
    )
    V2LocationStringPicker(
        value = draft.publicZoneText,
        onValueChange = { v -> onDraftChange { it.copy(publicZoneText = v) } }
    )
    OutlinedTextField(
        value = draft.totalCapacity,
        onValueChange = { v -> onDraftChange { it.copy(totalCapacity = v) } },
        label = { Text("Capacidad total") },
        modifier = Modifier.fillMaxWidth()
    )
    LeoPrimaryButton(
        text = if (saving) "Creando…" else "Crear perfil de refugio",
        onClick = onCreate,
        enabled = !saving
    )
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
    V2LocationStringPicker(
        value = draft.publicZoneText,
        onValueChange = { v -> onDraftChange { it.copy(publicZoneText = v) } },
        enabled = !isTerminal
    )
    LeoPrimaryButton(
        text = "Guardar datos públicos",
        onClick = onSavePublic,
        enabled = !saving && !isTerminal
    )

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
        supportingText = { Text("La UI operativa usa ocupación calculada desde mascotas y operaciones de refugio.") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isTerminal
    )
    LeoPrimaryButton(
        text = "Guardar capacidad",
        onClick = onSaveCapacity,
        enabled = !saving && !isTerminal
    )

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
        LeoFilterChip(
            label = if (period.closed) "Cerrado" else "Abierto",
            selected = period.closed,
            onClick = {
                if (!isTerminal) {
                    onDraftChange { d ->
                        d.copy(openingHours = d.openingHours.toggleClosed(day))
                    }
                }
            }
        )
    }
    LeoPrimaryButton(
        text = "Guardar horarios",
        onClick = onSaveHours,
        enabled = !saving && !isTerminal
    )

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
        LeoPrimaryButton(
            text = "Agregar contacto público",
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
            }
        )
    }
    LeoPrimaryButton(
        text = "Guardar contactos",
        onClick = onSaveContacts,
        enabled = !saving && !isTerminal
    )

    Text("Servicios", fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        M16ShelterService.entries.forEach { service ->
            LeoFilterChip(
                label = service.visibleLabel(),
                selected = draft.services.contains(service),
                onClick = {
                    if (!isTerminal) {
                        onDraftChange { d ->
                            d.copy(
                                services = if (service in d.services) d.services - service else d.services + service
                            )
                        }
                    }
                }
            )
        }
    }
    LeoPrimaryButton(
        text = "Guardar servicios",
        onClick = onSaveServices,
        enabled = !saving && !isTerminal
    )

    Text("Necesidades (categoría|descripción por línea)", fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = draft.needsText,
        onValueChange = { v -> onDraftChange { it.copy(needsText = v) } },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isTerminal
    )
    LeoPrimaryButton(
        text = "Guardar necesidades",
        onClick = onSaveNeeds,
        enabled = !saving && !isTerminal
    )

    if (!isTerminal) {
        Text("Acciones operativas", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        LeoPrimaryButton(
            text = "Publicar",
            onClick = onPublish,
            enabled = !saving
        )
        LeoPrimaryButton(
            text = "Pausar",
            onClick = onPause,
            enabled = !saving
        )
        LeoPrimaryButton(
            text = "Reactivar",
            onClick = onActivate,
            enabled = !saving
        )
        LeoPrimaryButton(
            text = "Solicitar verificación",
            onClick = onRequestVerification,
            enabled = !saving
        )
        LeoOutlinedButton(
            text = "Cerrar permanentemente",
            onClick = onClosePermanently,
            enabled = !saving
        )
    } else {
        Text(
            "Este refugio está cerrado permanentemente. No hay acciones operativas disponibles.",
            color = MaterialTheme.colorScheme.error
        )
        LeoPrimaryButton(
            text = "Intentar reactivar (debe fallar)",
            onClick = onActivate,
            enabled = !saving
        )
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
            LeoFilterChip(
                label = filter.name.lowercase().replace('_', ' '),
                selected = operationsFilter == filter,
                onClick = { onFilterChange(filter) }
            )
        }
    }
    LeoOutlinedButton(
        text = "Actualizar operación",
        onClick = onRefresh
    )
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
    if (flags.petsSourceUnavailable) parts += "mascotas"
    if (flags.adoptionsSourceUnavailable) parts += "adopciones"
    if (flags.fosterSourceUnavailable) parts += "Tránsito"
    if (flags.shelterOpsSourceUnavailable) parts += "Operaciones de refugio"
    if (flags.adoptionCompletionDatesUnavailable) parts += "fechas adopción"
    if (flags.fosterOrgQueryLimited) parts += "Tránsito (permisos limitados)"
    return when {
        flags.fosterOrgQueryLimited && parts.size == 1 ->
            "Los datos de tránsito pueden estar incompletos por permisos del entorno remoto."
        parts.isEmpty() -> "Datos parciales — alguna fuente no respondió."
        else -> "Datos parciales — fuentes pendientes: ${parts.joinToString(", ")}."
    }
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
    Text(
        if (b.recentAdoptionsApproximate) {
            "Adoptadas recientemente — estimación (${com.comunidapp.app.domain.m16.M16_RECENT_ADOPTION_WINDOW_DAYS} días): ${b.recentlyAdoptedCount}"
        } else {
            "Adoptadas últimos ${com.comunidapp.app.domain.m16.M16_RECENT_ADOPTION_WINDOW_DAYS} días: ${b.recentlyAdoptedCount}"
        },
        style = if (b.recentAdoptionsApproximate) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    )
    if (b.recentAdoptionsApproximate) {
        Text(
            "Fecha aproximada; puede no coincidir con la adopción exacta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
    if (summary.partialFlags.fosterOrgQueryLimited) {
        Text(
            "Los datos de tránsito pueden estar incompletos por permisos del entorno remoto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
    Text("Inconsistencias: ${summary.pets.count { it.status == com.comunidapp.app.data.model.M16ShelterPetOperationalStatus.INCONSISTENT }}")
    b.configuredOccupancySnapshot?.let {
        Text("Snapshot manual de ocupación: $it", style = MaterialTheme.typography.bodySmall)
    }
    if (b.snapshotDiffersFromCalculated) {
        Text(
            "El snapshot manual difiere de la ocupación física calculada.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        LeoOutlinedButton(
            text = "Actualizar snapshot de ocupación",
            onClick = onSyncOccupancySnapshot
        )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToPet(item.petId) }
    ) {
        Column(Modifier.padding(LeoDimens.SpaceCompact)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
                TextButton(onClick = { onNavigateToPet(item.petId) }) { Text("Mascota") }
                item.adoptionPostId?.let { id ->
                    TextButton(onClick = { onNavigateToAdoption(id) }) { Text("Adopción") }
                }
                item.fosterPlacementId?.let { id ->
                    TextButton(onClick = { onNavigateToFoster(id) }) { Text("Tránsito") }
                }
            }
        }
        LeoHairline()
    }
}
