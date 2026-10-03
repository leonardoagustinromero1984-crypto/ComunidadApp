package com.comunidapp.app.ui.screens.m18

import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18EventType
import com.comunidapp.app.data.model.M18MockOrganizations
import com.comunidapp.app.data.model.M18PublicEvent
import com.comunidapp.app.data.model.M18RegistrationStatus
import com.comunidapp.app.domain.m18.EventPresentation
import com.comunidapp.app.ui.components.leo.LeoActiveFilter
import com.comunidapp.app.ui.components.leo.LeoFilterBar
import com.comunidapp.app.ui.components.leo.LeoFilterSheet
import com.comunidapp.app.ui.components.leo.LeoStatusBadge
import com.comunidapp.app.domain.m18.MyEventRegistration
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker
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
import com.comunidapp.app.viewmodel.M18MyEventsUiState
import com.comunidapp.app.viewmodel.M18MyEventsViewModel
import com.comunidapp.app.viewmodel.m18EventStatusLabel
import com.comunidapp.app.viewmodel.m18EventTypeLabel
import com.comunidapp.app.viewmodel.m18RegistrationStatusLabel
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTextField
import com.comunidapp.app.ui.theme.LeoDimens

@Composable
fun M18EventsListScreen(
    onNavigateBack: () -> Unit,
    onEventClick: (String) -> Unit,
    onManage: () -> Unit,
    onCreate: () -> Unit,
    canAdminister: Boolean = false,
    viewModel: M18EventsListViewModel = viewModel(factory = M18EventsListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var query by remember(filter.query) { mutableStateOf(filter.query) }
    var filtersOpen by remember { mutableStateOf(false) }
    var draftSpots by remember { mutableStateOf(false) }
    var draftCompleted by remember { mutableStateOf(false) }
    var draftType by remember { mutableStateOf<M18EventType?>(null) }
    val activeFilters = buildList {
        if (filter.withOpenSpotsOnly) add(LeoActiveFilter("spots", "Con cupos"))
        if (filter.completedOnly) add(LeoActiveFilter("completed", "Completados"))
        filter.type?.let { add(LeoActiveFilter("type", EventPresentation.eventType(it))) }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = EventPresentation.DISCOVER_TITLE, showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Próximos encuentros de la comunidad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            LeoFilterBar(
                onOpenFilters = {
                    draftSpots = filter.withOpenSpotsOnly
                    draftCompleted = filter.completedOnly
                    draftType = filter.type
                    filtersOpen = true
                },
                activeFilters = activeFilters,
                onRemoveFilter = { id ->
                    when (id) {
                        "spots" -> viewModel.setWithOpenSpotsOnly(false)
                        "completed" -> viewModel.setCompletedOnly(false)
                        "type" -> viewModel.setType(null)
                    }
                },
                onClearFilters = { viewModel.clearFilters() },
                search = {
                    LeoTextField(
                        value = query,
                        onValueChange = { query = it; viewModel.setQuery(it) },
                        label = "Buscar evento"
                    )
                }
            )
            LeoFilterSheet(
                visible = filtersOpen,
                onDismiss = { filtersOpen = false },
                onClearDraft = {
                    draftSpots = false
                    draftCompleted = false
                    draftType = null
                },
                onApply = {
                    viewModel.setWithOpenSpotsOnly(draftSpots)
                    viewModel.setCompletedOnly(draftCompleted)
                    viewModel.setType(draftType)
                    filtersOpen = false
                }
            ) {
                LeoFilterChip(
                    label = "Con cupos",
                    selected = draftSpots,
                    onClick = { draftSpots = !draftSpots }
                )
                LeoFilterChip(
                    label = "Completados",
                    selected = draftCompleted,
                    onClick = { draftCompleted = !draftCompleted }
                )
                LeoFilterChip(
                    label = EventPresentation.eventType(M18EventType.ADOPTION_FAIR),
                    selected = draftType == M18EventType.ADOPTION_FAIR,
                    onClick = {
                        draftType = if (draftType == M18EventType.ADOPTION_FAIR) null else M18EventType.ADOPTION_FAIR
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canAdminister) {
                    LeoOutlinedButton(
                        text = "Administrar",
                        onClick = onManage
                    )
                    LeoPrimaryButton(
                        text = "Nuevo",
                        onClick = onCreate,
                        modifier = Modifier.wrapContentWidth()
                    )
                }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(LeoDimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(event.organizationDisplayName, style = MaterialTheme.typography.bodySmall)
            Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(EventPresentation.whenLine(event.startsAt, event.endsAt), style = MaterialTheme.typography.bodyMedium)
            EventPresentation.placeLine(event.venueName, event.reference.publicLocationText)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(EventPresentation.eventType(event.eventType), style = MaterialTheme.typography.labelMedium)
            EventPresentation.availability(event.maxCapacity, event.availableSpots, event.isWaitlistOpen)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (event.maxCapacity > 0) {
                val taken = (event.maxCapacity - event.availableSpots).coerceAtLeast(0)
                LinearProgressIndicator(
                    progress = { (taken.toFloat() / event.maxCapacity).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            LeoStatusBadge(EventPresentation.eventStatus(event.status))
        }
        LeoHairline()
    }
}

@Composable
fun M18EventDetailScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    viewModel: M18EventDetailViewModel = viewModel(factory = M18EventDetailViewModel.factory(eventId))
) {
    val event by viewModel.event.collectAsState()
    val participation by viewModel.participation.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = EventPresentation.DISCOVER_TITLE, showBackButton = true, onBackClick = onNavigateBack)
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
                val place = EventPresentation.placeLine(e.venueName, e.reference.publicLocationText)
                val availability = EventPresentation.availability(e.maxCapacity, e.availableSpots, e.isWaitlistOpen)
                Column(
                    Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(e.organizationDisplayName, style = MaterialTheme.typography.bodyMedium)
                    Text(e.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(EventPresentation.eventStatus(e.status), style = MaterialTheme.typography.labelMedium)
                    Text(EventPresentation.whenLine(e.startsAt, e.endsAt))
                    place?.let { Text(it) }
                    if (e.description.isNotBlank()) Text(e.description)
                    Text(EventPresentation.eventType(e.eventType), style = MaterialTheme.typography.labelMedium)
                    e.reference.petPublicName?.takeIf { it.isNotBlank() }?.let {
                        Text("Mascota: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    availability?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    val ended = e.endsAt < System.currentTimeMillis()
                    when (e.status) {
                        M18EventStatus.CANCELLED -> Text(EventPresentation.CANCELLED_EVENT)
                        M18EventStatus.COMPLETED -> Text(EventPresentation.PAST_EVENT)
                        else -> if (ended) Text(EventPresentation.PAST_EVENT)
                    }
                    when (participation) {
                        M18EventParticipationUiState.Loading -> Unit
                        M18EventParticipationUiState.NotAuthenticated -> {
                            Text("Iniciá sesión para inscribirte.")
                        }
                        M18EventParticipationUiState.Available -> {
                            LeoPrimaryButton(
                                text = EventPresentation.REGISTER_ACTION,
                                onClick = { viewModel.register() }
                            )
                        }
                        M18EventParticipationUiState.WaitlistAvailable -> {
                            Text(EventPresentation.WAITLIST_BEFORE)
                            Text(EventPresentation.WAITLIST_HOW, style = MaterialTheme.typography.bodySmall)
                            LeoPrimaryButton(
                                text = EventPresentation.WAITLIST_ACTION,
                                onClick = { viewModel.register() }
                            )
                        }
                        M18EventParticipationUiState.Registered -> {
                            Text(EventPresentation.REGISTERED_NOW)
                            LeoOutlinedButton(
                                text = EventPresentation.CANCEL_ACTION,
                                onClick = { viewModel.cancelRegistration() }
                            )
                        }
                        M18EventParticipationUiState.Waitlisted -> {
                            Text(EventPresentation.WAITLIST_NOW)
                            if (e.status == M18EventStatus.PUBLISHED && !ended) {
                                Text(EventPresentation.WAITLIST_HOW, style = MaterialTheme.typography.bodySmall)
                                LeoOutlinedButton(
                                    text = EventPresentation.LEAVE_WAITLIST_ACTION,
                                    onClick = { viewModel.cancelRegistration() }
                                )
                            }
                        }
                        M18EventParticipationUiState.CheckedIn -> {
                            Text(EventPresentation.ownRegistration(M18RegistrationStatus.CHECKED_IN))
                        }
                        M18EventParticipationUiState.Attended -> {
                            Text(EventPresentation.ownRegistration(M18RegistrationStatus.ATTENDED))
                        }
                        M18EventParticipationUiState.NoShow -> {
                            Text(EventPresentation.ownRegistration(M18RegistrationStatus.NO_SHOW))
                        }
                        M18EventParticipationUiState.Rejected -> {
                            Text(EventPresentation.ownRegistration(M18RegistrationStatus.REJECTED))
                        }
                        M18EventParticipationUiState.Cancelled -> {
                            Text(EventPresentation.ownRegistration(M18RegistrationStatus.CANCELLED))
                            if (e.isFull && e.isWaitlistOpen) {
                                Text(EventPresentation.WAITLIST_BEFORE)
                                LeoPrimaryButton(
                                    text = EventPresentation.WAITLIST_ACTION,
                                    onClick = { viewModel.register() }
                                )
                            } else if (e.isRegistrationOpen) {
                                LeoPrimaryButton(
                                    text = EventPresentation.REGISTER_ACTION,
                                    onClick = { viewModel.register() }
                                )
                            }
                        }
                        M18EventParticipationUiState.EventFull -> {
                            Text(EventPresentation.FULL_NO_WAITLIST)
                        }
                        M18EventParticipationUiState.EventClosed -> {
                            val alreadyExplained = e.status == M18EventStatus.CANCELLED ||
                                e.status == M18EventStatus.COMPLETED ||
                                ended
                            if (!alreadyExplained) Text(EventPresentation.CLOSED)
                        }
                        is M18EventParticipationUiState.Error -> {
                            Text(
                                (participation as M18EventParticipationUiState.Error).message,
                                color = MaterialTheme.colorScheme.error
                            )
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                    Column(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(LeoDimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        LeoHairline()
                    }
                    LeoOutlinedButton(
                        text = "Promover lista de espera (manual)",
                        onClick = { viewModel.promoteWaitlist() }
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.participants, key = { it.registrationId }) { p ->
                            Column(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(LeoDimens.SpaceCompact), verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceXs)) {
                                    Text(p.displayAlias, fontWeight = FontWeight.Medium)
                                    Text(m18RegistrationStatusLabel(p.status), style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (p.canCheckIn) {
                                            LeoOutlinedButton(
                                                text = "Check-in",
                                                onClick = { viewModel.checkIn(p.registrationId) }
                                            )
                                        }
                                        if (p.canMarkAttendance) {
                                            LeoOutlinedButton(
                                                text = "Asistió",
                                                onClick = { viewModel.markAttendance(p.registrationId) }
                                            )
                                        }
                                        if (p.canMarkNoShow) {
                                            LeoOutlinedButton(
                                                text = "No-show",
                                                onClick = { viewModel.markNoShow(p.registrationId) }
                                            )
                                        }
                                    }
                                }
                                LeoHairline()
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Administrar eventos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                M18MockOrganizations.MANAGE_ORGANIZATION_IDS.forEach { orgId ->
                    LeoFilterChip(
                        label = orgId.removePrefix("org_"),
                        selected = selectedOrg == orgId,
                        onClick = { viewModel.selectOrganization(orgId) }
                    )
                }
            }
            LeoPrimaryButton(
                text = "Nuevo evento",
                onClick = onCreate
            )
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
                        Column(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(LeoDimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
                                Text(ev.title, fontWeight = FontWeight.Bold)
                                Text("${m18EventStatusLabel(ev.status)} · ${m18EventTypeLabel(ev.eventType)}")
                                summary?.let {
                                    Text("Inscriptos: ${it.registeredCount}/${it.maxCapacity}")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LeoOutlinedButton(
                                        text = "Editar",
                                        onClick = { onEditEvent(ev.id) }
                                    )
                                    LeoOutlinedButton(
                                        text = "Operaciones",
                                        onClick = { onOperations(ev.id) }
                                    )
                                    if (ev.status == com.comunidapp.app.data.model.M18EventStatus.DRAFT) {
                                        LeoPrimaryButton(
                                            text = "Publicar",
                                            onClick = { viewModel.publish(ev.id) }
                                        )
                                    }
                                    if (ev.status == com.comunidapp.app.data.model.M18EventStatus.PUBLISHED) {
                                        LeoOutlinedButton(
                                            text = "Pausar",
                                            onClick = { viewModel.pause(ev.id) }
                                        )
                                        LeoOutlinedButton(
                                            text = "Completar",
                                            onClick = { viewModel.complete(ev.id) }
                                        )
                                    }
                                    if (!ev.status.isTerminal) {
                                        LeoOutlinedButton(
                                            text = "Cancelar",
                                            onClick = { viewModel.cancel(ev.id) }
                                        )
                                    }
                                }
                            }
                            LeoHairline()
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
            LeoTextField(
                value = draft.title,
                onValueChange = { viewModel.updateDraft { d -> d.copy(title = it) } },
                label = "Título"
            )
            LeoTextField(
                value = draft.description,
                onValueChange = { viewModel.updateDraft { d -> d.copy(description = it) } },
                label = "Descripción",
                singleLine = false,
                minLines = 3
            )
            LeoTextField(
                value = draft.maxCapacity.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { cap ->
                        viewModel.updateDraft { d -> d.copy(maxCapacity = cap) }
                    }
                },
                label = "Cupo máximo"
            )
            LeoTextField(
                value = draft.venueName,
                onValueChange = { viewModel.updateDraft { d -> d.copy(venueName = it) } },
                label = "Nombre del lugar (público)"
            )
            V2LocationStringPicker(
                value = draft.publicLocationText,
                onValueChange = { viewModel.updateDraft { d -> d.copy(publicLocationText = it) } }
            )
            LeoTextField(
                value = draft.petPublicName,
                onValueChange = { viewModel.updateDraft { d -> d.copy(petPublicName = it) } },
                label = "Mascota (opcional, nombre público)"
            )
            LeoTextField(
                value = draft.durationHours.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { h ->
                        viewModel.updateDraft { d -> d.copy(durationHours = h.coerceAtLeast(1)) }
                    }
                },
                label = "Duración (horas)"
            )
            if (state is M18EventEditUiState.Error) {
                Text((state as M18EventEditUiState.Error).message, color = MaterialTheme.colorScheme.error)
            }
            LeoPrimaryButton(
                text = if (state is M18EventEditUiState.Saving) "Guardando…" else "Guardar borrador",
                onClick = { viewModel.save() },
                enabled = state !is M18EventEditUiState.Saving
            )
        }
    }
}

@Composable
fun M18MyEventsScreen(
    onNavigateBack: () -> Unit,
    onEventClick: (String) -> Unit,
    viewModel: M18MyEventsViewModel = viewModel(factory = M18MyEventsViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = EventPresentation.ACTIVITY_TITLE,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            state.loading && state.upcoming.isEmpty() && state.waitlist.isEmpty() && state.past.isEmpty() ->
                LoadingState(contentModifier = Modifier.padding(padding))
            state.error != null && state.upcoming.isEmpty() && state.waitlist.isEmpty() && state.past.isEmpty() ->
                ErrorState(message = state.error ?: "", contentModifier = Modifier.padding(padding), onRetry = { viewModel.load() })
            state.upcoming.isEmpty() && state.waitlist.isEmpty() && state.past.isEmpty() ->
                EmptyState(
                    title = EventPresentation.ACTIVITY_TITLE,
                    contentModifier = Modifier.padding(padding),
                    message = EventPresentation.EMPTY_ACTIVITY
                )
            else -> Column(
                Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EventActivitySection(EventPresentation.SECTION_UPCOMING, state.upcoming, onEventClick)
                EventActivitySection(EventPresentation.SECTION_WAITLIST, state.waitlist, onEventClick)
                EventActivitySection(EventPresentation.SECTION_PAST, state.past, onEventClick)
            }
        }
    }
}

@Composable
private fun EventActivitySection(
    title: String,
    rows: List<MyEventRegistration>,
    onEventClick: (String) -> Unit
) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        rows.forEach { row ->
            val place = EventPresentation.placeLine(row.venueName, row.locationText)
            LeoListRow(
                title = row.title,
                subtitle = buildString {
                    append(row.organizationName)
                    append(" · ")
                    append(EventPresentation.whenLine(row.startsAt, row.endsAt))
                    if (place != null) {
                        append(" · ")
                        append(place)
                    }
                    append(" · ")
                    append(EventPresentation.ownRegistration(row.registrationStatus))
                },
                onClick = { onEventClick(row.eventId) }
            )
        }
    }
}
