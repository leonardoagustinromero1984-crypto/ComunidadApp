package com.comunidapp.app.ui.screens.shelters

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
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.domain.publish.LocalDebugDiagnostic
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.components.v2.V2FormErrorBanner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.domain.context.RefugeDestinations
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.data.model.ShelterIntakeType
import com.comunidapp.app.data.model.ShelterPetEndReason
import com.comunidapp.app.data.model.ShelterPetPlacementStatus
import com.comunidapp.app.data.model.ShelterStatus
import com.comunidapp.app.data.model.ShelterVolunteerRole
import com.comunidapp.app.data.repository.CreateShelterProfileInput
import com.comunidapp.app.data.repository.UpdateShelterProfileInput
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.components.v2.V2SurfaceCard
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.MySheltersUiState
import com.comunidapp.app.viewmodel.MySheltersViewModel
import com.comunidapp.app.viewmodel.ShelterDashboardUiState
import com.comunidapp.app.viewmodel.ShelterDashboardViewModel
import com.comunidapp.app.viewmodel.ShelterDetailUiState
import com.comunidapp.app.viewmodel.ShelterFormViewModel
import com.comunidapp.app.viewmodel.ShelterIntakeViewModel
import com.comunidapp.app.viewmodel.ShelterListUiState
import com.comunidapp.app.viewmodel.ShelterOpsDetailViewModel
import com.comunidapp.app.viewmodel.ShelterPetDetailViewModel
import com.comunidapp.app.viewmodel.ShelterPetsViewModel
import com.comunidapp.app.viewmodel.ShelterPublicListViewModel
import com.comunidapp.app.viewmodel.ShelterVolunteerInviteViewModel
import com.comunidapp.app.viewmodel.ShelterVolunteersViewModel

@Composable
fun ShelterOpsListScreen(
    onNavigateBack: () -> Unit,
    onShelterClick: (String) -> Unit,
    onMyShelters: () -> Unit,
    onPublicCampaigns: () -> Unit = {},
    onPublicSupplyRequests: () -> Unit = {},
    onPublicEmergencies: () -> Unit = {},
    onPublicEvents: () -> Unit = {},
    onImportPets: (String, String) -> Unit = { _, _ -> },
    onImportRescuer: () -> Unit = {},
    onAddPet: () -> Unit = {},
    operationalHub: com.comunidapp.app.ui.screens.context.OperationalHubActions? = null,
    showBackButton: Boolean = true,
    viewModel: ShelterPublicListViewModel = viewModel(factory = ShelterPublicListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val active by OperationalContextProvider.active.collectAsState()
    val canonical = DataProvider.useSupabase && !DataProvider.useLegacyRemoteModules
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Gestión",
                subtitle = when (active) {
                    is OperationalContext.Rescuer -> "Rescatista"
                    else -> "Refugio / ONG"
                },
                showBackButton = showBackButton,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LeoDimens.SpaceMd)
        ) {
            val hub = operationalHub
            if (hub != null && active is OperationalContext.Foster) {
                com.comunidapp.app.ui.screens.context.FosterOperationalHub(context = active, actions = hub)
                Spacer(Modifier.height(LeoDimens.SpaceCompact))
            } else if (hub != null && active is OperationalContext.Rescuer) {
                com.comunidapp.app.ui.screens.context.RescuerOperationalHub(context = active, actions = hub)
                Spacer(Modifier.height(LeoDimens.SpaceCompact))
            } else if (hub != null && com.comunidapp.app.domain.context.ContextIdentityMapping.isRefugeNav(active)) {
                com.comunidapp.app.ui.screens.context.RefugeOperationalHub(context = active, actions = hub)
                Spacer(Modifier.height(LeoDimens.SpaceCompact))
            }
            if (canonical) {
                if (hub == null) {
                val org = active as? OperationalContext.Organization
                val rescuer = active is OperationalContext.Rescuer
                if (org != null || rescuer) {
                    if (org != null) {
                        OutlinedButton(onClick = { onImportPets(org.entityId, org.displayName) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Importar mascotas")
                        }
                    } else {
                        OutlinedButton(onClick = onImportRescuer, modifier = Modifier.fillMaxWidth()) {
                            Text("Importar mascotas")
                        }
                    }
                    Spacer(Modifier.height(LeoDimens.SpaceCompact))
                    OutlinedButton(onClick = onAddPet, modifier = Modifier.fillMaxWidth()) {
                        Text("+ Agregar mascota")
                    }
                    Spacer(Modifier.height(LeoDimens.SpaceCompact))
                }
                LeoEmptyState(
                    title = RefugeDestinations.notYetAvailableMessage(),
                    message = "Podés volver al inicio del refugio o a Perfil. Campañas, insumos, urgencias y eventos todavía no están en esta versión.",
                    icon = Icons.Default.Dashboard
                )
                }
            } else {
            OutlinedButton(onClick = onMyShelters, modifier = Modifier.fillMaxWidth()) {
                Text("Mis refugios")
            }
            OutlinedButton(onClick = onPublicCampaigns, modifier = Modifier.fillMaxWidth()) {
                Text("Campañas")
            }
            OutlinedButton(onClick = onPublicSupplyRequests, modifier = Modifier.fillMaxWidth()) {
                Text("Pedidos de insumos")
            }
            OutlinedButton(onClick = onPublicEmergencies, modifier = Modifier.fillMaxWidth()) {
                Text("Urgencias")
            }
            OutlinedButton(onClick = onPublicEvents, modifier = Modifier.fillMaxWidth()) {
                Text("Eventos")
            }
            Spacer(Modifier.height(LeoDimens.SpaceCompact))
            when (val s = state) {
                ShelterListUiState.Loading -> LoadingState()
                ShelterListUiState.Empty -> EmptyState(title = "No hay refugios públicos activos.")
                is ShelterListUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::refresh)
                is ShelterListUiState.Content -> Column(
                    verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
                ) {
                    s.items.forEach { item ->
                        V2SurfaceCard(onClick = { onShelterClick(item.id) }) {
                            Text(item.displayName, style = LeoCardTitle, color = BrandText)
                            Text(
                                item.publicZoneText ?: "Zona no informada",
                                style = LeoCaption,
                                color = BrandTextSecondary
                            )
                            Text(
                                "Disponibilidad: ${item.availability.name} · cupos ~${item.freeSlotsApproximate}",
                                style = LeoCaption,
                                color = BrandTextSecondary
                            )
                            Text(
                                "Especies: ${item.acceptedSpecies.joinToString()}",
                                style = LeoCaption,
                                color = BrandTextSecondary
                            )
                            if (item.acceptsEmergencies) {
                                Text("Acepta emergencias", style = LeoCaption, color = BrandText)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun MySheltersScreen(
    onNavigateBack: () -> Unit,
    onShelterClick: (String) -> Unit,
    onCreate: () -> Unit,
    onImportPets: (String, String) -> Unit = { _, _ -> },
    onAddPet: () -> Unit = {},
    showBackButton: Boolean = true,
    viewModel: MySheltersViewModel = viewModel(factory = MySheltersViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val active by OperationalContextProvider.active.collectAsState()
    val orgContext = active is OperationalContext.Organization
    val canonical = DataProvider.useSupabase && !DataProvider.useLegacyRemoteModules
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = if (orgContext) "Animales" else "Mis refugios",
                showBackButton = showBackButton,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(LeoDimens.SpaceMd)) {
            val org = active as? OperationalContext.Organization
            if (org != null) {
                OutlinedButton(
                    onClick = { onImportPets(org.entityId, org.displayName) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Importar mascotas") }
                Spacer(Modifier.height(LeoDimens.SpaceCompact))
                OutlinedButton(onClick = onAddPet, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Agregar mascota")
                }
                Spacer(Modifier.height(LeoDimens.SpaceCompact))
            }
            if (!orgContext && !canonical) {
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Crear refugio") }
                Spacer(Modifier.height(LeoDimens.SpaceCompact))
            }
            when (val s = state) {
                MySheltersUiState.Loading -> LoadingState()
                MySheltersUiState.Empty -> EmptyState(
                    title = if (orgContext) {
                        "Todavía no hay animales cargados en este refugio."
                    } else {
                        "Sin refugios vinculados."
                    }
                )
                is MySheltersUiState.Error -> ErrorState(message = s.message)
                is MySheltersUiState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
                ) {
                    items(s.items, key = { it.id }) { item ->
                        V2SurfaceCard(onClick = { onShelterClick(item.id) }) {
                            Text(
                                "${item.displayName} · ${item.status.name}",
                                style = LeoCardTitle,
                                color = BrandText
                            )
                            Text(
                                "Ocupación ${item.currentOccupancy}+${item.reservedCapacity}/${item.totalCapacity}",
                                style = LeoCaption,
                                color = BrandTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterOpsFormScreen(
    editShelterId: String? = null,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ShelterFormViewModel = viewModel(factory = ShelterFormViewModel.factory(editShelterId))
) {
    var orgId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("10") }
    var zone by remember { mutableStateOf("") }
    var addressRef by remember { mutableStateOf("") }
    var selectedSpecies by remember { mutableStateOf(setOf("DOG", "CAT")) }
    var emergencies by remember { mutableStateOf(false) }
    var activate by remember { mutableStateOf(true) }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val diagnostic by viewModel.diagnostic.collectAsState()
    val managedOrgs by viewModel.managedOrganizations.collectAsState()
    val existing by viewModel.existing.collectAsState()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(existing) {
        existing?.let {
            orgId = it.organizationId
            name = it.displayName
            description = it.description.orEmpty()
            capacity = it.totalCapacity.toString()
            zone = it.publicZoneText.orEmpty()
            addressRef = it.internalAddressRef.orEmpty()
            selectedSpecies = it.acceptedSpecies.toSet()
            emergencies = it.acceptsEmergencies
        }
    }
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved(it) } }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = if (editShelterId == null) "Nuevo refugio" else "Editar refugio",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (editShelterId == null) {
                Text("Organización", fontWeight = FontWeight.SemiBold)
                if (managedOrgs.isEmpty()) {
                    Text(
                        "Para crear un refugio necesitás una organización vinculada. Pedí una invitación o creá una organización.",
                        style = LeoCaption,
                        color = BrandTextSecondary
                    )
                } else {
                    managedOrgs.forEach { org ->
                        FilterChip(
                            selected = orgId == org.id.value,
                            onClick = { orgId = org.id.value },
                            label = { Text(org.publicName) }
                        )
                    }
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(capacity, { capacity = it.filter { ch -> ch.isDigit() } }, label = { Text("Capacidad") }, modifier = Modifier.fillMaxWidth())
            V2LocationStringPicker(value = zone, onValueChange = { zone = it })
            OutlinedTextField(addressRef, { addressRef = it }, label = { Text("Domicilio") }, modifier = Modifier.fillMaxWidth())
            Text("Especies", fontWeight = FontWeight.SemiBold)
            PetSpecies.entries.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = selectedSpecies.contains(entry.name),
                        onCheckedChange = { checked ->
                            selectedSpecies = if (checked) {
                                selectedSpecies + entry.name
                            } else {
                                selectedSpecies - entry.name
                            }
                        }
                    )
                    Text(entry.toDisplayName())
                }
            }
            RowCheck("Emergencias", emergencies) { emergencies = it }
            if (editShelterId == null) RowCheck("Activar", activate) { activate = it }
            error?.let { message ->
                val diagnosticText = diagnostic
                V2FormErrorBanner(
                    title = message,
                    onCopyDiagnostic = if (
                        LocalDebugDiagnostic.isCopyEnabled() && !diagnosticText.isNullOrBlank()
                    ) {
                        { clipboard.setText(AnnotatedString(diagnosticText)) }
                    } else {
                        null
                    }
                )
            }
            Button(
                enabled = !submitting && (editShelterId != null || orgId.isNotBlank()),
                onClick = {
                    val caps = capacity.toIntOrNull() ?: 0
                    val specs = selectedSpecies
                    if (editShelterId == null) {
                        viewModel.create(
                            CreateShelterProfileInput(
                                organizationId = orgId,
                                displayName = name,
                                description = description.ifBlank { null },
                                totalCapacity = caps,
                                acceptedSpecies = specs,
                                acceptsEmergencies = emergencies,
                                publicZoneText = zone.ifBlank { null },
                                internalAddressRef = addressRef.ifBlank { null },
                                activate = activate
                            )
                        )
                    } else {
                        viewModel.update(
                            UpdateShelterProfileInput(
                                shelterId = editShelterId,
                                displayName = name,
                                description = description.ifBlank { null },
                                totalCapacity = caps,
                                acceptedSpecies = specs,
                                acceptsEmergencies = emergencies,
                                publicZoneText = zone.ifBlank { null },
                                internalAddressRef = addressRef.ifBlank { null }
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Guardando…" else "Guardar") }
        }
    }
}

@Composable
fun ShelterOpsDetailScreen(
    onNavigateBack: () -> Unit,
    onDashboard: (String) -> Unit,
    viewModel: ShelterOpsDetailViewModel
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Refugio", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            ShelterDetailUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is ShelterDetailUiState.Error -> ErrorState(message = s.message, contentModifier = Modifier.padding(padding), onRetry = viewModel::reload)
            is ShelterDetailUiState.Content -> {
                val sh = s.shelter
                Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sh.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(sh.description.orEmpty())
                    Text("Zona: ${sh.publicZoneText ?: "—"}")
                    Text("Estado: ${sh.status.name} · ${sh.availability.name}")
                    Text("Cupos ~${sh.freeSlots} / ${sh.totalCapacity}")
                    Text("Especies: ${sh.acceptedSpecies.joinToString()}")
                    // never show internalAddressRef publicly
                    Button(onClick = { onDashboard(sh.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Panel operativo")
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterDashboardScreen(
    onNavigateBack: () -> Unit,
    onPets: (String) -> Unit,
    onVolunteers: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCampaigns: (String) -> Unit,
    onSupplyRequests: (String) -> Unit,
    onEmergencies: (String) -> Unit,
    onEvents: (String) -> Unit,
    onReports: (String) -> Unit,
    viewModel: ShelterDashboardViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val error by viewModel.error.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Panel del refugio", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            ShelterDashboardUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is ShelterDashboardUiState.Error -> ErrorState(message = s.message, contentModifier = Modifier.padding(padding))
            is ShelterDashboardUiState.Content -> {
                val d = s.data
                val sh = d.shelter
                Column(
                    Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(sh.displayName, fontWeight = FontWeight.SemiBold)
                    Text("Ocupación ${sh.currentOccupancy} · Reservas ${sh.reservedCapacity} · Capacidad ${sh.totalCapacity}")
                    Text("Disponibilidad: ${sh.availability.name}")
                    val active = d.pets.count { it.status == ShelterPetPlacementStatus.ACTIVE }
                    val quar = d.pets.count { it.status == ShelterPetPlacementStatus.QUARANTINE }
                    val med = d.pets.count { it.status == ShelterPetPlacementStatus.MEDICAL_CARE }
                    val vols = d.volunteers.count { it.status.name == "ACTIVE" }
                    Text("Mascotas activas: $active · Cuarentena: $quar · Médica: $med")
                    Text("Voluntarios activos: $vols")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(onClick = { onPets(sh.id) }, modifier = Modifier.fillMaxWidth()) { Text("Mascotas") }
                    OutlinedButton(onClick = { onVolunteers(sh.id) }, modifier = Modifier.fillMaxWidth()) { Text("Voluntarios") }
                    OutlinedButton(onClick = { onCampaigns(sh.id) }, modifier = Modifier.fillMaxWidth()) { Text("Campañas") }
                    OutlinedButton(onClick = { onSupplyRequests(sh.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Pedidos de insumos")
                    }
                    OutlinedButton(onClick = { onEmergencies(sh.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Urgencias")
                    }
                    OutlinedButton(onClick = { onEvents(sh.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Eventos")
                    }
                    OutlinedButton(onClick = { onReports(sh.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Reportes")
                    }
                    OutlinedButton(onClick = { onEdit(sh.id) }, modifier = Modifier.fillMaxWidth()) { Text("Editar perfil") }
                    if (sh.status == ShelterStatus.ACTIVE) {
                        OutlinedButton(onClick = { viewModel.changeStatus(ShelterStatus.PAUSED) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Pausar")
                        }
                    } else if (sh.status == ShelterStatus.PAUSED || sh.status == ShelterStatus.DRAFT) {
                        OutlinedButton(onClick = { viewModel.changeStatus(ShelterStatus.ACTIVE) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Activar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterOpsPetsScreen(
    onNavigateBack: () -> Unit,
    onIntake: () -> Unit,
    onDetail: (String) -> Unit,
    onImportPets: () -> Unit = {},
    viewModel: ShelterPetsViewModel
) {
    val pets by viewModel.pets.collectAsState()
    val error by viewModel.error.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Mascotas del refugio", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Button(onClick = onIntake, modifier = Modifier.fillMaxWidth()) { Text("Ingresar mascota") }
            OutlinedButton(onClick = onImportPets, modifier = Modifier.fillMaxWidth()) { Text("Importar mascotas") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (pets.isEmpty()) EmptyState(title = "Sin alojamientos.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pets, key = { it.id }) { p ->
                    Column(Modifier.fillMaxWidth().clickable { onDetail(p.id) }.padding(8.dp)) {
                        Text("${p.petName ?: p.petId} · ${p.status.name}", fontWeight = FontWeight.SemiBold)
                        Text("Ingreso: ${p.intakeType.name}")
                        if (p.status.isOpen) {
                            OutlinedButton(onClick = {
                                viewModel.changeStatus(p.id, ShelterPetPlacementStatus.QUARANTINE)
                            }) { Text("Cuarentena") }
                            OutlinedButton(onClick = {
                                viewModel.changeStatus(p.id, ShelterPetPlacementStatus.MEDICAL_CARE)
                            }) { Text("Atención médica") }
                            OutlinedButton(onClick = {
                                viewModel.release(p.id, ShelterPetEndReason.RELEASED_TO_OWNER)
                            }) { Text("Egresar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterIntakeScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ShelterIntakeViewModel
) {
    var petId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ShelterIntakeType.RESCUE) }
    var reserveOnly by remember { mutableStateOf(false) }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved() } }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Ingreso", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(petId, { petId = it }, label = { Text("Identificador de mascota") }, modifier = Modifier.fillMaxWidth())
            ShelterIntakeType.entries.filter { it != ShelterIntakeType.UNKNOWN }.forEach { t ->
                RowRadio(t.name, type == t) { type = t }
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
            RowCheck("Solo reservar", reserveOnly) { reserveOnly = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !submitting,
                onClick = { viewModel.admit(petId, type, notes.ifBlank { null }, reserveOnly) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Guardando…" else "Confirmar") }
        }
    }
}

@Composable
fun ShelterOpsPetDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShelterPetDetailViewModel
) {
    val placement by viewModel.placement.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Alojamiento", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when {
            loading -> LoadingState(contentModifier = Modifier.padding(padding))
            error != null && placement == null -> ErrorState(message = error ?: "", contentModifier = Modifier.padding(padding))
            placement == null -> EmptyState(title = "No encontrado", contentModifier = Modifier.padding(padding))
            else -> {
                val p = placement!!
                Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${p.petName ?: p.petId}", fontWeight = FontWeight.SemiBold)
                    Text("Estado: ${p.status.name}")
                    Text("Ingreso: ${p.intakeType.name}")
                    Text(
                        if (p.organizationalResponsibilityId.isNullOrBlank()) {
                            "Sin vínculo organizacional registrado"
                        } else {
                            "Vínculo organizacional activo"
                        }
                    )
                    Text("El responsable principal no se elimina desde operaciones de refugio.")
                    p.endReason?.let { Text("Egreso: $it") }
                }
            }
        }
    }
}

@Composable
fun ShelterOpsVolunteersScreen(
    onNavigateBack: () -> Unit,
    onInvite: () -> Unit,
    viewModel: ShelterVolunteersViewModel
) {
    val list by viewModel.volunteers.collectAsState()
    val error by viewModel.error.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Voluntarios", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Button(onClick = onInvite, modifier = Modifier.fillMaxWidth()) { Text("Invitar") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("La asignación no otorga permisos administrativos.")
            if (list.isEmpty()) EmptyState(title = "Sin voluntarios.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { v ->
                    Column(Modifier.padding(8.dp)) {
                        Text("${v.userId} · ${v.role.name} · ${v.status.name}", fontWeight = FontWeight.SemiBold)
                        if (v.status.name == "INVITED") {
                            OutlinedButton(onClick = { viewModel.accept(v.id) }) { Text("Aceptar") }
                        }
                        if (v.status.name == "ACTIVE") {
                            OutlinedButton(onClick = { viewModel.pause(v.id) }) { Text("Pausar") }
                        }
                        if (v.status.isOpen) {
                            OutlinedButton(onClick = { viewModel.end(v.id) }) { Text("Finalizar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterVolunteerInviteScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ShelterVolunteerInviteViewModel
) {
    var userId by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(ShelterVolunteerRole.ANIMAL_CARE) }
    var notes by remember { mutableStateOf("") }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved() } }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Invitar voluntario", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(userId, { userId = it }, label = { Text("Usuario ID") }, modifier = Modifier.fillMaxWidth())
            ShelterVolunteerRole.entries.filter { it != ShelterVolunteerRole.UNKNOWN }.forEach { r ->
                RowRadio(r.name, role == r) { role = r }
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !submitting,
                onClick = { viewModel.invite(userId, role, notes.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Enviando…" else "Invitar") }
        }
    }
}

@Composable
private fun RowRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

@Composable
private fun RowCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
