package com.comunidapp.app.ui.screens.m15

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M15FosterHomeDetailViewModel
import com.comunidapp.app.viewmodel.M15FosterHomesListUiState
import com.comunidapp.app.viewmodel.M15FosterHomesListViewModel
import com.comunidapp.app.viewmodel.M15FosterHubUiState
import com.comunidapp.app.viewmodel.M15FosterHubViewModel
import com.comunidapp.app.viewmodel.M15FosterRequestFormViewModel
import com.comunidapp.app.viewmodel.M15FosterRequestsViewModel
import com.comunidapp.app.viewmodel.M15MyFosterHomeViewModel

@Composable
fun M15FosterHubScreen(
    onNavigateBack: () -> Unit,
    onBrowseHomes: () -> Unit,
    onMyHome: () -> Unit,
    onReceivedRequests: () -> Unit,
    onMyPlacements: () -> Unit,
    onOperations: () -> Unit = {},
    viewModel: M15FosterHubViewModel = viewModel(factory = M15FosterHubViewModel.factory())
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
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Disponibilidad, solicitudes y alojamiento temporal (M15).",
                style = MaterialTheme.typography.bodySmall
            )
            when (val s = state) {
                M15FosterHubUiState.Loading -> LoadingState()
                is M15FosterHubUiState.Error -> ErrorState(message = s.message)
                is M15FosterHubUiState.Content -> {
                    Text("Hogares disponibles: ${s.availableCount}")
                    s.myHome?.let { home ->
                        Text("Tu hogar: ${home.displayName} (${home.status})")
                    }
                    Button(onClick = onBrowseHomes, modifier = Modifier.fillMaxWidth()) {
                        Text("Explorar hogares")
                    }
                    Button(onClick = onMyHome, modifier = Modifier.fillMaxWidth()) {
                        Text("Mi hogar de tránsito")
                    }
                    Button(onClick = onReceivedRequests, modifier = Modifier.fillMaxWidth()) {
                        Text("Solicitudes recibidas")
                    }
                    Button(onClick = onMyPlacements, modifier = Modifier.fillMaxWidth()) {
                        Text("Mis alojamientos")
                    }
                    Button(onClick = onOperations, modifier = Modifier.fillMaxWidth()) {
                        Text("Operaciones y métricas")
                    }
                }
            }
        }
    }
}

@Composable
fun M15FosterHomesListScreen(
    onNavigateBack: () -> Unit,
    onHomeClick: (String) -> Unit,
    viewModel: M15FosterHomesListViewModel = viewModel(factory = M15FosterHomesListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Hogares disponibles",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M15FosterHomesListUiState.Loading -> LoadingState()
                M15FosterHomesListUiState.Empty -> EmptyState(
                    title = "Sin hogares",
                    message = "Todavía no hay hogares de tránsito activos."
                )
                is M15FosterHomesListUiState.Error -> ErrorState(message = s.message)
                is M15FosterHomesListUiState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.items, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onHomeClick(item.id) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.displayName, fontWeight = FontWeight.Bold)
                                Text("${item.zoneText} · ${item.availabilityStatus}")
                                Text("Cupos libres: ${item.freeSlots}/${item.totalCapacity}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M15FosterHomeDetailScreen(
    homeId: String,
    onNavigateBack: () -> Unit,
    onRequest: (String) -> Unit,
    viewModel: M15FosterHomeDetailViewModel = viewModel(
        factory = M15FosterHomeDetailViewModel.factory(homeId)
    )
) {
    val listing by viewModel.listing.collectAsState()
    val message by viewModel.message.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Detalle del hogar",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            listing?.let { home ->
                Text(home.displayName, style = MaterialTheme.typography.titleLarge)
                Text("Zona: ${home.zoneText}")
                Text("Disponibilidad: ${home.availabilityStatus}")
                Text("Cupos: ${home.freeSlots}/${home.totalCapacity}")
                home.description?.let { Text(it) }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onRequest(home.id) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = home.freeSlots > 0
                ) {
                    Text("Solicitar tránsito")
                }
            } ?: LoadingState()
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun M15MyFosterHomeScreen(
    onNavigateBack: () -> Unit,
    viewModel: M15MyFosterHomeViewModel = viewModel(factory = M15MyFosterHomeViewModel.factory())
) {
    val home by viewModel.home.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var name by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("1") }
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Mi hogar de tránsito",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            home?.let { h ->
                Text(h.displayName, fontWeight = FontWeight.Bold)
                Text("Estado: ${h.status}")
                Text("Disponibilidad: ${h.availabilityStatus}")
                Text("Capacidad: ${h.totalCapacity}")
                if (h.status.name == "DRAFT") {
                    Button(
                        onClick = { viewModel.activate() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Activar hogar")
                    }
                }
            } ?: Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del hogar") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = zone,
                    onValueChange = { zone = it },
                    label = { Text("Zona") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Capacidad") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.createDraft(
                            displayName = name,
                            zoneText = zone,
                            capacity = capacity.toIntOrNull() ?: 1
                        )
                    },
                    enabled = !busy && name.isNotBlank() && zone.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Crear borrador")
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun M15FosterRequestFormScreen(
    homeId: String,
    onNavigateBack: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: M15FosterRequestFormViewModel = viewModel(
        factory = M15FosterRequestFormViewModel.factory(homeId)
    )
) {
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val submitted by viewModel.submitted.collectAsState()
    var petId by remember { mutableStateOf("") }
    var requestMessage by remember { mutableStateOf("") }
    LaunchedEffect(submitted) {
        if (submitted) onSubmitted()
    }
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Solicitar tránsito",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = petId,
                onValueChange = { petId = it },
                label = { Text("ID de mascota") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = requestMessage,
                onValueChange = { requestMessage = it },
                label = { Text("Mensaje") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.submit(petId, requestMessage) },
                enabled = !busy && petId.isNotBlank() && requestMessage.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar solicitud")
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun M15FosterRequestsScreen(
    onNavigateBack: () -> Unit,
    viewModel: M15FosterRequestsViewModel = viewModel(factory = M15FosterRequestsViewModel.factory())
) {
    val requests by viewModel.received.collectAsState()
    val message by viewModel.message.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Solicitudes recibidas",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            if (requests.isEmpty()) {
                EmptyState(title = "Sin solicitudes", message = "No hay solicitudes pendientes.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(requests, key = { it.id }) { req ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(req.petName ?: req.petId, fontWeight = FontWeight.Bold)
                                Text("Estado: ${req.status}")
                                Text(req.message)
                                if (req.status.name == "SUBMITTED" || req.status.name == "UNDER_REVIEW") {
                                    Button(onClick = { viewModel.accept(req.id) }) {
                                        Text("Aceptar")
                                    }
                                    Button(onClick = { viewModel.reject(req.id) }) {
                                        Text("Rechazar")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
