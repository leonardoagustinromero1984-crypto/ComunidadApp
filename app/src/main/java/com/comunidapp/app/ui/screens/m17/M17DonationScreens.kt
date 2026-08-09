package com.comunidapp.app.ui.screens.m17

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.comunidapp.app.data.model.M17CampaignType
import com.comunidapp.app.data.model.M17MockOrganizations
import com.comunidapp.app.data.model.M17PublicCampaign
import com.comunidapp.app.data.repository.M17DonationValidators
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M17CampaignDetailViewModel
import com.comunidapp.app.viewmodel.M17CampaignEditUiState
import com.comunidapp.app.viewmodel.M17CampaignEditViewModel
import com.comunidapp.app.viewmodel.M17CampaignManageUiState
import com.comunidapp.app.viewmodel.M17CampaignManageViewModel
import com.comunidapp.app.viewmodel.M17CampaignsListUiState
import com.comunidapp.app.viewmodel.M17CampaignsListViewModel
import com.comunidapp.app.viewmodel.m17CampaignStatusLabel
import com.comunidapp.app.viewmodel.m17CampaignTypeLabel

@Composable
fun M17CampaignsListScreen(
    onNavigateBack: () -> Unit,
    onCampaignClick: (String) -> Unit,
    onManage: () -> Unit,
    onCreate: () -> Unit,
    viewModel: M17CampaignsListViewModel = viewModel(factory = M17CampaignsListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var query by remember(filter.query) { mutableStateOf(filter.query) }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Campañas solidarias", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Directorio público de campañas — sin datos financieros sensibles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.setQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar campaña") },
                singleLine = true
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter.withPetOnly, onClick = { viewModel.setWithPetOnly(!filter.withPetOnly) }, label = { Text("Con mascota") })
                FilterChip(selected = filter.nearGoalOnly, onClick = { viewModel.setNearGoalOnly(!filter.nearGoalOnly) }, label = { Text("Cerca del objetivo") })
                FilterChip(selected = filter.completedOnly, onClick = { viewModel.setCompletedOnly(!filter.completedOnly) }, label = { Text("Completadas") })
                FilterChip(selected = filter.type == M17CampaignType.MEDICAL, onClick = {
                    viewModel.setType(if (filter.type == M17CampaignType.MEDICAL) null else M17CampaignType.MEDICAL)
                }, label = { Text("Médicas") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.clearFilters() }) { Text("Limpiar filtros") }
                OutlinedButton(onClick = onManage) { Text("Administrar") }
                Button(onClick = onCreate) { Text("Nueva") }
            }
            when (val s = state) {
                M17CampaignsListUiState.Loading -> LoadingState()
                M17CampaignsListUiState.Empty -> EmptyState(
                    title = "Sin campañas",
                    message = "No hay campañas publicadas con estos filtros."
                )
                is M17CampaignsListUiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.load() })
                is M17CampaignsListUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.items, key = { it.id }) { item ->
                        M17CampaignCard(item, onClick = { onCampaignClick(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun M17CampaignCard(campaign: M17PublicCampaign, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(campaign.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(campaign.organizationDisplayName, style = MaterialTheme.typography.bodySmall)
            Text(m17CampaignTypeLabel(campaign.campaignType), style = MaterialTheme.typography.labelMedium)
            Text(
                M17DonationValidators.formatMoneyMinor(campaign.confirmedAmountMinor, campaign.currency) +
                    " / " + M17DonationValidators.formatMoneyMinor(campaign.goalAmountMinor, campaign.currency),
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { (campaign.progressPercent.coerceIn(0, 100) / 100f) },
                modifier = Modifier.fillMaxWidth()
            )
            campaign.reference.publicLocationText?.let {
                Text("📍 $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun M17CampaignDetailScreen(
    campaignId: String,
    onNavigateBack: () -> Unit,
    viewModel: M17CampaignDetailViewModel = viewModel(factory = M17CampaignDetailViewModel.factory(campaignId))
) {
    val campaign by viewModel.campaign.collectAsState()
    val contributions by viewModel.contributions.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        if (message != null) viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Detalle campaña", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when {
            loading -> LoadingState(contentModifier = Modifier.padding(padding))
            campaign == null -> ErrorState(
                message = "Campaña no disponible",
                contentModifier = Modifier.padding(padding)
            )
            else -> {
                val c = campaign!!
                Column(
                    Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(c.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(c.organizationDisplayName, style = MaterialTheme.typography.bodyMedium)
                    Text(c.description)
                    Text("Estado: ${m17CampaignStatusLabel(c.status)}")
                    Text(
                        "Recaudado confirmado: " + M17DonationValidators.formatMoneyMinor(c.confirmedAmountMinor, c.currency) +
                            " (${c.progressPercent}% del objetivo)"
                    )
                    LinearProgressIndicator(progress = { c.progressPercent.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                    c.reference.petPublicName?.let { Text("Mascota: $it", style = MaterialTheme.typography.bodySmall) }
                    c.reference.shelterPublicName?.let { Text("Refugio: $it", style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "Los pagos reales todavía no están habilitados en este bloque.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    if (c.publicUpdates.isNotEmpty()) {
                        Text("Actualizaciones", fontWeight = FontWeight.SemiBold)
                        c.publicUpdates.forEach { u -> Text("• ${u.message}", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (contributions.isNotEmpty()) {
                        Text("Contribuciones públicas", fontWeight = FontWeight.SemiBold)
                        contributions.forEach { co ->
                            Text(
                                "${co.donorLabel}: ${M17DonationValidators.formatMoneyMinor(co.amountMinor, co.currency)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.registerMockContribution(1_000_00) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Contribución de prueba — pagos reales aún no habilitados")
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
fun M17CampaignManageScreen(
    onNavigateBack: () -> Unit,
    onEditCampaign: (String) -> Unit,
    onCreate: () -> Unit,
    viewModel: M17CampaignManageViewModel = viewModel(factory = M17CampaignManageViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val selectedOrg by viewModel.selectedOrg.collectAsState()
    val message by viewModel.message.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Administrar campañas", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                M17MockOrganizations.MANAGE_ORGANIZATION_IDS.forEach { orgId ->
                    FilterChip(
                        selected = selectedOrg == orgId,
                        onClick = { viewModel.selectOrganization(orgId) },
                        label = { Text(orgId.removePrefix("org_")) }
                    )
                }
            }
            Button(onClick = onCreate) { Text("Nueva campaña") }
            when (val s = state) {
                M17CampaignManageUiState.Loading -> LoadingState()
                M17CampaignManageUiState.PermissionDenied -> ErrorState(message = "No tenés permiso para administrar esta organización.")
                M17CampaignManageUiState.NoCampaigns -> EmptyState(
                    title = "Sin campañas",
                    message = "No hay campañas para esta organización."
                )
                is M17CampaignManageUiState.Error -> ErrorState(message = s.message)
                is M17CampaignManageUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.campaigns, key = { it.id }) { c ->
                        val summary = s.summaryById[c.id]
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(c.title, fontWeight = FontWeight.Bold)
                                Text("${m17CampaignStatusLabel(c.status)} · ${m17CampaignTypeLabel(c.campaignType)}")
                                summary?.let {
                                    Text(
                                        M17DonationValidators.formatMoneyMinor(it.confirmedAmountMinor, it.currency) +
                                            " / " + M17DonationValidators.formatMoneyMinor(it.goalAmountMinor, it.currency)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onEditCampaign(c.id) }) { Text("Editar") }
                                    if (c.status == com.comunidapp.app.data.model.M17CampaignStatus.DRAFT) {
                                        Button(onClick = { viewModel.publish(c.id) }) { Text("Publicar") }
                                    }
                                    if (c.status == com.comunidapp.app.data.model.M17CampaignStatus.PUBLISHED) {
                                        OutlinedButton(onClick = { viewModel.pause(c.id) }) { Text("Pausar") }
                                        OutlinedButton(onClick = { viewModel.complete(c.id) }) { Text("Completar") }
                                    }
                                    if (!c.status.isTerminal) {
                                        OutlinedButton(onClick = { viewModel.cancel(c.id) }) { Text("Cancelar") }
                                    }
                                }
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
fun M17CampaignEditScreen(
    campaignId: String?,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: M17CampaignEditViewModel = viewModel(factory = M17CampaignEditViewModel.factory(campaignId))
) {
    val draft by viewModel.draft.collectAsState()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state) {
        if (state is M17CampaignEditUiState.Saved) {
            onSaved((state as M17CampaignEditUiState.Saved).campaignId)
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = if (campaignId == null) "Nueva campaña" else "Editar campaña",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { viewModel.updateDraft { d -> d.copy(title = it) } },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { viewModel.updateDraft { d -> d.copy(description = it) } },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            OutlinedTextField(
                value = (draft.goalAmountMinor / 100).toString(),
                onValueChange = { v ->
                    v.toLongOrNull()?.let { major ->
                        viewModel.updateDraft { d -> d.copy(goalAmountMinor = major * 100) }
                    }
                },
                label = { Text("Objetivo (unidades principales)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.publicLocationText,
                onValueChange = { viewModel.updateDraft { d -> d.copy(publicLocationText = it) } },
                label = { Text("Ubicación pública aproximada") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.petPublicName,
                onValueChange = { viewModel.updateDraft { d -> d.copy(petPublicName = it) } },
                label = { Text("Mascota (opcional, nombre público)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.shelterPublicName,
                onValueChange = { viewModel.updateDraft { d -> d.copy(shelterPublicName = it) } },
                label = { Text("Refugio (opcional, nombre público)") },
                modifier = Modifier.fillMaxWidth()
            )
            if (state is M17CampaignEditUiState.Error) {
                Text((state as M17CampaignEditUiState.Error).message, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { viewModel.save() },
                enabled = state !is M17CampaignEditUiState.Saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state is M17CampaignEditUiState.Saving) "Guardando…" else "Guardar borrador")
            }
        }
    }
}
