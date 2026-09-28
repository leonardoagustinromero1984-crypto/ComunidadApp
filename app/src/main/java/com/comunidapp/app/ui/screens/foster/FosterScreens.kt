package com.comunidapp.app.ui.screens.foster

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterHomeRequestStatus
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.components.v2.V2LocationCityProvincePicker
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.FosterDetailUiState
import com.comunidapp.app.viewmodel.FosterHomeDetailViewModel
import com.comunidapp.app.viewmodel.FosterHomeFormViewModel
import com.comunidapp.app.viewmodel.FosterHomesListViewModel
import com.comunidapp.app.viewmodel.FosterListUiState
import com.comunidapp.app.viewmodel.FosterPlacementDetailViewModel
import com.comunidapp.app.viewmodel.FosterPlacementsViewModel
import com.comunidapp.app.viewmodel.FosterRequestDetailViewModel
import com.comunidapp.app.viewmodel.FosterRequestFormViewModel
import com.comunidapp.app.viewmodel.FosterRequestsListViewModel
import com.comunidapp.app.viewmodel.MyFosterHomeUiState
import com.comunidapp.app.viewmodel.MyFosterHomeViewModel

@Composable
fun FosterHomesScreen(
    onNavigateBack: () -> Unit,
    onHomeClick: (String) -> Unit,
    onMyHome: () -> Unit,
    onReceived: () -> Unit,
    onSent: () -> Unit,
    onPlacements: () -> Unit,
    onHistory: () -> Unit = {},
    viewModel: FosterHomesListViewModel = viewModel(factory = FosterHomesListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Hogares de tránsito",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            LeoOutlinedButton(text = "Mi hogar", onClick = onMyHome)
            Spacer(Modifier.height(8.dp))
            LeoOutlinedButton(text = "Recibidas", onClick = onReceived)
            Spacer(Modifier.height(8.dp))
            LeoOutlinedButton(text = "Enviadas", onClick = onSent)
            Spacer(Modifier.height(8.dp))
            LeoOutlinedButton(text = "Animales alojados", onClick = onPlacements)
            Spacer(Modifier.height(8.dp))
            LeoOutlinedButton(text = "Historial de tránsitos", onClick = onHistory)
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                FosterListUiState.Loading -> LoadingState()
                FosterListUiState.Empty -> EmptyState(title = "No hay hogares disponibles.")
                is FosterListUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::refresh)
                is FosterListUiState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.homes, key = { it.id }) { home ->
                        Column(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onHomeClick(home.id) }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(home.displayName, fontWeight = FontWeight.Bold)
                                Text("${home.zoneText} · ${home.freeSlots} lugares libres")
                                Text(
                                    home.acceptedSpecies.joinToString() + " · " +
                                        home.acceptedSizes.joinToString()
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
fun MyFosterHomeScreen(
    onNavigateBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onPlacements: () -> Unit = {},
    onRequests: () -> Unit = {},
    onOpenRequests: () -> Unit = {},
    onNewPlacement: () -> Unit = {},
    viewModel: MyFosterHomeViewModel = viewModel(factory = MyFosterHomeViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val error by viewModel.actionError.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Mi hogar de tránsito",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when (val s = state) {
                MyFosterHomeUiState.Loading -> LoadingState()
                MyFosterHomeUiState.Empty -> {
                    EmptyState(title = "Todavía no tenés un perfil de hogar.")
                    LeoPrimaryButton(text = "Crear perfil", onClick = onCreate)
                }
                is MyFosterHomeUiState.Error -> ErrorState(message = s.message)
                is MyFosterHomeUiState.Content -> {
                    val h = s.home
                    Text("Hogar de tránsito", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Alojamientos temporales de mascotas que están a tu cuidado.")
                    Text(
                        if (h.status == FosterHomeStatus.ACTIVE) {
                            "Hogar de tránsito activo"
                        } else {
                            "Hogar de tránsito pausado"
                        }
                    )
                    Text(
                        if (h.availabilityStatus == FosterAvailabilityStatus.AVAILABLE ||
                            h.availabilityStatus == FosterAvailabilityStatus.LIMITED
                        ) {
                            "Disponible para recibir tránsitos"
                        } else {
                            "No disponible por ahora"
                        }
                    )
                    Text("Capacidad: ${h.totalCapacity} mascotas")
                    if (h.zoneText.isNotBlank()) Text("Zona: ${com.comunidapp.app.domain.ux.HumanLocationLabel.visible(h.zoneText)}")
                    Spacer(Modifier.height(12.dp))
                    LeoPrimaryButton(text = "Ver solicitudes abiertas", onClick = onOpenRequests)
                    LeoPrimaryButton(text = "Tránsitos", onClick = onPlacements)
                    Spacer(Modifier.height(8.dp))
                    LeoPrimaryButton(text = "+ Nuevo tránsito", onClick = onNewPlacement)
                    Spacer(Modifier.height(8.dp))
                    LeoOutlinedButton(text = "Solicitudes", onClick = onRequests)
                    Spacer(Modifier.height(8.dp))
                    LeoOutlinedButton(text = "Editar disponibilidad", onClick = { onEdit(h.id) })
                    if (h.status != FosterHomeStatus.ACTIVE) {
                        Spacer(Modifier.height(8.dp))
                        LeoPrimaryButton(
                            text = "Activar disponibilidad",
                            onClick = { viewModel.activate(h.id) },
                            enabled = !submitting
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        LeoOutlinedButton(
                            text = "Pausar disponibilidad",
                            onClick = { viewModel.pause(h.id) },
                            enabled = !submitting
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FosterHomeFormScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    editHomeId: String? = null,
    viewModel: FosterHomeFormViewModel = viewModel(factory = FosterHomeFormViewModel.factory())
) {
    val form by viewModel.form.collectAsState()
    LaunchedEffect(editHomeId) {
        if (!editHomeId.isNullOrBlank()) viewModel.loadForEdit(editHomeId)
    }
    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = if (editHomeId == null) "Hogar de tránsito" else "Editar hogar de tránsito",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Este es un perfil personal. No es una organización.",
                style = MaterialTheme.typography.bodySmall
            )
            V2LocationCityProvincePicker(
                city = form.publicLocationText,
                province = form.zoneText,
                onCityChange = { v -> viewModel.update { it.copy(publicLocationText = v) } },
                onProvinceChange = { v -> viewModel.update { it.copy(zoneText = v) } },
                onLocalityIdChange = { id -> viewModel.update { it.copy(localityId = id) } }
            )
            Text("Capacidad aproximada")
            Row {
                listOf("1", "2", "3").forEach { cap ->
                    RadioButton(
                        selected = form.capacity == cap || (cap == "3" && (form.capacity.toIntOrNull() ?: 0) >= 3),
                        onClick = { viewModel.update { it.copy(capacity = cap) } }
                    )
                    Text(if (cap == "3") "3+" else cap)
                }
            }
            Text("Preferencias opcionales")
            OutlinedTextField(
                form.speciesPref,
                { v -> viewModel.update { it.copy(speciesPref = v) } },
                label = { Text("Perros / gatos / ambos") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.agePref,
                { v -> viewModel.update { it.copy(agePref = v) } },
                label = { Text("Cachorros / adultos") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.notes,
                { v -> viewModel.update { it.copy(notes = v) } },
                label = { Text("Tratamientos, convivencia, observaciones") },
                modifier = Modifier.fillMaxWidth()
            )
            if (editHomeId == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(form.activate, { c -> viewModel.update { it.copy(activate = c) } })
                    Text("Disponible para tránsito")
                }
            }
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LeoPrimaryButton(
                text = if (form.submitting) "Guardando…" else "Guardar",
                onClick = viewModel::submit,
                enabled = !form.submitting
            )
        }
    }
}

@Composable
fun FosterHomeDetailScreen(
    onNavigateBack: () -> Unit,
    onRequest: (String) -> Unit,
    viewModel: FosterHomeDetailViewModel
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Hogar de tránsito", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            FosterDetailUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is FosterDetailUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is FosterDetailUiState.Content -> Column(Modifier.padding(padding).padding(16.dp)) {
                val h = s.home
                Text(h.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(h.zoneText)
                h.publicLocationText?.let { Text(it) }
                Text("${h.freeSlots} lugares libres de ${h.totalCapacity}")
                Text("Especies: ${h.acceptedSpecies.joinToString()}")
                Text("Tamaños: ${h.acceptedSizes.joinToString()}")
                if (h.acceptsEmergencies) Text("Acepta urgencias")
                h.description?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(16.dp))
                if (s.canRequest) {
                    LeoPrimaryButton(
                        text = "Solicitar tránsito",
                        onClick = { onRequest(h.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun FosterRequestFormScreen(
    onNavigateBack: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: FosterRequestFormViewModel
) {
    val form by viewModel.form.collectAsState()
    LaunchedEffect(form.submitted) {
        if (form.submitted) onSubmitted()
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Solicitar tránsito", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Mascota", fontWeight = FontWeight.SemiBold)
            form.pets.forEach { pet ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.update { it.copy(selectedPetId = pet.id) } }
                ) {
                    RadioButton(
                        selected = form.selectedPetId == pet.id,
                        onClick = { viewModel.update { it.copy(selectedPetId = pet.id) } }
                    )
                    Text("${pet.name} (${pet.species.name}/${pet.size.name})")
                }
            }
            if (form.pets.isEmpty()) {
                Text("No tenés mascotas elegibles.")
            }
            OutlinedTextField(
                form.message,
                { v -> viewModel.update { it.copy(message = v) } },
                label = { Text("Mensaje") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Urgencia")
            FosterUrgency.entries.filter { it != FosterUrgency.UNKNOWN }.forEach { u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = form.urgency == u,
                        onClick = { viewModel.update { it.copy(urgency = u) } }
                    )
                    Text(u.name)
                }
            }
            OutlinedTextField(
                form.specialNeeds,
                { v -> viewModel.update { it.copy(specialNeeds = v) } },
                label = { Text("Necesidades especiales") },
                modifier = Modifier.fillMaxWidth()
            )
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LeoPrimaryButton(
                text = if (form.submitting) "Enviando…" else "Enviar solicitud",
                onClick = viewModel::submit,
                enabled = !form.submitting && !form.submitted
            )
        }
    }
}

@Composable
fun FosterRequestsScreen(
    title: String,
    received: Boolean,
    onNavigateBack: () -> Unit,
    onRequestClick: (String) -> Unit,
    showBackButton: Boolean = true,
    viewModel: FosterRequestsListViewModel = viewModel(
        factory = FosterRequestsListViewModel.factory(received)
    )
) {
    val requests by viewModel.requests.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var confirmAccept by remember { mutableStateOf<String?>(null) }
    var confirmReject by remember { mutableStateOf<String?>(null) }

    confirmAccept?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmAccept = null },
            title = { Text("Aceptar solicitud") },
            text = { Text("Se reservará capacidad. El ingreso se confirma después.") },
            confirmButton = {
                TextButton({
                    viewModel.accept(id)
                    confirmAccept = null
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton({ confirmAccept = null }) { Text("Cancelar") } }
        )
    }
    confirmReject?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmReject = null },
            title = { Text("Rechazar solicitud") },
            confirmButton = {
                TextButton({
                    viewModel.reject(id, null)
                    confirmReject = null
                }) { Text("Rechazar") }
            },
            dismissButton = { TextButton({ confirmReject = null }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = title,
                showBackButton = showBackButton,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            loading -> LoadingState(contentModifier = Modifier.padding(padding))
            error != null && requests.isEmpty() -> ErrorState(
                message = error ?: "",
                contentModifier = Modifier.padding(padding)
            )
            requests.isEmpty() -> EmptyState(
                title = "Sin solicitudes.",
                contentModifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                Modifier.padding(padding).padding(LeoDimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
            ) {
                items(requests, key = { it.id }) { req ->
                    Column(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onRequestClick(req.id) }
                                .padding(vertical = LeoDimens.SpaceCompact)
                        ) {
                            Text(
                                "${req.petName ?: req.petId} · ${req.urgency.name} · ${req.status.name}",
                                style = LeoCardTitle,
                                color = BrandText
                            )
                            Text(req.message, style = LeoCaption, color = BrandTextSecondary)
                            if (received) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (req.status == FosterHomeRequestStatus.SUBMITTED) {
                                        TextButton(
                                            onClick = { viewModel.markUnderReview(req.id) },
                                            enabled = !busy
                                        ) { Text("Revisar") }
                                    }
                                    if (req.status == FosterHomeRequestStatus.SUBMITTED ||
                                        req.status == FosterHomeRequestStatus.UNDER_REVIEW
                                    ) {
                                        TextButton(
                                            onClick = { confirmAccept = req.id },
                                            enabled = !busy
                                        ) { Text("Aceptar") }
                                        TextButton(
                                            onClick = { confirmReject = req.id },
                                            enabled = !busy
                                        ) { Text("Rechazar") }
                                    }
                                    if (req.status == FosterHomeRequestStatus.ACCEPTED) {
                                        TextButton(
                                            onClick = { viewModel.startPlacement(req.id) },
                                            enabled = !busy
                                        ) { Text("Registrar ingreso") }
                                    }
                                }
                            } else if (req.status == FosterHomeRequestStatus.SUBMITTED ||
                                req.status == FosterHomeRequestStatus.UNDER_REVIEW
                            ) {
                                TextButton(
                                    onClick = { viewModel.cancel(req.id) },
                                    enabled = !busy
                                ) { Text("Cancelar") }
                            }
                        }
                        LeoHairline()
                    }
                }
            }
        }
    }
}

@Composable
fun FosterRequestDetailScreen(
    onNavigateBack: () -> Unit,
    onPlacementStarted: (String) -> Unit,
    viewModel: FosterRequestDetailViewModel
) {
    val request by viewModel.request.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val busy by viewModel.busy.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.placementStarted.collect { onPlacementStarted(it) }
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Solicitud", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when {
            loading -> LoadingState(contentModifier = Modifier.padding(padding))
            error != null && request == null -> ErrorState(
                message = error ?: "",
                contentModifier = Modifier.padding(padding)
            )
            request == null -> EmptyState(
                title = "No encontrada",
                contentModifier = Modifier.padding(padding)
            )
            else -> {
                val req = request!!
                Column(Modifier.padding(padding).padding(16.dp)) {
                    Text("Estado: ${req.status.name}")
                    Text("Mascota: ${req.petName ?: req.petId}")
                    Text("Urgencia: ${req.urgency.name}")
                    Text(req.message)
                    req.specialNeeds?.let { Text("Necesidades: $it") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (req.status == FosterHomeRequestStatus.ACCEPTED) {
                        Spacer(Modifier.height(12.dp))
                        LeoPrimaryButton(
                            text = "Registrar ingreso",
                            onClick = viewModel::startPlacement,
                            enabled = !busy
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FosterPlacementsScreen(
    onNavigateBack: () -> Unit,
    onPlacementClick: (String) -> Unit,
    showBackButton: Boolean = true,
    onNewPlacement: () -> Unit = {},
    viewModel: FosterPlacementsViewModel = viewModel(factory = FosterPlacementsViewModel.factory())
) {
    val placements by viewModel.placements.collectAsState()
    val active = placements.filter { it.status == FosterPlacementStatus.ACTIVE || it.status == FosterPlacementStatus.RESERVED }
    val previous = placements.filter { it.status == FosterPlacementStatus.COMPLETED || it.status == FosterPlacementStatus.CANCELLED }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Hogar de tránsito",
                subtitle = "Tránsitos temporales a tu cargo.",
                showBackButton = showBackButton,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            item {
                LeoPrimaryButton(text = "+ Nuevo tránsito", onClick = onNewPlacement)
            }
            if (placements.isEmpty()) {
                item {
                    EmptyState(title = "Todavía no tenés tránsitos activos.")
                }
            } else {
                if (active.isNotEmpty()) {
                    item { Text("Activos", style = LeoCardTitle, color = BrandText) }
                    items(active, key = { it.id }) { p ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPlacementClick(p.id) }
                                .padding(vertical = LeoDimens.SpaceCompact)
                        ) {
                            Text(p.petName ?: "Mascota", style = LeoCardTitle, color = BrandText)
                            Text(if (p.status == FosterPlacementStatus.ACTIVE) "Activo" else "Reservado")
                            Text(
                                if (p.vitacoraAccessGranted == true) "VitaCora ✓ Acceso habilitado"
                                else "VitaCora Acceso pendiente"
                            )
                        }
                        LeoHairline()
                    }
                }
                if (previous.isNotEmpty()) {
                    item { Text("Anteriores", style = LeoCardTitle, color = BrandText) }
                    items(previous, key = { it.id }) { p ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPlacementClick(p.id) }
                                .padding(vertical = LeoDimens.SpaceCompact)
                        ) {
                            Text(p.petName ?: "Mascota", style = LeoCardTitle, color = BrandText)
                            Text("Anterior")
                        }
                        LeoHairline()
                    }
                }
            }
        }
    }
}

@Composable
fun FosterPlacementDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: FosterPlacementDetailViewModel
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
            error != null && placement == null -> ErrorState(
                message = error ?: "",
                contentModifier = Modifier.padding(padding)
            )
            placement == null -> EmptyState(
                title = "No encontrado",
                contentModifier = Modifier.padding(padding)
            )
            else -> {
                val p = placement!!
                Column(Modifier.padding(padding).padding(16.dp)) {
                    Text("Estado: ${p.status.name}")
                    Text("Mascota: ${p.petName ?: p.petId}")
                    Text(
                        if (p.vitacoraAccessGranted == true) "VitaCora ✓ Acceso habilitado"
                        else "VitaCora Acceso pendiente"
                    )
                    Text("Hogar: ${p.fosterHomeId}")
                    Text("Cuidador temporal: ${p.fosterUserId}")
                    p.temporaryResponsibilityId?.let {
                        Text("Vínculo temporal activo")
                    }
                    Text("El responsable principal no cambia.")
                }
            }
        }
    }
}
