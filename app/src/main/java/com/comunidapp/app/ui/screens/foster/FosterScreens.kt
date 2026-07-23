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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.comunidapp.app.data.model.FosterHomeRequestStatus
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
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
    viewModel: FosterHomesListViewModel = viewModel(factory = FosterHomesListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onMyHome) { Text("Mi hogar") }
                OutlinedButton(onClick = onReceived) { Text("Recibidas") }
                OutlinedButton(onClick = onSent) { Text("Enviadas") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPlacements, modifier = Modifier.fillMaxWidth()) {
                Text("Animales alojados")
            }
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                FosterListUiState.Loading -> LoadingState()
                FosterListUiState.Empty -> EmptyState(title = "No hay hogares disponibles.")
                is FosterListUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::refresh)
                is FosterListUiState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.homes, key = { it.id }) { home ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onHomeClick(home.id) }
                                .padding(12.dp)
                        ) {
                            Text(home.displayName, fontWeight = FontWeight.Bold)
                            Text("${home.zoneText} · ${home.availabilityStatus.name} · libres ${home.freeSlots}")
                            Text(
                                home.acceptedSpecies.joinToString() + " · " +
                                    home.acceptedSizes.joinToString()
                            )
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
    viewModel: MyFosterHomeViewModel = viewModel(factory = MyFosterHomeViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val error by viewModel.actionError.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
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
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                        Text("Crear perfil")
                    }
                }
                is MyFosterHomeUiState.Error -> ErrorState(message = s.message)
                is MyFosterHomeUiState.Content -> {
                    val h = s.home
                    Text(h.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Estado: ${h.status.name} · ${h.availabilityStatus.name}")
                    Text("Capacidad: ${h.totalCapacity} · Ocupación: ${h.currentOccupancy} · Reservas: ${h.reservedCount}")
                    Text("Zona: ${h.zoneText}")
                    h.privateAddressText?.let { Text("Dirección (privada): $it") }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { onEdit(h.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Editar")
                    }
                    if (h.status != FosterHomeStatus.ACTIVE) {
                        Button(
                            onClick = { viewModel.activate(h.id) },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Activar") }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.pause(h.id) },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Pausar") }
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
        topBar = {
            ComunidappTopBar(
                title = if (editHomeId == null) "Nuevo hogar" else "Editar hogar",
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
            OutlinedTextField(
                form.displayName,
                { v -> viewModel.update { it.copy(displayName = v) } },
                label = { Text("Nombre público") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.description,
                { v -> viewModel.update { it.copy(description = v) } },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.zoneText,
                { v -> viewModel.update { it.copy(zoneText = v) } },
                label = { Text("Zona (localidad / área)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.publicLocationText,
                { v -> viewModel.update { it.copy(publicLocationText = v) } },
                label = { Text("Ubicación pública aproximada") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.privateAddressText,
                { v -> viewModel.update { it.copy(privateAddressText = v) } },
                label = { Text("Dirección privada (no pública)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.capacity,
                { v -> viewModel.update { it.copy(capacity = v) } },
                label = { Text("Capacidad total") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Especies", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(form.speciesDog, { c -> viewModel.update { it.copy(speciesDog = c) } })
                Text("Perro")
                Checkbox(form.speciesCat, { c -> viewModel.update { it.copy(speciesCat = c) } })
                Text("Gato")
            }
            Text("Tamaños", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(form.sizeS, { c -> viewModel.update { it.copy(sizeS = c) } })
                Text("S")
                Checkbox(form.sizeM, { c -> viewModel.update { it.copy(sizeM = c) } })
                Text("M")
                Checkbox(form.sizeL, { c -> viewModel.update { it.copy(sizeL = c) } })
                Text("L")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    form.acceptsSpecialNeeds,
                    { c -> viewModel.update { it.copy(acceptsSpecialNeeds = c) } }
                )
                Text("Necesidades especiales")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    form.acceptsEmergencies,
                    { c -> viewModel.update { it.copy(acceptsEmergencies = c) } }
                )
                Text("Urgencias")
            }
            if (editHomeId == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(form.activate, { c -> viewModel.update { it.copy(activate = c) } })
                    Text("Activar al guardar")
                }
            }
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = viewModel::submit,
                enabled = !form.submitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (form.submitting) "Guardando…" else "Guardar")
            }
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
        topBar = {
            ComunidappTopBar(title = "Hogar de tránsito", showBackButton = true, onBackClick = onNavigateBack)
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
                Text("${h.availabilityStatus.name} · libres ${h.freeSlots}/${h.totalCapacity}")
                Text("Especies: ${h.acceptedSpecies.joinToString()}")
                Text("Tamaños: ${h.acceptedSizes.joinToString()}")
                if (h.acceptsEmergencies) Text("Acepta urgencias")
                h.description?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(16.dp))
                if (s.canRequest) {
                    Button(onClick = { onRequest(h.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Solicitar tránsito")
                    }
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
        topBar = {
            ComunidappTopBar(title = "Solicitar tránsito", showBackButton = true, onBackClick = onNavigateBack)
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
            Button(
                onClick = viewModel::submit,
                enabled = !form.submitting && !form.submitted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (form.submitting) "Enviando…" else "Enviar solicitud")
            }
        }
    }
}

@Composable
fun FosterRequestsScreen(
    title: String,
    received: Boolean,
    onNavigateBack: () -> Unit,
    onRequestClick: (String) -> Unit,
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
        topBar = {
            ComunidappTopBar(title = title, showBackButton = true, onBackClick = onNavigateBack)
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
                Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(requests, key = { it.id }) { req ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onRequestClick(req.id) }
                            .padding(8.dp)
                    ) {
                        Text(
                            "${req.petName ?: req.petId} · ${req.urgency.name} · ${req.status.name}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(req.message, style = MaterialTheme.typography.bodySmall)
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
        topBar = {
            ComunidappTopBar(title = "Solicitud", showBackButton = true, onBackClick = onNavigateBack)
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
                        Button(
                            onClick = viewModel::startPlacement,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Registrar ingreso") }
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
    viewModel: FosterPlacementsViewModel = viewModel(factory = FosterPlacementsViewModel.factory())
) {
    val placements by viewModel.placements.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Alojamientos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        if (placements.isEmpty()) {
            EmptyState(
                title = "Sin alojamientos activos.",
                contentModifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(placements, key = { it.id }) { p ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPlacementClick(p.id) }
                            .padding(8.dp)
                    ) {
                        Text(
                            "${p.petName ?: p.petId} · ${p.status.name}",
                            fontWeight = FontWeight.SemiBold
                        )
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
        topBar = {
            ComunidappTopBar(title = "Alojamiento", showBackButton = true, onBackClick = onNavigateBack)
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
                    Text("Hogar: ${p.fosterHomeId}")
                    Text("Cuidador temporal: ${p.fosterUserId}")
                    p.temporaryResponsibilityId?.let {
                        Text("Custodia M08: $it (TEMPORARY_CUSTODIAN)")
                    }
                    Text("El responsable PRINCIPAL no cambia.")
                }
            }
        }
    }
}
