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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.comunidapp.app.data.model.M15DischargeOutcome
import com.comunidapp.app.data.model.M15DischargeReason
import com.comunidapp.app.data.model.M15EvolutionEventType
import com.comunidapp.app.data.model.M15ExpenseCategory
import com.comunidapp.app.data.model.M15FosterPlacementStatus
import com.comunidapp.app.data.model.M15HelpRequestType
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M15DischargeViewModel
import com.comunidapp.app.viewmodel.M15EvolutionFormViewModel
import com.comunidapp.app.viewmodel.M15EvolutionListViewModel
import com.comunidapp.app.viewmodel.M15ExpenseFormViewModel
import com.comunidapp.app.viewmodel.M15ExpensesViewModel
import com.comunidapp.app.viewmodel.M15HelpFormViewModel
import com.comunidapp.app.viewmodel.M15HelpListViewModel
import com.comunidapp.app.viewmodel.M15PlacementDetailViewModel
import com.comunidapp.app.viewmodel.M15PlacementsListViewModel

@Composable
fun M15PlacementsListScreen(
    onNavigateBack: () -> Unit,
    onPlacementClick: (String) -> Unit,
    viewModel: M15PlacementsListViewModel = viewModel(factory = M15PlacementsListViewModel.factory())
) {
    val placements by viewModel.placements.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Mis alojamientos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            if (placements.isEmpty()) {
                EmptyState(title = "Sin alojamientos", message = "No hay tránsitos activos.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(placements, key = { it.id }) { p ->
                        Card(
                            Modifier.fillMaxWidth().clickable { onPlacementClick(p.id) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(p.petName ?: p.petId, fontWeight = FontWeight.Bold)
                                Text("Estado: ${p.status}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M15PlacementDetailScreen(
    placementId: String,
    onNavigateBack: () -> Unit,
    onEvolution: () -> Unit,
    onDischarge: () -> Unit,
    onExpenses: () -> Unit,
    onHelp: () -> Unit,
    viewModel: M15PlacementDetailViewModel = viewModel(factory = M15PlacementDetailViewModel.factory(placementId))
) {
    val placement by viewModel.placement.collectAsState()
    val message by viewModel.message.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Alojamiento", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            placement?.let { p ->
                Text(p.petName ?: p.petId, style = MaterialTheme.typography.titleLarge)
                Text("Estado: ${p.status}")
                p.dischargeOutcome?.let { Text("Egreso: $it") }
                val open = p.status == M15FosterPlacementStatus.ACTIVE ||
                    p.status == M15FosterPlacementStatus.RESERVED
                Button(onClick = onEvolution, enabled = open, modifier = Modifier.fillMaxWidth()) {
                    Text("Evolución")
                }
                Button(onClick = onExpenses, enabled = open, modifier = Modifier.fillMaxWidth()) {
                    Text("Gastos")
                }
                Button(onClick = onHelp, enabled = open, modifier = Modifier.fillMaxWidth()) {
                    Text("Pedidos de ayuda")
                }
                Button(onClick = onDischarge, enabled = open, modifier = Modifier.fillMaxWidth()) {
                    Text("Egreso")
                }
            } ?: LoadingState()
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun M15EvolutionListScreen(
    placementId: String,
    onNavigateBack: () -> Unit,
    onAdd: () -> Unit,
    viewModel: M15EvolutionListViewModel = viewModel(factory = M15EvolutionListViewModel.factory(placementId))
) {
    val items by viewModel.items.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Evolución", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Agregar registro") }
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) EmptyState(title = "Sin registros", message = "Todavía no hay evolución.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(e.eventType.name, fontWeight = FontWeight.Bold)
                            Text(e.summary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M15EvolutionFormScreen(
    placementId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: M15EvolutionFormViewModel = viewModel(factory = M15EvolutionFormViewModel.factory(placementId))
) {
    var summary by remember { mutableStateOf("") }
    val message by viewModel.message.collectAsState()
    val saved by viewModel.saved.collectAsState()
    LaunchedEffect(saved) { if (saved) onSaved() }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Nueva evolución", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(summary, { summary = it }, label = { Text("Resumen") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    viewModel.submit(summary, M15EvolutionEventType.GENERAL_UPDATE, false)
                },
                enabled = summary.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun M15DischargeScreen(
    placementId: String,
    onNavigateBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: M15DischargeViewModel = viewModel(factory = M15DischargeViewModel.factory(placementId))
) {
    var note by remember { mutableStateOf("") }
    val message by viewModel.message.collectAsState()
    val completed by viewModel.completed.collectAsState()
    LaunchedEffect(completed) { if (completed) onCompleted() }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Egreso", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(note, { note = it }, label = { Text("Nota privada") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    viewModel.discharge(M15DischargeReason.RETURNED_TO_RESPONSIBLE, M15DischargeOutcome.COMPLETED, note)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Finalizar — retorno al responsable") }
            Button(
                onClick = {
                    viewModel.discharge(M15DischargeReason.EMERGENCY, M15DischargeOutcome.INTERRUPTED, note)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Interrumpir — emergencia") }
            Button(
                onClick = {
                    viewModel.discharge(M15DischargeReason.OTHER, M15DischargeOutcome.CANCELLED, note)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancelar reserva") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun M15ExpensesScreen(
    placementId: String,
    onNavigateBack: () -> Unit,
    onAdd: () -> Unit,
    viewModel: M15ExpensesViewModel = viewModel(factory = M15ExpensesViewModel.factory(placementId))
) {
    val items by viewModel.items.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Gastos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Registrar gasto") }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${e.category} — ${e.amountMinor} ${e.currency}")
                            Text(e.description)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M15ExpenseFormScreen(
    placementId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: M15ExpenseFormViewModel = viewModel(factory = M15ExpenseFormViewModel.factory(placementId))
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val message by viewModel.message.collectAsState()
    val saved by viewModel.saved.collectAsState()
    LaunchedEffect(saved) { if (saved) onSaved() }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Nuevo gasto", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() } }, label = { Text("Importe (centavos)") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    viewModel.submit(description, amount.toLongOrNull() ?: 0L, M15ExpenseCategory.FOOD)
                },
                enabled = description.isNotBlank() && (amount.toLongOrNull() ?: 0L) > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun M15HelpListScreen(
    placementId: String,
    onNavigateBack: () -> Unit,
    onAdd: () -> Unit,
    viewModel: M15HelpListViewModel = viewModel(factory = M15HelpListViewModel.factory(placementId))
) {
    val items by viewModel.items.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Ayuda", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Nuevo pedido") }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { h ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(h.title, fontWeight = FontWeight.Bold)
                            Text("${h.type} — ${h.status}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M15HelpFormScreen(
    placementId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: M15HelpFormViewModel = viewModel(factory = M15HelpFormViewModel.factory(placementId))
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val message by viewModel.message.collectAsState()
    val saved by viewModel.saved.collectAsState()
    LaunchedEffect(saved) { if (saved) onSaved() }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Pedido de ayuda", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { viewModel.submit(title, description, M15HelpRequestType.FOOD) },
                enabled = title.isNotBlank() && description.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Publicar") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
