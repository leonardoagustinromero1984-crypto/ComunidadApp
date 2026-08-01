package com.comunidapp.app.ui.screens.m16

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
import com.comunidapp.app.viewmodel.M16ShelterDetailViewModel
import com.comunidapp.app.viewmodel.M16ShelterManageViewModel
import com.comunidapp.app.viewmodel.M16SheltersListUiState
import com.comunidapp.app.viewmodel.M16SheltersListViewModel

@Composable
fun M16SheltersListScreen(
    onNavigateBack: () -> Unit,
    onShelterClick: (String) -> Unit,
    onManage: () -> Unit,
    viewModel: M16SheltersListViewModel = viewModel(factory = M16SheltersListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShelterClick(item.id) }
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
                }
            }
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
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when {
                message != null -> ErrorState(message = message!!)
                shelter == null -> LoadingState()
                else -> {
                    val s = shelter!!
                    Text(s.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text("Zona: ${s.publicZoneText}")
                    s.description?.let { Text(it) }
                    Spacer(Modifier.height(8.dp))
                    Text("Estado: ${s.operationalStatus} · ${s.verificationStatus}")
                    Text("Capacidad agregada: ${s.freeSlotsApproximate}/${s.totalCapacity} libres")
                    Text("Especies: ${s.acceptedSpecies.joinToString()}")
                    Text("Servicios: ${s.services.joinToString { it.name }}")
                    if (s.needs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Necesidades", fontWeight = FontWeight.Bold)
                        s.needs.forEach { Text("· ${it.category}: ${it.description}") }
                    }
                    if (s.publicContacts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Contacto público", fontWeight = FontWeight.Bold)
                        s.publicContacts.forEach { Text("${it.type.name}: ${it.value}") }
                    }
                }
            }
        }
    }
}

@Composable
fun M16ShelterManageScreen(
    onNavigateBack: () -> Unit,
    viewModel: M16ShelterManageViewModel = viewModel(factory = M16ShelterManageViewModel.factory())
) {
    val profile by viewModel.profile.collectAsState()
    val message by viewModel.message.collectAsState()
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
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (profile == null) {
                LoadingState()
            } else {
                val p = profile!!
                Text(p.displayName, fontWeight = FontWeight.Bold)
                Text("Operativo: ${p.operationalStatus}")
                Text("Publicación: ${p.publicationStatus}")
                Text("Verificación: ${p.verificationStatus}")
                Text("Capacidad: ${p.capacity.currentOccupancy}/${p.capacity.totalCapacity}")
                Button(onClick = { viewModel.pause() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Pausar")
                }
                Button(onClick = { viewModel.activate() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reactivar")
                }
                Button(onClick = { viewModel.publish() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Publicar")
                }
                Button(onClick = { viewModel.unpublish() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Despublicar")
                }
                Button(onClick = { viewModel.requestVerification() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Solicitar verificación")
                }
            }
        }
    }
}
