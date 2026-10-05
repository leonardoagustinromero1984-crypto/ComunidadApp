package com.comunidapp.app.ui.screens.m17

import com.comunidapp.app.ui.theme.BrandBackground

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M17InKindNeedStatus
import com.comunidapp.app.data.model.M17InKindPledgeStatus
import com.comunidapp.app.data.model.M17PublicInKindNeed
import com.comunidapp.app.data.model.M17PublicVolunteerOpportunity
import com.comunidapp.app.data.model.M17VolunteerApplicationStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityStatus
import com.comunidapp.app.domain.m17.CommunityHelpPresentation
import com.comunidapp.app.domain.m17.MoneyPresentation
import com.comunidapp.app.domain.m17.MyGoodsPledge
import com.comunidapp.app.domain.m17.MyMoneyContribution
import com.comunidapp.app.domain.m17.MyVolunteerInterest
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTextField
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.viewmodel.M17GoodsDetailUiState
import com.comunidapp.app.viewmodel.M17GoodsDetailViewModel
import com.comunidapp.app.viewmodel.M17InKindListUiState
import com.comunidapp.app.viewmodel.M17InKindListViewModel
import com.comunidapp.app.viewmodel.M17MyHelpUiState
import com.comunidapp.app.viewmodel.M17MyHelpViewModel
import com.comunidapp.app.viewmodel.M17VolunteerDetailUiState
import com.comunidapp.app.viewmodel.M17VolunteerDetailViewModel
import com.comunidapp.app.viewmodel.M17VolunteerListUiState
import com.comunidapp.app.viewmodel.M17VolunteerListViewModel

@Composable
fun M17HubScreen(
    onNavigateBack: () -> Unit,
    onCampaigns: () -> Unit,
    onGoods: () -> Unit,
    onVolunteer: () -> Unit
) {
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = CommunityHelpPresentation.DISCOVER_TITLE,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                CommunityHelpPresentation.DISCOVER_QUESTION,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            HelpChoice(
                title = CommunityHelpPresentation.MONEY_ACTION,
                subtitle = CommunityHelpPresentation.MONEY_HINT,
                onClick = onCampaigns
            )
            HelpChoice(
                title = CommunityHelpPresentation.GOODS_ACTION,
                subtitle = CommunityHelpPresentation.GOODS_HINT,
                onClick = onGoods
            )
            HelpChoice(
                title = CommunityHelpPresentation.TIME_ACTION,
                subtitle = CommunityHelpPresentation.TIME_HINT,
                onClick = onVolunteer
            )
        }
    }
}

@Composable
private fun HelpChoice(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(LeoDimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = LeoCardTitle, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = LeoCaption, color = MutedText)
        }
        LeoHairline()
    }
}

@Composable
fun M17GoodsListScreen(
    onNavigateBack: () -> Unit,
    onNeedClick: (String) -> Unit,
    organizationId: String? = null,
    viewModel: M17InKindListViewModel = viewModel(factory = M17InKindListViewModel.factory(organizationId))
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = CommunityHelpPresentation.GOODS_ACTION,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                CommunityHelpPresentation.GOODS_HINT,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall
            )
            when (val s = state) {
                M17InKindListUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                M17InKindListUiState.Empty -> EmptyState(
                    title = "Sin necesidades publicadas",
                    contentModifier = Modifier.fillMaxSize(),
                    message = "Cuando una organización publique qué necesita, va a aparecer acá."
                )
                is M17InKindListUiState.Error -> ErrorState(s.message, Modifier.fillMaxSize(), onRetry = { viewModel.load() })
                is M17InKindListUiState.Content -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(s.items, key = { it.id }) { need ->
                        GoodsNeedRow(need, onClick = { onNeedClick(need.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GoodsNeedRow(need: M17PublicInKindNeed, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(LeoDimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
            Text(need.organizationDisplayName, style = MaterialTheme.typography.bodySmall)
            Text(need.title, fontWeight = FontWeight.Bold)
            Text(CommunityHelpPresentation.goodsCategory(need.category), style = MaterialTheme.typography.labelMedium)
            Text("Necesita ${CommunityHelpPresentation.quantityLine(need.quantityRequested, need.quantityUnit)}")
            need.publicLocationText?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(CommunityHelpPresentation.needStatus(need.status), style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(
                progress = { MoneyPresentation.barFraction(need.coveragePercent) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        LeoHairline()
    }
}

@Composable
fun M17GoodsDetailScreen(
    needId: String,
    onNavigateBack: () -> Unit,
    viewModel: M17GoodsDetailViewModel = viewModel(factory = M17GoodsDetailViewModel.factory(needId))
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    var quantity by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = CommunityHelpPresentation.GOODS_ACTION, showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            M17GoodsDetailUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is M17GoodsDetailUiState.Error -> ErrorState(message = s.message, contentModifier = Modifier.padding(padding))
            is M17GoodsDetailUiState.Content -> {
                val need = s.need
                Column(
                    Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(need.organizationDisplayName, style = MaterialTheme.typography.bodyMedium)
                    Text(need.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(CommunityHelpPresentation.goodsCategory(need.category), style = MaterialTheme.typography.labelMedium)
                    if (need.description.isNotBlank()) Text(need.description)
                    Text("Necesita ${CommunityHelpPresentation.quantityLine(need.quantityRequested, need.quantityUnit)}")
                    if (need.quantityPledged > 0) {
                        Text(
                            "Ofrecido: ${CommunityHelpPresentation.quantityLine(need.quantityPledged, need.quantityUnit)}",
                            style = LeoCaption,
                            color = MutedText
                        )
                    }
                    need.publicLocationText?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    Text("Estado: ${CommunityHelpPresentation.needStatus(need.status)}")
                    LinearProgressIndicator(
                        progress = { MoneyPresentation.barFraction(need.coveragePercent) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (need.status == M17InKindNeedStatus.PUBLISHED) {
                        LeoHairline()
                        LeoTextField(value = quantity, onValueChange = { quantity = it }, label = "Cantidad")
                        LeoTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = "Nota (opcional)",
                            singleLine = false,
                            minLines = 2
                        )
                        LeoPrimaryButton(
                            text = if (submitting) "Enviando…" else CommunityHelpPresentation.WANT_TO_HELP,
                            onClick = { viewModel.offer(quantity, note) },
                            enabled = !submitting && (quantity.toIntOrNull() ?: 0) > 0
                        )
                    }
                    if (need.canManage) {
                        LeoHairline()
                        Text("Aportes", style = MaterialTheme.typography.titleMedium)
                        if (s.pledges.isEmpty()) {
                            Text("Todavía no hay aportes.", color = MutedText)
                        }
                        s.pledges.forEach { pledge ->
                            Text("${pledge.quantity} · ${CommunityHelpPresentation.pledgeStatus(pledge.status)}")
                            if (pledge.status == M17InKindPledgeStatus.PLEDGED) {
                                LeoPrimaryButton(
                                    text = "Marcar entregado",
                                    onClick = { viewModel.confirmDelivery(pledge.id) },
                                    enabled = !submitting
                                )
                            }
                        }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
fun M17VolunteerListScreen(
    onNavigateBack: () -> Unit,
    onOpportunityClick: (String) -> Unit,
    organizationId: String? = null,
    viewModel: M17VolunteerListViewModel = viewModel(factory = M17VolunteerListViewModel.factory(organizationId))
) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = CommunityHelpPresentation.TIME_ACTION,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                CommunityHelpPresentation.TIME_HINT,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall
            )
            LeoTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.search(it)
                },
                label = "Buscar convocatoria",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            when (val s = state) {
                M17VolunteerListUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                M17VolunteerListUiState.Empty -> EmptyState(
                    title = "Sin oportunidades publicadas",
                    contentModifier = Modifier.fillMaxSize(),
                    message = "Cuando una organización pida una mano, va a aparecer acá."
                )
                is M17VolunteerListUiState.Error -> ErrorState(
                    s.message,
                    Modifier.fillMaxSize(),
                    onRetry = { viewModel.load() }
                )
                is M17VolunteerListUiState.Content -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(s.items, key = { it.id }) { opp ->
                        VolunteerOpportunityRow(opp, onClick = { onOpportunityClick(opp.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VolunteerOpportunityRow(opp: M17PublicVolunteerOpportunity, onClick: () -> Unit) {
    val slots = CommunityHelpPresentation.slotsLine(opp.slotsFilled, opp.slotsNeeded)
    LeoListRow(
        title = CommunityHelpPresentation.volunteerTitle(opp.title),
        subtitle = buildString {
            append(opp.organizationDisplayName)
            append(" · ")
            append(CommunityHelpPresentation.volunteerType(opp.type))
            if (slots != null) {
                append(" · ")
                append(slots)
            }
            opp.scheduleHint?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        },
        onClick = onClick
    )
}

@Composable
fun M17VolunteerDetailScreen(
    opportunityId: String,
    onNavigateBack: () -> Unit,
    viewModel: M17VolunteerDetailViewModel = viewModel(
        factory = M17VolunteerDetailViewModel.factory(opportunityId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    var note by remember { mutableStateOf("") }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = CommunityHelpPresentation.TIME_ACTION,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val s = state) {
            M17VolunteerDetailUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is M17VolunteerDetailUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is M17VolunteerDetailUiState.Content -> {
                val opp = s.opportunity
                val slots = CommunityHelpPresentation.slotsLine(opp.slotsFilled, opp.slotsNeeded)
                Column(
                    Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(opp.organizationDisplayName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        CommunityHelpPresentation.volunteerTitle(opp.title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(CommunityHelpPresentation.volunteerType(opp.type), style = MaterialTheme.typography.labelMedium)
                    if (opp.description.isNotBlank()) Text(opp.description)
                    opp.publicLocationText?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    opp.scheduleHint?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    slots?.let { Text(it) }
                    Text("Estado: ${CommunityHelpPresentation.opportunityStatus(opp.status)}")
                    if (opp.canManage) {
                        LeoHairline()
                        Text("Postulantes", style = MaterialTheme.typography.titleMedium)
                        if (s.applicants.isEmpty()) {
                            Text("Todavía no hay postulantes.", color = MutedText)
                        }
                        s.applicants.forEach { applicant ->
                            Text(CommunityHelpPresentation.applicationStatus(applicant.status))
                            if (applicant.status == M17VolunteerApplicationStatus.SUBMITTED) {
                                LeoPrimaryButton(
                                    text = "Aceptar",
                                    onClick = { viewModel.accept(applicant.id) },
                                    enabled = !submitting && opp.status == M17VolunteerOpportunityStatus.PUBLISHED
                                )
                            }
                        }
                    }
                    if (opp.status == M17VolunteerOpportunityStatus.PUBLISHED && !opp.canManage) {
                        LeoHairline()
                        Text(
                            "Anotarte no te convierte en integrante de la organización.",
                            style = LeoCaption,
                            color = MutedText
                        )
                        LeoTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = "Nota (opcional)",
                            singleLine = false,
                            minLines = 2
                        )
                        LeoPrimaryButton(
                            text = if (submitting) "Enviando…" else CommunityHelpPresentation.WANT_TO_HELP,
                            onClick = { viewModel.offer(note) },
                            enabled = !submitting
                        )
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
fun M17MyHelpScreen(
    onNavigateBack: () -> Unit,
    viewModel: M17MyHelpViewModel = viewModel(factory = M17MyHelpViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = CommunityHelpPresentation.ACTIVITY_TITLE,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        if (state.loading && state.money.isEmpty() && state.goods.isEmpty() && state.time.isEmpty()) {
            LoadingState(contentModifier = Modifier.padding(padding))
        } else {
            Column(
                Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HelpHistorySection(
                    title = CommunityHelpPresentation.SECTION_MONEY,
                    empty = CommunityHelpPresentation.EMPTY_MONEY,
                    error = state.moneyError,
                    rows = state.money.map { it.toRow() }
                )
                HelpHistorySection(
                    title = CommunityHelpPresentation.SECTION_GOODS,
                    empty = CommunityHelpPresentation.EMPTY_GOODS,
                    error = state.goodsError,
                    rows = state.goods.map { it.toRow() }
                )
                HelpHistorySection(
                    title = CommunityHelpPresentation.SECTION_TIME,
                    empty = CommunityHelpPresentation.EMPTY_TIME,
                    error = state.timeError,
                    rows = state.time.map { it.toRow() }
                )
            }
        }
    }
}

private fun MyMoneyContribution.toRow(): Pair<String, String> = campaignTitle to buildString {
    append(organizationName)
    append(" · ")
    append(MoneyPresentation.formatMinor(amountMinor, currency))
    append(" · ")
    append(CommunityHelpPresentation.date(createdAt))
    append(" · ")
    append(CommunityHelpPresentation.contributionStatus(status))
}

private fun MyGoodsPledge.toRow(): Pair<String, String> = needTitle to buildString {
    append(organizationName)
    append(" · ")
    append(CommunityHelpPresentation.quantityLine(quantity, unit))
    append(" · ")
    append(CommunityHelpPresentation.date(createdAt))
    append(" · ")
    append(CommunityHelpPresentation.pledgeStatus(status))
}

private fun MyVolunteerInterest.toRow(): Pair<String, String> = opportunityTitle to buildString {
    append(organizationName)
    append(" · ")
    append(CommunityHelpPresentation.date(createdAt))
    append(" · ")
    append(CommunityHelpPresentation.applicationStatus(status))
}

@Composable
private fun HelpHistorySection(
    title: String,
    empty: String,
    error: String?,
    rows: List<Pair<String, String>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = LeoCardTitle, fontWeight = FontWeight.SemiBold)
        when {
            error != null -> Text(error, color = MaterialTheme.colorScheme.error)
            rows.isEmpty() -> Text(empty, style = LeoCaption, color = MutedText)
            else -> rows.forEach { (rowTitle, subtitle) ->
                LeoListRow(title = rowTitle, subtitle = subtitle)
            }
        }
    }
}
