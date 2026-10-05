package com.comunidapp.app.ui.screens.adoptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.ui.components.AdoptionCard
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.leo.LeoActiveFilter
import com.comunidapp.app.ui.components.leo.LeoFilterBar
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoFilterSheet
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.AdoptionListUiState
import com.comunidapp.app.viewmodel.AdoptionsViewModel

@Suppress("UNUSED_PARAMETER")
@Composable
fun AdoptionsScreen(
    onAdoptionClick: (String) -> Unit,
    onSearchAdoptions: () -> Unit = {},
    onMyApplications: () -> Unit = {},
    onReceivedApplications: () -> Unit = {},
    onAdoptionProfile: () -> Unit = {},
    onCreateAdoption: () -> Unit = {},
    showReceivedApplications: Boolean = true,
    showPublishAdoption: Boolean = false,
    showBackButton: Boolean = false,
    onNavigateBack: () -> Unit = {}
) {
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Adopciones",
                subtitle = "Encontrá tu próximo compañero",
                showBackButton = showBackButton,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            LeoPrimaryButton(
                text = "Buscar mascota para adoptar",
                onClick = onSearchAdoptions
            )
            LeoListRow(
                title = "Mi perfil de adopción",
                onClick = onAdoptionProfile
            )
            LeoListRow(
                title = "Mis postulaciones",
                onClick = onMyApplications
            )
            if (showReceivedApplications) {
                LeoListRow(
                    title = "Postulaciones recibidas",
                    onClick = onReceivedApplications
                )
            }
            if (showPublishAdoption) {
                LeoPrimaryButton(
                    text = "Publicar en adopción",
                    onClick = onCreateAdoption
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdoptionSearchScreen(
    onAdoptionClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: AdoptionsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filters by viewModel.filters.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.setOrganization(com.comunidapp.app.domain.organization.OrganizationListContext.organizationId)
    }
    var filtersOpen by remember { mutableStateOf(false) }
    var draftLocation by remember { mutableStateOf("") }
    var draftSex by remember { mutableStateOf<PetSex?>(null) }
    var draftSize by remember { mutableStateOf<PetSize?>(null) }
    val activeFilters = buildList {
        filters.location.trim().takeIf { it.isNotEmpty() }?.let { add(LeoActiveFilter("location", it)) }
        filters.sex?.let { add(LeoActiveFilter("sex", it.toDisplayName())) }
        filters.size?.let { add(LeoActiveFilter("size", it.toDisplayName())) }
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Buscar mascota para adoptar",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            Column(modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd)) {
                LeoFilterBar(
                    onOpenFilters = {
                        draftLocation = filters.location
                        draftSex = filters.sex
                        draftSize = filters.size
                        filtersOpen = true
                    },
                    activeFilters = activeFilters,
                    onRemoveFilter = { id ->
                        when (id) {
                            "location" -> viewModel.onLocationChange("")
                            "sex" -> viewModel.onSexFilterChange(null)
                            "size" -> viewModel.onSizeFilterChange(null)
                        }
                    },
                    onClearFilters = {
                        viewModel.onLocationChange("")
                        viewModel.onSexFilterChange(null)
                        viewModel.onSizeFilterChange(null)
                    }
                )
            }
            LeoFilterSheet(
                visible = filtersOpen,
                onDismiss = { filtersOpen = false },
                onClearDraft = {
                    draftLocation = ""
                    draftSex = null
                    draftSize = null
                },
                onApply = {
                    viewModel.onLocationChange(draftLocation)
                    viewModel.onSexFilterChange(draftSex)
                    viewModel.onSizeFilterChange(draftSize)
                    filtersOpen = false
                }
            ) {
                Text("Lugar", style = LeoCaption)
                V2LocationStringPicker(
                    value = draftLocation,
                    onValueChange = { draftLocation = it }
                )
                Text("Sexo", style = LeoCaption)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
                    listOf(PetSex.MALE, PetSex.FEMALE).forEach { sex ->
                        LeoFilterChip(
                            label = sex.toDisplayName(),
                            selected = draftSex == sex,
                            onClick = { draftSex = if (draftSex == sex) null else sex }
                        )
                    }
                }
                Text("Tamaño", style = LeoCaption)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
                    PetSize.entries.forEach { size ->
                        LeoFilterChip(
                            label = size.toDisplayName(),
                            selected = draftSize == size,
                            onClick = { draftSize = if (draftSize == size) null else size }
                        )
                    }
                }
            }
            when (val state = uiState) {
                AdoptionListUiState.Loading -> LoadingState()
                AdoptionListUiState.Empty -> EmptyState(
                    title = "Sin publicaciones",
                    message = "No hay publicaciones activas por ahora."
                )
                is AdoptionListUiState.Error -> ErrorState(
                    message = state.message,
                    onRetry = viewModel::refresh
                )
                is AdoptionListUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = LeoDimens.SpaceMd,
                        end = LeoDimens.SpaceMd,
                        bottom = padding.calculateBottomPadding() + LeoDimens.SpaceSm
                    ),
                    verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
                ) {
                    items(state.posts, key = { it.id }) { post ->
                        AdoptionCard(post = post, onClick = { onAdoptionClick(post.id) })
                    }
                }
            }
        }
    }
}
