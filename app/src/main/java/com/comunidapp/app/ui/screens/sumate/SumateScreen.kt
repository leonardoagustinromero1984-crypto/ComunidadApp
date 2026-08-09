package com.comunidapp.app.ui.screens.sumate

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoSearchBar
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.screens.adoptions.AdoptionsContent
import com.comunidapp.app.ui.screens.lostfound.LostFoundContent
import com.comunidapp.app.ui.screens.sumate.tabs.AdoptionEventsContent
import com.comunidapp.app.ui.screens.sumate.tabs.FosterHomesContent
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.viewmodel.SumateViewModel
import kotlinx.coroutines.launch

private val sumateCategories = listOf(
    "Adopciones",
    "Perdidos",
    "Encontrados",
    "Tránsito",
    "Eventos"
)

@Composable
fun SumateScreen(
    onAdoptionClick: (String) -> Unit,
    onShelterClick: (String) -> Unit,
    onNavigateToMap: () -> Unit = {},
    onMyApplications: () -> Unit = {},
    onReceivedApplications: () -> Unit = {},
    onFosterHomes: () -> Unit = {},
    onShelterOps: () -> Unit = {},
    onVeterinaryDirectory: () -> Unit = {},
    onM16Shelters: () -> Unit = {},
    onM17Campaigns: () -> Unit = {},
    onM18Events: () -> Unit = {},
    onNavigateToPublish: () -> Unit = {},
    onCreateAdoption: () -> Unit = {},
    onCreateLost: () -> Unit = {},
    onCreateFound: () -> Unit = {},
    onCreateFoster: () -> Unit = {},
    onCreateEvent: () -> Unit = {},
    viewModel: SumateViewModel = viewModel()
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val orgFilter by viewModel.orgFilter.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activeFilters = if (orgFilter) 1 else 0

    @Suppress("UNUSED_VARIABLE")
    val preservedRoutes = remember {
        listOf(
            onShelterClick,
            onMyApplications,
            onReceivedApplications,
            onShelterOps,
            onVeterinaryDirectory,
            onM17Campaigns,
            onNavigateToPublish
        )
    }

    val createLabel = when (selectedCategory) {
        0 -> "Publicar adopción"
        1 -> "Reportar mascota perdida"
        2 -> "Informar mascota encontrada"
        3 -> "Ofrecer tránsito"
        4 -> "Crear evento"
        else -> "Crear"
    }
    val onCreate = when (selectedCategory) {
        0 -> onCreateAdoption
        1 -> onCreateLost
        2 -> onCreateFound
        3 -> onCreateFoster
        4 -> onCreateEvent
        else -> onNavigateToPublish
    }

    Scaffold(
        containerColor = BrandCream,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LeoTopAppBar(
                title = "Sumate",
                subtitle = "Encontrá mascotas y causas que necesitan ayuda."
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            LeoSearchBar(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = "Buscar por zona, nombre o localidad",
                onFilterClick = {
                    val next = !orgFilter
                    viewModel.setOrgFilter(next)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (next) "Filtro: publicado por organizaciones"
                            else "Filtros limpios"
                        )
                    }
                },
                activeFiltersCount = activeFilters,
                modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm),
                horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
            ) {
                sumateCategories.forEachIndexed { index, category ->
                    LeoFilterChip(
                        label = category,
                        selected = selectedCategory == index,
                        onClick = { viewModel.selectCategory(index) }
                    )
                }
            }

            LeoPrimaryButton(
                text = createLabel,
                onClick = onCreate,
                icon = Icons.Default.Add,
                modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm)
            )

            if (orgFilter) {
                Text(
                    text = "Mostrando orientación a organizaciones. Las fichas se abren desde cada caso.",
                    style = LeoCaption,
                    color = MutedText,
                    modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                when (selectedCategory) {
                    0 -> AdoptionsContent(
                        onAdoptionClick = onAdoptionClick,
                        showPrivateActions = false,
                        bottomPadding = 0.dp
                    )
                    1 -> LostFoundContent(
                        onNavigateToMap = onNavigateToMap,
                        lockedType = LostFoundType.LOST,
                        bottomPadding = 0.dp
                    )
                    2 -> LostFoundContent(
                        onNavigateToMap = onNavigateToMap,
                        lockedType = LostFoundType.FOUND,
                        bottomPadding = 0.dp
                    )
                    3 -> FosterHomesContent(
                        onOpenFosterHomes = onFosterHomes,
                        bottomPadding = 0.dp
                    )
                    4 -> AdoptionEventsContent(
                        onM18Events = onM18Events,
                        bottomPadding = 0.dp
                    )
                }
            }
            // RC1.2: sin enlace genérico "Ver organizaciones" al final.
            // Organizaciones se muestran como autor de casos / filtro / sección contextual.
            @Suppress("UNUSED_VARIABLE")
            val orgsEntryPreserved = onM16Shelters
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390)
@Composable
private fun SumateHubPreview() {
    ComunidappTheme {
        Column(
            modifier = Modifier.padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            LeoTopAppBar(
                title = "Sumate",
                subtitle = "Encontrá mascotas y causas que necesitan ayuda."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)) {
                LeoFilterChip(label = "Adopciones", selected = true, onClick = {})
                LeoFilterChip(label = "Perdidos", selected = false, onClick = {})
            }
            LeoPrimaryButton(text = "Publicar adopción", onClick = {}, icon = Icons.Default.Add)
            LeoEmptyState(
                title = "No encontramos casos con estos filtros",
                message = "Probá cambiando la categoría, la zona o los filtros.",
                icon = Icons.Default.Pets
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, name = "SumateOrganizationAuthorPreview")
@Composable
private fun SumateOrganizationAuthorPreview() {
    ComunidappTheme {
        Column(Modifier.padding(LeoDimens.SpaceMd)) {
            Text("Publicado por", style = LeoCaption, color = MutedText)
            Text("Refugio Patitas del Sur ✓", style = LeoCaption)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, name = "SumateOrganizationsContextPreview")
@Composable
private fun SumateOrganizationsContextPreview() {
    ComunidappTheme {
        LeoEmptyState(
            title = "No encontramos publicaciones activas",
            message = "También podés conocer organizaciones de tu zona.",
            secondaryActionLabel = "Ver organizaciones cercanas",
            onSecondaryAction = {},
            icon = Icons.Default.Pets
        )
    }
}
