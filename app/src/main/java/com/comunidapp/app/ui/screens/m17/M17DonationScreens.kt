package com.comunidapp.app.ui.screens.m17

import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M17CampaignType
import com.comunidapp.app.data.model.M17Contribution
import com.comunidapp.app.domain.m17.M17ContributionModeration
import com.comunidapp.app.data.model.M17MockOrganizations
import com.comunidapp.app.data.model.M17PublicCampaign
import com.comunidapp.app.data.repository.M17DonationValidators
import com.comunidapp.app.domain.m17.CommunityHelpPresentation
import com.comunidapp.app.domain.m17.MoneyPresentation
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker
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
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoActiveFilter
import com.comunidapp.app.ui.components.leo.LeoFilterBar
import com.comunidapp.app.ui.components.leo.LeoFilterSheet
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTextField
import com.comunidapp.app.ui.theme.LeoDimens

@Composable
fun M17CampaignsListScreen(
    onNavigateBack: () -> Unit,
    onCampaignClick: (String) -> Unit,
    onManage: () -> Unit,
    onCreate: () -> Unit,
    canAdminister: Boolean = false,
    viewModel: M17CampaignsListViewModel = viewModel(factory = M17CampaignsListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var query by remember(filter.query) { mutableStateOf(filter.query) }
    var filtersOpen by remember { mutableStateOf(false) }
    var draftPet by remember { mutableStateOf(false) }
    var draftNear by remember { mutableStateOf(false) }
    var draftCompleted by remember { mutableStateOf(false) }
    var draftType by remember { mutableStateOf<M17CampaignType?>(null) }
    val activeFilters = buildList {
        if (filter.withPetOnly) add(LeoActiveFilter("pet", "Con mascota"))
        if (filter.nearGoalOnly) add(LeoActiveFilter("near", "Cerca del objetivo"))
        if (filter.completedOnly) add(LeoActiveFilter("completed", "Completadas"))
        filter.type?.let { add(LeoActiveFilter("type", m17CampaignTypeLabel(it))) }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Campañas solidarias", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                CommunityHelpPresentation.MONEY_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            LeoFilterBar(
                onOpenFilters = {
                    draftPet = filter.withPetOnly
                    draftNear = filter.nearGoalOnly
                    draftCompleted = filter.completedOnly
                    draftType = filter.type
                    filtersOpen = true
                },
                activeFilters = activeFilters,
                onRemoveFilter = { id ->
                    when (id) {
                        "pet" -> viewModel.setWithPetOnly(false)
                        "near" -> viewModel.setNearGoalOnly(false)
                        "completed" -> viewModel.setCompletedOnly(false)
                        "type" -> viewModel.setType(null)
                    }
                },
                onClearFilters = { viewModel.clearFilters() },
                search = {
                    LeoTextField(
                        value = query,
                        onValueChange = { query = it; viewModel.setQuery(it) },
                        label = "Buscar campaña"
                    )
                }
            )
            LeoFilterSheet(
                visible = filtersOpen,
                onDismiss = { filtersOpen = false },
                onClearDraft = {
                    draftPet = false
                    draftNear = false
                    draftCompleted = false
                    draftType = null
                },
                onApply = {
                    viewModel.setWithPetOnly(draftPet)
                    viewModel.setNearGoalOnly(draftNear)
                    viewModel.setCompletedOnly(draftCompleted)
                    viewModel.setType(draftType)
                    filtersOpen = false
                }
            ) {
                LeoFilterChip(
                    label = "Con mascota",
                    selected = draftPet,
                    onClick = { draftPet = !draftPet }
                )
                LeoFilterChip(
                    label = "Cerca del objetivo",
                    selected = draftNear,
                    onClick = { draftNear = !draftNear }
                )
                LeoFilterChip(
                    label = "Completadas",
                    selected = draftCompleted,
                    onClick = { draftCompleted = !draftCompleted }
                )
                LeoFilterChip(
                    label = m17CampaignTypeLabel(M17CampaignType.MEDICAL),
                    selected = draftType == M17CampaignType.MEDICAL,
                    onClick = {
                        draftType = if (draftType == M17CampaignType.MEDICAL) null else M17CampaignType.MEDICAL
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
                        text = "Nueva",
                        onClick = onCreate,
                        modifier = Modifier.wrapContentWidth()
                    )
                }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(LeoDimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(campaign.organizationDisplayName, style = MaterialTheme.typography.bodySmall)
            Text(campaign.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (campaign.description.isNotBlank()) {
                Text(
                    campaign.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3
                )
            }
            Text(
                MoneyPresentation.raisedOfGoal(
                    campaign.confirmedAmountMinor,
                    campaign.goalAmountMinor,
                    campaign.currency
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { MoneyPresentation.barFraction(campaign.progressPercent) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(m17CampaignStatusLabel(campaign.status), style = MaterialTheme.typography.labelMedium)
            campaign.reference.publicLocationText?.let {
                Text("📍 $it", style = MaterialTheme.typography.bodySmall)
            }
        }
        LeoHairline()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M17CampaignDetailScreen(
    campaignId: String,
    onNavigateBack: () -> Unit,
    viewModel: M17CampaignDetailViewModel = viewModel(factory = M17CampaignDetailViewModel.factory(campaignId))
) {
    val campaign by viewModel.campaign.collectAsState()
    val managed by viewModel.managedContributions.collectAsState()
    val canManage by viewModel.canManageOrganization.collectAsState()
    val viewerId by viewModel.viewerUserId.collectAsState()
    val declaring by viewModel.declaring.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    var showDeclareSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Detalle campaña", showBackButton = true, onBackClick = onNavigateBack)
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
                    if (c.description.isNotBlank()) Text(c.description)
                    Text("Estado: ${m17CampaignStatusLabel(c.status)}")
                    Text(
                        MoneyPresentation.raisedOfGoal(
                            c.confirmedAmountMinor,
                            c.goalAmountMinor,
                            c.currency
                        )
                    )
                    Text(
                        "${c.confirmedContributionCount} colaboraciones confirmadas",
                        style = LeoCaption,
                        color = MutedText
                    )
                    LinearProgressIndicator(
                        progress = { MoneyPresentation.barFraction(c.progressPercent) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    c.reference.petPublicName?.let { Text("Mascota: $it", style = MaterialTheme.typography.bodySmall) }
                    c.reference.shelterPublicName?.let { Text("Refugio: $it", style = MaterialTheme.typography.bodySmall) }
                    LeoHairline()
                    Text("Datos para transferir", style = LeoCardTitle, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Las transferencias se realizan fuera de LeoVer.",
                        style = LeoCaption,
                        color = MutedText
                    )
                    val alias = c.paymentAlias?.trim().orEmpty()
                    if (alias.isNotEmpty()) {
                        LeoListRow(
                            title = "Alias",
                            subtitle = alias
                        )
                        LeoOutlinedButton(
                            text = "Copiar alias",
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Alias", alias))
                                viewModel.notifyAliasCopied()
                            }
                        )
                    } else {
                        Text(
                            "El creador todavía no publicó un alias.",
                            style = LeoCaption,
                            color = MutedText
                        )
                    }
                    LeoPrimaryButton(
                        text = "Colaboré",
                        onClick = { showDeclareSheet = true }
                    )
                    if (c.publicUpdates.isNotEmpty()) {
                        Text("Actualizaciones", fontWeight = FontWeight.SemiBold)
                        c.publicUpdates.forEach { u -> Text("• ${u.message}", style = MaterialTheme.typography.bodySmall) }
                    }
                    val own = managed.filter { row ->
                        row.declaredByViewer ||
                            (viewerId != null && row.contributorUserId == viewerId)
                    }
                    if (own.isNotEmpty()) {
                        LeoHairline()
                        Text("Tus colaboraciones", style = LeoCardTitle, fontWeight = FontWeight.SemiBold)
                        own.forEach { item ->
                            LeoListRow(
                                title = M17DonationValidators.formatMoneyMinor(item.amountMinor, item.currency),
                                subtitle = M17ContributionModeration.statusLabel(item.status)
                            )
                        }
                    }
                    val actionable = managed.filter { item ->
                        M17ContributionModeration.canConfirmOrReject(canManage, item, viewerId)
                    }
                    if (canManage) {
                        LeoHairline()
                        Text("Colaboraciones declaradas", style = LeoCardTitle, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Confirmá solo si recibiste la transferencia. LeoVer no verifica el dinero.",
                            style = LeoCaption,
                            color = MutedText
                        )
                        if (actionable.isEmpty()) {
                            Text("No hay declaraciones pendientes.", style = LeoCaption, color = MutedText)
                        } else {
                            actionable.forEach { item ->
                                CampaignContributionManageRow(
                                    contribution = item,
                                    onConfirm = { viewModel.confirmContribution(item.id) },
                                    onReject = { viewModel.rejectContribution(item.id) }
                                )
                            }
                        }
                    }
                    Text("Quiero colaborar en especie", fontWeight = FontWeight.SemiBold)
                    var goods by remember { mutableStateOf(false) }
                    var supplies by remember { mutableStateOf(false) }
                    var transport by remember { mutableStateOf(false) }
                    var volunteer by remember { mutableStateOf(false) }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = goods, onCheckedChange = { goods = it })
                        Text("Bienes")
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = supplies, onCheckedChange = { supplies = it })
                        Text("Insumos")
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = transport, onCheckedChange = { transport = it })
                        Text("Traslado")
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = volunteer, onCheckedChange = { volunteer = it })
                        Text("Voluntariado")
                    }
                    LeoOutlinedButton(
                        text = "Ofrecer ayuda",
                        onClick = {
                            val kinds = buildList {
                                if (goods) add("bienes")
                                if (supplies) add("insumos")
                                if (transport) add("traslado")
                                if (volunteer) add("voluntariado")
                            }
                            viewModel.offerHelp(kinds)
                        },
                        enabled = goods || supplies || transport || volunteer
                    )
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }

    if (showDeclareSheet) {
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { showDeclareSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier.padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Declarar colaboración", style = LeoCardTitle, fontWeight = FontWeight.SemiBold)
                Text(
                    "Transferí por fuera de LeoVer y declará el monto. Queda pendiente de confirmación.",
                    style = LeoCaption,
                    color = MutedText
                )
                LeoTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "Monto"
                )
                LeoTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Nota (opcional)",
                    singleLine = false,
                    minLines = 2
                )
                LeoPrimaryButton(
                    text = if (declaring) "Enviando…" else "Declarar",
                    onClick = {
                        viewModel.declareContribution(amount, note)
                        showDeclareSheet = false
                    },
                    enabled = !declaring && M17DonationValidators.parseDeclaredAmountToMinor(amount) != null
                )
            }
        }
    }
}

@Composable
private fun CampaignContributionManageRow(
    contribution: M17Contribution,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
        LeoListRow(
            title = M17DonationValidators.formatMoneyMinor(contribution.amountMinor, contribution.currency),
            subtitle = contribution.message?.takeIf { it.isNotBlank() } ?: "Pendiente de confirmación"
        )
        LeoPrimaryButton(text = "Confirmar", onClick = onConfirm)
        LeoOutlinedButton(text = "Rechazar", onClick = onReject)
        LeoHairline()
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Administrar campañas", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                M17MockOrganizations.MANAGE_ORGANIZATION_IDS.forEach { orgId ->
                    LeoFilterChip(
                        label = orgId.removePrefix("org_"),
                        selected = selectedOrg == orgId,
                        onClick = { viewModel.selectOrganization(orgId) }
                    )
                }
            }
            LeoPrimaryButton(
                text = "Nueva campaña",
                onClick = onCreate
            )
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
                        Column(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(LeoDimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
                                Text(c.title, fontWeight = FontWeight.Bold)
                                Text("${m17CampaignStatusLabel(c.status)} · ${m17CampaignTypeLabel(c.campaignType)}")
                                summary?.let {
                                    Text(
                                        MoneyPresentation.raisedOfGoal(
                                            it.confirmedAmountMinor,
                                            it.goalAmountMinor,
                                            it.currency
                                        )
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LeoOutlinedButton(
                                        text = "Editar",
                                        onClick = { onEditCampaign(c.id) }
                                    )
                                    if (c.status == com.comunidapp.app.data.model.M17CampaignStatus.DRAFT) {
                                        LeoPrimaryButton(
                                            text = "Publicar",
                                            onClick = { viewModel.publish(c.id) }
                                        )
                                    }
                                    if (c.status == com.comunidapp.app.data.model.M17CampaignStatus.PUBLISHED) {
                                        LeoOutlinedButton(
                                            text = "Pausar",
                                            onClick = { viewModel.pause(c.id) }
                                        )
                                        LeoOutlinedButton(
                                            text = "Completar",
                                            onClick = { viewModel.complete(c.id) }
                                        )
                                    }
                                    if (!c.status.isTerminal) {
                                        LeoOutlinedButton(
                                            text = "Cancelar",
                                            onClick = { viewModel.cancel(c.id) }
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                value = (draft.goalAmountMinor / 100).toString(),
                onValueChange = { v ->
                    v.toLongOrNull()?.let { major ->
                        viewModel.updateDraft { d -> d.copy(goalAmountMinor = major * 100) }
                    }
                },
                label = "Objetivo (unidades principales)"
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
                value = draft.shelterPublicName,
                onValueChange = { viewModel.updateDraft { d -> d.copy(shelterPublicName = it) } },
                label = "Refugio (opcional, nombre público)"
            )
            if (state is M17CampaignEditUiState.Error) {
                Text((state as M17CampaignEditUiState.Error).message, color = MaterialTheme.colorScheme.error)
            }
            LeoPrimaryButton(
                text = if (state is M17CampaignEditUiState.Saving) "Guardando…" else "Guardar borrador",
                onClick = { viewModel.save() },
                enabled = state !is M17CampaignEditUiState.Saving
            )
        }
    }
}
