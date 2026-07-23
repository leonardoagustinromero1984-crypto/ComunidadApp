package com.comunidapp.app.ui.screens.foster

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.comunidapp.app.data.model.FosterExpenseCategory
import com.comunidapp.app.data.model.FosterEvolutionVisibility
import com.comunidapp.app.data.model.FosterHealthStatus
import com.comunidapp.app.data.model.FosterHelpStatus
import com.comunidapp.app.data.model.FosterHelpType
import com.comunidapp.app.data.model.FosterPlacementEndReason
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.FosterCarePanelUiState
import com.comunidapp.app.viewmodel.FosterCompleteUiState
import com.comunidapp.app.viewmodel.FosterCompleteViewModel
import com.comunidapp.app.viewmodel.FosterEvolutionFormViewModel
import com.comunidapp.app.viewmodel.FosterEvolutionListViewModel
import com.comunidapp.app.viewmodel.FosterEvolutionUiState
import com.comunidapp.app.viewmodel.FosterExpenseFormViewModel
import com.comunidapp.app.viewmodel.FosterExpensesUiState
import com.comunidapp.app.viewmodel.FosterExpensesViewModel
import com.comunidapp.app.viewmodel.FosterHelpDetailUiState
import com.comunidapp.app.viewmodel.FosterHelpDetailViewModel
import com.comunidapp.app.viewmodel.FosterHelpFormViewModel
import com.comunidapp.app.viewmodel.FosterHelpListUiState
import com.comunidapp.app.viewmodel.FosterHelpListViewModel
import com.comunidapp.app.viewmodel.FosterHistoryViewModel
import com.comunidapp.app.viewmodel.FosterPlacementManagementViewModel

@Composable
fun FosterPlacementManagementScreen(
    onNavigateBack: () -> Unit,
    onExpenses: (String) -> Unit,
    onEvolution: (String) -> Unit,
    onHelp: (String) -> Unit,
    onComplete: (String) -> Unit,
    viewModel: FosterPlacementManagementViewModel
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Gestión del tránsito",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val s = state) {
            FosterCarePanelUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            FosterCarePanelUiState.Empty -> EmptyState(
                title = "Sin alojamiento",
                contentModifier = Modifier.padding(padding)
            )
            is FosterCarePanelUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding),
                onRetry = { viewModel.reload() }
            )
            is FosterCarePanelUiState.Content -> {
                val p = s.placement
                Column(
                    Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Estado: ${p.status.name}", fontWeight = FontWeight.SemiBold)
                    Text("Mascota: ${p.petName ?: p.petId}")
                    Text("Hogar: ${p.fosterHomeId}")
                    Text("Cuidador temporal: ${p.fosterUserId}")
                    Text("Responsable principal se conserva (PRINCIPAL).")
                    p.temporaryResponsibilityId?.let {
                        Text("Custodia temporal M08: $it")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onExpenses(p.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Gastos") }
                    OutlinedButton(
                        onClick = { onEvolution(p.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Evolución") }
                    OutlinedButton(
                        onClick = { onHelp(p.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Pedidos de ayuda") }
                    if (p.status == FosterPlacementStatus.ACTIVE) {
                        Button(
                            onClick = { onComplete(p.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Finalizar tránsito") }
                    }
                }
            }
        }
    }
}

@Composable
fun FosterExpensesScreen(
    onNavigateBack: () -> Unit,
    onAdd: () -> Unit,
    viewModel: FosterExpensesViewModel
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Gastos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            FosterExpensesUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            FosterExpensesUiState.Empty -> Column(Modifier.padding(padding).padding(16.dp)) {
                EmptyState(title = "Sin gastos registrados.")
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Registrar gasto") }
            }
            is FosterExpensesUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is FosterExpensesUiState.Content -> Column(Modifier.padding(padding)) {
                Button(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) { Text("Registrar gasto") }
                LazyColumn(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.items, key = { it.id }) { e ->
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            Text("${e.category.name} · ${e.amountMinor} ${e.currency}", fontWeight = FontWeight.SemiBold)
                            Text(e.description)
                            Text("Comprobante: ${if (e.receiptRef.isNullOrBlank()) "no" else "sí"}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FosterExpenseFormScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: FosterExpenseFormViewModel
) {
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("ARS") }
    var receiptRef by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(FosterExpenseCategory.FOOD) }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Nuevo gasto", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FosterExpenseCategory.entries.filter { it != FosterExpenseCategory.UNKNOWN }.forEach { c ->
                RowRadio(c.name, category == c) { category = c }
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Importe (centavos / minor)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it },
                label = { Text("Moneda") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = receiptRef,
                onValueChange = { receiptRef = it },
                label = { Text("Comprobante (m05:// o file_asset:)") },
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !submitting,
                onClick = {
                    viewModel.submit(
                        category = category,
                        description = description,
                        amountMinor = amountText.toLongOrNull() ?: 0L,
                        currency = currency,
                        receiptRef = receiptRef.ifBlank { null }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Guardando…" else "Guardar") }
        }
    }
}

@Composable
fun FosterEvolutionScreen(
    onNavigateBack: () -> Unit,
    onAdd: () -> Unit,
    viewModel: FosterEvolutionListViewModel
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Evolución", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            FosterEvolutionUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            FosterEvolutionUiState.Empty -> Column(Modifier.padding(padding).padding(16.dp)) {
                EmptyState(title = "Sin evoluciones.")
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Agregar") }
            }
            is FosterEvolutionUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is FosterEvolutionUiState.Content -> Column(Modifier.padding(padding)) {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("Agregar evolución") }
                LazyColumn(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.items, key = { it.id }) { e ->
                        Column(Modifier.padding(8.dp)) {
                            Text(e.title, fontWeight = FontWeight.SemiBold)
                            Text(e.description)
                            Text("Salud: ${e.healthStatus.name}")
                            e.weightGrams?.let { Text("Peso: $it g") }
                            Text("Visibilidad: ${e.visibility.name}")
                            if (e.mediaRefs.isNotEmpty()) {
                                Text("Medios: ${e.mediaRefs.size}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FosterEvolutionFormScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: FosterEvolutionFormViewModel
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var mediaRef by remember { mutableStateOf("") }
    var health by remember { mutableStateOf(FosterHealthStatus.GOOD) }
    var visibility by remember { mutableStateOf(FosterEvolutionVisibility.PARTICIPANTS) }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved() } }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Nueva evolución", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                description,
                { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            FosterHealthStatus.entries.filter { it != FosterHealthStatus.UNKNOWN }.forEach { h ->
                RowRadio(h.name, health == h) { health = h }
            }
            OutlinedTextField(
                weight,
                { weight = it.filter { ch -> ch.isDigit() } },
                label = { Text("Peso (gramos)") },
                modifier = Modifier.fillMaxWidth()
            )
            FosterEvolutionVisibility.entries.filter { it != FosterEvolutionVisibility.UNKNOWN }.forEach { v ->
                RowRadio(v.name, visibility == v) { visibility = v }
            }
            OutlinedTextField(
                mediaRef,
                { mediaRef = it },
                label = { Text("Media (m05:// o file_asset:)") },
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !submitting,
                onClick = {
                    viewModel.submit(
                        title = title,
                        description = description,
                        healthStatus = health,
                        weightGrams = weight.toIntOrNull(),
                        mediaRefs = listOfNotNull(mediaRef.ifBlank { null }),
                        visibility = visibility
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Guardando…" else "Guardar") }
        }
    }
}

@Composable
fun FosterHelpScreen(
    onNavigateBack: () -> Unit,
    onAdd: () -> Unit,
    onDetail: (String) -> Unit,
    viewModel: FosterHelpListViewModel
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Pedidos de ayuda", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            FosterHelpListUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            FosterHelpListUiState.Empty -> Column(Modifier.padding(padding).padding(16.dp)) {
                EmptyState(title = "Sin pedidos.")
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Crear pedido") }
            }
            is FosterHelpListUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is FosterHelpListUiState.Content -> Column(Modifier.padding(padding)) {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Crear pedido")
                }
                LazyColumn(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.items, key = { it.id }) { h ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onDetail(h.id) }
                                .padding(8.dp)
                        ) {
                            Text("${h.type.name} · ${h.status.name}", fontWeight = FontWeight.SemiBold)
                            Text(h.title)
                            Text("Urgencia: ${h.urgency.name}")
                            if (h.type == FosterHelpType.MONEY && h.targetAmountMinor != null) {
                                Text("Progreso: ${h.receivedAmountMinor}/${h.targetAmountMinor} ${h.currency ?: ""}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FosterHelpFormScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: FosterHelpFormViewModel
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("ARS") }
    var qty by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(FosterHelpType.FOOD) }
    var urgency by remember { mutableStateOf(FosterUrgency.NORMAL) }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved() } }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Nuevo pedido", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FosterHelpType.entries.filter { it != FosterHelpType.UNKNOWN }.forEach { t ->
                RowRadio(t.name, type == t) { type = t }
            }
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                description,
                { description = it },
                label = { Text("Descripción (sin datos bancarios)") },
                modifier = Modifier.fillMaxWidth()
            )
            if (type == FosterHelpType.MONEY) {
                OutlinedTextField(
                    target,
                    { target = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Objetivo (minor)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    currency,
                    { currency = it },
                    label = { Text("Moneda") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    qty,
                    { qty = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Cantidad necesaria") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            FosterUrgency.entries.forEach { u ->
                RowRadio(u.name, urgency == u) { urgency = u }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !submitting,
                onClick = {
                    viewModel.submit(
                        type = type,
                        title = title,
                        description = description,
                        targetAmountMinor = target.toLongOrNull(),
                        currency = currency.ifBlank { null },
                        quantityNeeded = qty.toIntOrNull(),
                        urgency = urgency
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Guardando…" else "Publicar") }
        }
    }
}

@Composable
fun FosterHelpDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: FosterHelpDetailViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    var contribDesc by remember { mutableStateOf("") }
    var contribAmount by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Pedido de ayuda", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            FosterHelpDetailUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is FosterHelpDetailUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is FosterHelpDetailUiState.Content -> {
                val h = s.data.request
                Column(
                    Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(h.title, style = MaterialTheme.typography.titleMedium)
                    Text(h.description)
                    Text("Tipo: ${h.type.name} · ${h.status.name} · ${h.urgency.name}")
                    if (h.targetAmountMinor != null) {
                        Text("Progreso: ${h.receivedAmountMinor}/${h.targetAmountMinor}")
                    }
                    Text("Aportes: ${s.data.contributions.size}")
                    s.data.contributions.forEach { c ->
                        Text("· ${c.description} (${c.status.name})")
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (h.status.isEditable) {
                        OutlinedTextField(
                            contribDesc,
                            { contribDesc = it },
                            label = { Text("Aporte recibido") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            contribAmount,
                            { contribAmount = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Monto / cantidad") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = !busy,
                            onClick = {
                                viewModel.recordContribution(
                                    description = contribDesc,
                                    amountMinor = if (h.type == FosterHelpType.MONEY) {
                                        contribAmount.toLongOrNull()
                                    } else null,
                                    quantity = if (h.type != FosterHelpType.MONEY) {
                                        contribAmount.toIntOrNull()
                                    } else null
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Registrar aporte") }
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { viewModel.changeStatus(FosterHelpStatus.FULFILLED) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Marcar cumplido") }
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { viewModel.changeStatus(FosterHelpStatus.CANCELLED) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancelar pedido") }
                    }
                }
            }
        }
    }
}

@Composable
fun FosterCompleteScreen(
    onNavigateBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: FosterCompleteViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    var reason by remember { mutableStateOf(FosterPlacementEndReason.RETURNED_TO_OWNER) }
    var notes by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.completed.collect { onCompleted() } }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Finalizar tránsito", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            FosterCompleteUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is FosterCompleteUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is FosterCompleteUiState.Content -> {
                val sum = s.summary
                val p = sum.placement
                Column(
                    Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Resumen", fontWeight = FontWeight.SemiBold)
                    Text("Mascota: ${p.petName ?: p.petId}")
                    Text("Hogar: ${p.fosterHomeId}")
                    Text("Principal: ${sum.principalUserId ?: "—"}")
                    Text("Inicio: ${p.startedAt}")
                    Text("Gastos: ${sum.expenseCount} · Evoluciones: ${sum.evolutionCount}")
                    Text("Pedidos abiertos: ${sum.openHelpCount} (se cancelarán)")
                    Text("Se revoca TEMPORARY_CUSTODIAN y se conserva PRINCIPAL.")
                    FosterPlacementEndReason.entries
                        .filter { it != FosterPlacementEndReason.UNKNOWN && it != FosterPlacementEndReason.CANCELLED_BEFORE_START }
                        .forEach { r -> RowRadio(r.name, reason == r) { reason = r } }
                    OutlinedTextField(
                        notes,
                        { notes = it },
                        label = { Text("Notas finales") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        enabled = !submitting && p.status == FosterPlacementStatus.ACTIVE,
                        onClick = { confirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (submitting) "Finalizando…" else "Confirmar finalización") }
                }
                if (confirm) {
                    AlertDialog(
                        onDismissRequest = { confirm = false },
                        title = { Text("¿Finalizar tránsito?") },
                        text = { Text("Esta acción no se puede deshacer. Se liberará capacidad y se cerrarán pedidos abiertos.") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirm = false
                                viewModel.complete(reason, notes.ifBlank { null })
                            }) { Text("Finalizar") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirm = false }) { Text("Cancelar") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FosterHistoryScreen(
    onNavigateBack: () -> Unit,
    onPlacementClick: (String) -> Unit,
    viewModel: FosterHistoryViewModel = viewModel(factory = FosterHistoryViewModel.factory())
) {
    val history by viewModel.history.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Historial de tránsitos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        if (history.isEmpty()) {
            EmptyState(title = "Sin historial.", contentModifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history, key = { it.id }) { p ->
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
                        p.endReason?.let { Text("Motivo: $it") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}
