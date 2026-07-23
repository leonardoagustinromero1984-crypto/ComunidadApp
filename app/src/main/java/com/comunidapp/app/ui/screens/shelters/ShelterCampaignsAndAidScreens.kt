package com.comunidapp.app.ui.screens.shelters

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.ShelterCampaignCategory
import com.comunidapp.app.data.model.ShelterCampaignStatus
import com.comunidapp.app.data.model.ShelterCampaignVisibility
import com.comunidapp.app.data.model.ShelterSupplyCategory
import com.comunidapp.app.data.model.ShelterSupplyContributionStatus
import com.comunidapp.app.data.model.ShelterSupplyPriority
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.ShelterCampaignDetailUiState
import com.comunidapp.app.viewmodel.ShelterCampaignDetailViewModel
import com.comunidapp.app.viewmodel.ShelterCampaignFormViewModel
import com.comunidapp.app.viewmodel.ShelterCampaignUpdateFormViewModel
import com.comunidapp.app.viewmodel.ShelterCampaignsViewModel
import com.comunidapp.app.viewmodel.ShelterPublicCampaignsUiState
import com.comunidapp.app.viewmodel.ShelterPublicCampaignsViewModel
import com.comunidapp.app.viewmodel.ShelterPublicSupplyRequestsUiState
import com.comunidapp.app.viewmodel.ShelterPublicSupplyRequestsViewModel
import com.comunidapp.app.viewmodel.ShelterSupplyContributeViewModel
import com.comunidapp.app.viewmodel.ShelterSupplyContributionsViewModel
import com.comunidapp.app.viewmodel.ShelterSupplyRequestDetailUiState
import com.comunidapp.app.viewmodel.ShelterSupplyRequestDetailViewModel
import com.comunidapp.app.viewmodel.ShelterSupplyRequestFormViewModel
import com.comunidapp.app.viewmodel.ShelterSupplyRequestsViewModel

@Composable
fun ShelterPublicCampaignsScreen(
    onNavigateBack: () -> Unit,
    onCampaignClick: (String) -> Unit,
    viewModel: ShelterPublicCampaignsViewModel = viewModel(factory = ShelterPublicCampaignsViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Campañas públicas", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            when (val s = state) {
                ShelterPublicCampaignsUiState.Loading -> LoadingState()
                ShelterPublicCampaignsUiState.Empty -> EmptyState(title = "No hay campañas activas.")
                is ShelterPublicCampaignsUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::refresh)
                is ShelterPublicCampaignsUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.items, key = { it.id }) { item ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onCampaignClick(item.id) }.padding(8.dp)
                        ) {
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                            item.shelterDisplayName?.let { Text(it) }
                            Text("${item.category.name} · ${item.status.name}")
                            Text(item.description)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterCampaignsScreen(
    onNavigateBack: () -> Unit,
    onCreate: () -> Unit,
    onDetail: (String) -> Unit,
    viewModel: ShelterCampaignsViewModel
) {
    val campaigns by viewModel.campaigns.collectAsState()
    val error by viewModel.error.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Campañas", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Nueva campaña") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (campaigns.isEmpty()) EmptyState(title = "Sin campañas.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(campaigns, key = { it.id }) { c ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onDetail(c.id) }.padding(8.dp)
                    ) {
                        Text("${c.title} · ${c.status.name}", fontWeight = FontWeight.SemiBold)
                        Text("${c.category.name} · ${c.visibility.name}")
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterCampaignDetailScreen(
    onNavigateBack: () -> Unit,
    onAddUpdate: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    viewModel: ShelterCampaignDetailViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Detalle de campaña", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            ShelterCampaignDetailUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is ShelterCampaignDetailUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is ShelterCampaignDetailUiState.Content -> {
                val c = s.data.campaign
                Column(
                    Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(c.title, style = MaterialTheme.typography.titleLarge)
                    Text(c.description)
                    Text("Estado: ${c.status.name} · ${c.category.name} · ${c.visibility.name}")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(
                        onClick = { onEdit(c.shelterProfileId, c.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Editar") }
                    OutlinedButton(
                        onClick = { onAddUpdate(c.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Agregar novedad") }
                    when (c.status) {
                        ShelterCampaignStatus.DRAFT -> OutlinedButton(
                            enabled = !busy,
                            onClick = { viewModel.changeStatus(ShelterCampaignStatus.ACTIVE) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Activar") }
                        ShelterCampaignStatus.ACTIVE -> {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { viewModel.changeStatus(ShelterCampaignStatus.PAUSED) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Pausar") }
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { viewModel.changeStatus(ShelterCampaignStatus.COMPLETED) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Completar") }
                        }
                        ShelterCampaignStatus.PAUSED -> {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { viewModel.changeStatus(ShelterCampaignStatus.ACTIVE) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Reanudar") }
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { viewModel.changeStatus(ShelterCampaignStatus.COMPLETED) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Completar") }
                        }
                        else -> Unit
                    }
                    if (c.status != ShelterCampaignStatus.COMPLETED &&
                        c.status != ShelterCampaignStatus.CANCELLED
                    ) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { viewModel.changeStatus(ShelterCampaignStatus.CANCELLED) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancelar campaña") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Novedades", fontWeight = FontWeight.SemiBold)
                    if (s.data.updates.isEmpty()) {
                        Text("Sin novedades publicadas.")
                    } else {
                        s.data.updates.forEach { u ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text("${u.visibility.name} · ${u.message}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterCampaignFormScreen(
    shelterId: String,
    editCampaignId: String? = null,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ShelterCampaignFormViewModel
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ShelterCampaignCategory.OTHER) }
    var visibility by remember { mutableStateOf(ShelterCampaignVisibility.PUBLIC) }
    var activate by remember { mutableStateOf(false) }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val existing by viewModel.existing.collectAsState()
    LaunchedEffect(existing) {
        existing?.let {
            title = it.title
            description = it.description
            category = it.category
            visibility = it.visibility
        }
    }
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved(it) } }
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = if (editCampaignId == null) "Nueva campaña" else "Editar campaña",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                description,
                { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Categoría")
            ShelterCampaignCategory.entries.filter { it != ShelterCampaignCategory.UNKNOWN }.forEach { cat ->
                AidRowRadio(cat.name, category == cat) { category = cat }
            }
            Text("Visibilidad")
            ShelterCampaignVisibility.entries.filter { it != ShelterCampaignVisibility.UNKNOWN }.forEach { vis ->
                AidRowRadio(vis.name, visibility == vis) { visibility = vis }
            }
            if (editCampaignId == null) {
                AidRowCheck("Activar al crear", activate) { activate = it }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !submitting,
                onClick = {
                    if (editCampaignId == null) {
                        viewModel.create(title, description, category, visibility, activate)
                    } else {
                        viewModel.update(title, description, category, visibility)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Guardando…" else "Guardar") }
        }
    }
}

@Composable
fun ShelterCampaignUpdateScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ShelterCampaignUpdateFormViewModel
) {
    var message by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(ShelterCampaignVisibility.PUBLIC) }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved() } }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Nueva novedad", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                message,
                { message = it },
                label = { Text("Mensaje") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Visibilidad")
            ShelterCampaignVisibility.entries.filter { it != ShelterCampaignVisibility.UNKNOWN }.forEach { vis ->
                AidRowRadio(vis.name, visibility == vis) { visibility = vis }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !submitting,
                onClick = { viewModel.addUpdate(visibility, message) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Publicando…" else "Publicar") }
        }
    }
}

@Composable
fun ShelterPublicSupplyRequestsScreen(
    onNavigateBack: () -> Unit,
    onRequestClick: (String) -> Unit,
    onContribute: (String) -> Unit,
    viewModel: ShelterPublicSupplyRequestsViewModel = viewModel(factory = ShelterPublicSupplyRequestsViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Pedidos de insumos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            when (val s = state) {
                ShelterPublicSupplyRequestsUiState.Loading -> LoadingState()
                ShelterPublicSupplyRequestsUiState.Empty -> EmptyState(title = "No hay pedidos abiertos.")
                is ShelterPublicSupplyRequestsUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::refresh)
                is ShelterPublicSupplyRequestsUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.items, key = { it.id }) { item ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onRequestClick(item.id) }.padding(8.dp)
                        ) {
                            Text(item.itemName, fontWeight = FontWeight.SemiBold)
                            item.shelterDisplayName?.let { Text(it) }
                            Text(
                                "Solicitado: ${item.quantityRequested} ${item.unitText} · " +
                                    "Comprometido: ${item.quantityCommitted} · " +
                                    "Recibido: ${item.quantityReceived}"
                            )
                            Text("Prioridad: ${item.priority.name} · Estado: ${item.status.name}")
                            item.publicNotes?.let { Text(it) }
                            OutlinedButton(onClick = { onContribute(item.id) }) { Text("Comprometer") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterSupplyRequestsScreen(
    onNavigateBack: () -> Unit,
    onCreate: () -> Unit,
    onDetail: (String) -> Unit,
    viewModel: ShelterSupplyRequestsViewModel
) {
    val requests by viewModel.requests.collectAsState()
    val error by viewModel.error.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Pedidos de insumos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Nuevo pedido") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (requests.isEmpty()) EmptyState(title = "Sin pedidos.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(requests, key = { it.id }) { r ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onDetail(r.id) }.padding(8.dp)
                    ) {
                        Text("${r.itemName} · ${r.status.name}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Solicitado: ${r.quantityRequested} ${r.unitText} · " +
                                "Comprometido: ${r.quantityCommitted} · Recibido: ${r.quantityReceived}"
                        )
                        Text("Prioridad: ${r.priority.name}")
                    }
                }
            }
        }
    }
}

@Composable
fun ShelterSupplyRequestDetailScreen(
    onNavigateBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    onContributions: (String) -> Unit,
    onContribute: (String) -> Unit,
    viewModel: ShelterSupplyRequestDetailViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(Unit) { viewModel.cancelled.collect { onNavigateBack() } }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Detalle del pedido", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            ShelterSupplyRequestDetailUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is ShelterSupplyRequestDetailUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is ShelterSupplyRequestDetailUiState.Content -> {
                val r = s.data.request
                Column(
                    Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(r.itemName, style = MaterialTheme.typography.titleLarge)
                    r.description?.let { Text(it) }
                    Text(
                        "Solicitado: ${r.quantityRequested} ${r.unitText} · " +
                            "Comprometido: ${r.quantityCommitted} · Recibido: ${r.quantityReceived}"
                    )
                    Text("Prioridad: ${r.priority.name} · Estado: ${r.status.name}")
                    r.publicNotes?.let { Text("Notas públicas: $it") }
                    r.internalNotes?.let { Text("Notas internas: $it") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(
                        onClick = { onEdit(r.shelterProfileId, r.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Editar") }
                    OutlinedButton(
                        onClick = { onContributions(r.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ver aportes") }
                    if (r.status.isOpen) {
                        OutlinedButton(
                            onClick = { onContribute(r.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Comprometer insumos") }
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { viewModel.cancel() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancelar pedido") }
                    }
                    if (s.data.contributions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Aportes (${s.data.contributions.size})", fontWeight = FontWeight.SemiBold)
                        s.data.contributions.forEach { c ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text("${c.contributorUserId} · ${c.quantityCommitted} · ${c.status.name}")
                                if (c.status == ShelterSupplyContributionStatus.PLEDGED) {
                                    OutlinedButton(
                                        enabled = !busy,
                                        onClick = { viewModel.confirmContribution(c.id) }
                                    ) { Text("Confirmar") }
                                }
                                if (c.status == ShelterSupplyContributionStatus.CONFIRMED ||
                                    c.status == ShelterSupplyContributionStatus.PARTIALLY_RECEIVED
                                ) {
                                    OutlinedButton(
                                        enabled = !busy,
                                        onClick = {
                                            viewModel.recordReceipt(
                                                c.id,
                                                c.quantityCommitted - c.quantityReceived
                                            )
                                        }
                                    ) { Text("Registrar recepción") }
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
fun ShelterSupplyRequestFormScreen(
    shelterId: String,
    editRequestId: String? = null,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ShelterSupplyRequestFormViewModel
) {
    var category by remember { mutableStateOf(ShelterSupplyCategory.OTHER) }
    var itemName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("unidades") }
    var priority by remember { mutableStateOf(ShelterSupplyPriority.NORMAL) }
    var publicNotes by remember { mutableStateOf("") }
    var internalNotes by remember { mutableStateOf("") }
    var publishOpen by remember { mutableStateOf(false) }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val existing by viewModel.existing.collectAsState()
    LaunchedEffect(existing) {
        existing?.let {
            category = it.category
            itemName = it.itemName
            description = it.description.orEmpty()
            quantity = it.quantityRequested.toString()
            unit = it.unitText
            priority = it.priority
            publicNotes = it.publicNotes.orEmpty()
            internalNotes = it.internalNotes.orEmpty()
        }
    }
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved(it) } }
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = if (editRequestId == null) "Nuevo pedido" else "Editar pedido",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(itemName, { itemName = it }, label = { Text("Ítem") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                description,
                { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                quantity,
                { quantity = it.filter { ch -> ch.isDigit() } },
                label = { Text("Cantidad") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(unit, { unit = it }, label = { Text("Unidad") }, modifier = Modifier.fillMaxWidth())
            Text("Categoría")
            ShelterSupplyCategory.entries.filter { it != ShelterSupplyCategory.UNKNOWN }.forEach { cat ->
                AidRowRadio(cat.name, category == cat) { category = cat }
            }
            Text("Prioridad")
            ShelterSupplyPriority.entries.filter { it != ShelterSupplyPriority.UNKNOWN }.forEach { p ->
                AidRowRadio(p.name, priority == p) { priority = p }
            }
            OutlinedTextField(
                publicNotes,
                { publicNotes = it },
                label = { Text("Notas públicas") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                internalNotes,
                { internalNotes = it },
                label = { Text("Notas internas (privadas)") },
                modifier = Modifier.fillMaxWidth()
            )
            if (editRequestId == null) {
                AidRowCheck("Publicar abierto", publishOpen) { publishOpen = it }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !submitting,
                onClick = {
                    val qty = quantity.toIntOrNull() ?: 0
                    if (editRequestId == null) {
                        viewModel.create(
                            category,
                            itemName,
                            description.ifBlank { null },
                            qty,
                            unit,
                            priority,
                            publicNotes.ifBlank { null },
                            internalNotes.ifBlank { null },
                            publishOpen
                        )
                    } else {
                        viewModel.update(
                            category,
                            itemName,
                            description.ifBlank { null },
                            qty,
                            unit,
                            priority,
                            publicNotes.ifBlank { null },
                            internalNotes.ifBlank { null }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Guardando…" else "Guardar") }
        }
    }
}

@Composable
fun ShelterSupplyContributeScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ShelterSupplyContributeViewModel
) {
    var quantity by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(Unit) { viewModel.saved.collect { onSaved() } }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Comprometer insumos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                quantity,
                { quantity = it.filter { ch -> ch.isDigit() } },
                label = { Text("Cantidad") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !submitting,
                onClick = {
                    viewModel.pledge(quantity.toIntOrNull() ?: 0, notes.ifBlank { null })
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Enviando…" else "Comprometer") }
        }
    }
}

@Composable
fun ShelterSupplyContributionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShelterSupplyContributionsViewModel
) {
    val contributions by viewModel.contributions.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Aportes", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (contributions.isEmpty()) EmptyState(title = "Sin aportes.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(contributions, key = { it.id }) { c ->
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            "${c.contributorUserId} · ${c.quantityCommitted} · ${c.status.name}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Recibido: ${c.quantityReceived}")
                        c.contributorNotes?.let { Text("Notas: $it") }
                        c.internalReceiptNotes?.let { Text("Recepción interna: $it") }
                        if (c.status == ShelterSupplyContributionStatus.PLEDGED) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = { viewModel.confirm(c.id) }
                            ) { Text("Confirmar") }
                        }
                        if (c.status == ShelterSupplyContributionStatus.CONFIRMED ||
                            c.status == ShelterSupplyContributionStatus.PARTIALLY_RECEIVED
                        ) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    viewModel.recordReceipt(c.id, c.quantityCommitted - c.quantityReceived)
                                }
                            ) { Text("Registrar recepción") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AidRowRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

@Composable
private fun AidRowCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
