package com.comunidapp.app.ui.screens.comunidad

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.location.ForegroundLocation
import com.comunidapp.app.domain.location.ServiceLocationFilter
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState
import com.comunidapp.app.domain.map.LeoVerMapMarker
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoSearchBar
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.leo.LeoVerCard
import com.comunidapp.app.ui.components.leo.LeoVerProviderCard
import com.comunidapp.app.ui.components.v2.V2LocationCityProvincePicker
import com.comunidapp.app.ui.map.LeoVerMap
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.viewmodel.CommunityResultsView
import com.comunidapp.app.viewmodel.ComunidadViewModel
import kotlinx.coroutines.launch

private data class ServiceCategoryChip(
    val category: ServiceCategory,
    val title: String,
    val emptyTitle: String,
    val icon: ImageVector
)

private val serviceCategoryChips = listOf(
    ServiceCategoryChip(
        ServiceCategory.VET, "Veterinarias",
        "No hay veterinarias para mostrar", Icons.Default.LocalHospital
    ),
    ServiceCategoryChip(
        ServiceCategory.SHOP, "Tiendas",
        "No hay tiendas para mostrar", Icons.Default.ShoppingBag
    ),
    ServiceCategoryChip(
        ServiceCategory.WALKER, "Paseadores",
        "No hay paseadores para mostrar", Icons.Default.Pets
    ),
    ServiceCategoryChip(
        ServiceCategory.TRAINER, "Adiestradores",
        "No hay educadores para mostrar", Icons.Default.School
    ),
    ServiceCategoryChip(
        ServiceCategory.DAYCARE, "Guarderías",
        "No hay guarderías para mostrar", Icons.Default.Home
    ),
    ServiceCategoryChip(
        ServiceCategory.GROOMING, "Peluquerías",
        "No hay peluquerías para mostrar", Icons.Default.ContentCut
    ),
    ServiceCategoryChip(
        ServiceCategory.PET_FRIENDLY, "Lugares pet friendly",
        "No hay lugares pet friendly para mostrar", Icons.Default.Park
    )
)

@Composable
fun ComunidadScreen(
    onServiceClick: (String) -> Unit,
    onOpenSocialFeed: () -> Unit = {},
    onOpenMessaging: () -> Unit = {},
    onOpenReputation: () -> Unit = {},
    onOpenProviders: () -> Unit = {},
    onOpenBookings: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenAiAssistance: () -> Unit = {},
    onOpenIntegrations: () -> Unit = {},
    viewModel: ComunidadViewModel = viewModel()
) {
    @Suppress("UNUSED_VARIABLE")
    val preservedModuleRoutes = remember {
        listOf(
            onOpenSocialFeed, onOpenMessaging, onOpenReputation, onOpenProviders,
            onOpenBookings, onOpenMarketplace, onOpenAiAssistance, onOpenIntegrations
        )
    }

    val uiState by viewModel.uiState.collectAsState()
    val services by viewModel.services.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedMarker by remember { mutableStateOf<LeoVerMapMarker?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            scope.launch {
                val point = ForegroundLocation.current(context)
                if (point != null) viewModel.enableNearMe(point.latitude, point.longitude)
            }
        }
    }

    val selectedChip = serviceCategoryChips.find { it.category == uiState.selectedCategory }
    val catalog by DataProvider.locationCatalogRepository.nodes.collectAsState()
    val availableTags = remember(services) {
        services.flatMap { it.tags }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filteredServices = remember(
        services, searchQuery, uiState.selectedCategory, uiState.locationQuery,
        uiState.localityId, uiState.selectedTag, uiState.activeOnly, catalog
    ) {
        if (uiState.selectedCategory == null) return@remember emptyList()
        services.filter { service ->
            val matchesCategory = service.category == uiState.selectedCategory
            val q = searchQuery.trim()
            val loc = uiState.locationQuery.trim()
            val matchesQuery = q.isEmpty() ||
                service.name.contains(q, ignoreCase = true) ||
                service.description.contains(q, ignoreCase = true)
            val matchesLocation = when {
                !uiState.localityId.isNullOrBlank() ->
                    service.localityId == uiState.localityId ||
                        service.localityIds.contains(uiState.localityId) ||
                        ServiceLocationFilter.matches(service, loc, catalog)
                loc.isNotBlank() -> ServiceLocationFilter.matches(service, loc, catalog)
                else -> true
            }
            val matchesTag = uiState.selectedTag.isNullOrBlank() ||
                service.tags.any { it.equals(uiState.selectedTag, ignoreCase = true) }
            val matchesActive = !uiState.activeOnly || service.active
            matchesCategory && matchesQuery && matchesLocation && matchesTag && matchesActive
        }
    }

    VisualDirectionPilot {
        val visual = leoVisual()
        Scaffold(
            containerColor = visual.background,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = {
                LeoTopAppBar(
                    title = "Comunidad",
                    subtitle = "Servicios para tu mascota"
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = LeoDimens.SpaceMd,
                    end = LeoDimens.SpaceMd,
                    bottom = padding.calculateBottomPadding() + LeoDimens.SpaceMd
                ),
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
            ) {
                item(key = "categories") {
                    CommunityCategoryGrid(
                        selected = uiState.selectedCategory,
                        onSelect = viewModel::selectCategory
                    )
                }

                if (uiState.selectedCategory == null) {
                    item(key = "choose_service") {
                        LeoEmptyState(
                            title = "¿Qué servicio necesitás?",
                            message = "Elegí un servicio para encontrar opciones cerca tuyo.",
                            icon = Icons.Default.Storefront
                        )
                    }
                } else {
                    item(key = "filters") {
                        var geoProvince by remember(uiState.selectedCategory) { mutableStateOf(uiState.province) }
                        var geoCity by remember(uiState.selectedCategory) { mutableStateOf(uiState.city) }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(LeoDimens.RadiusCard),
                            color = visual.surface,
                            border = BorderStroke(1.dp, visual.borderSoft)
                        ) {
                            Column(
                                modifier = Modifier.padding(LeoDimens.SpaceMd),
                                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
                            ) {
                                Text(
                                    text = selectedChip?.title.orEmpty(),
                                    style = LeoCaption,
                                    color = visual.textPrimary
                                )
                                V2LocationCityProvincePicker(
                                    city = geoCity,
                                    province = geoProvince,
                                    onCityChange = { city ->
                                        geoCity = city
                                        viewModel.applyGeography(geoProvince, city, uiState.localityId)
                                    },
                                    onProvinceChange = { province ->
                                        geoProvince = province
                                        viewModel.applyGeography(province, geoCity, null)
                                    },
                                    onLocalityIdChange = { localityId ->
                                        viewModel.applyGeography(geoProvince, geoCity, localityId)
                                    },
                                    includeZone = false
                                )
                                LeoFilterChip(
                                    label = if (uiState.nearMeEnabled) "Cerca de tu ubicación" else "Cerca mío",
                                    selected = uiState.nearMeEnabled,
                                    onClick = {
                                        if (uiState.nearMeEnabled) {
                                            viewModel.disableNearMe()
                                        } else if (ForegroundLocation.hasForegroundPermission(context)) {
                                            scope.launch {
                                                val point = ForegroundLocation.current(context)
                                                if (point != null) {
                                                    viewModel.enableNearMe(point.latitude, point.longitude)
                                                }
                                            }
                                        } else {
                                            permissionLauncher.launch(ForegroundLocation.permissions)
                                        }
                                    }
                                )
                                if (availableTags.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(availableTags, key = { it }) { tag ->
                                            LeoFilterChip(
                                                label = tag,
                                                selected = uiState.selectedTag == tag,
                                                onClick = {
                                                    viewModel.selectTag(
                                                        if (uiState.selectedTag == tag) null else tag
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                                LeoSearchBar(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = "Buscar por nombre"
                                )
                                if (uiState.activeFilterCount > 0 || searchQuery.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            searchQuery = ""
                                            viewModel.clearFilters()
                                        }
                                    ) {
                                        Text("Limpiar filtros")
                                    }
                                }
                                LeoPrimaryButton(
                                    text = if (uiState.hasSearched) "Actualizar búsqueda" else "Buscar",
                                    onClick = viewModel::search
                                )
                            }
                        }
                    }
                    if (!uiState.hasSearched && !uiState.isLoading) {
                        item(key = "await_search") {
                            Text(
                                text = "Elegí provincia o localidad y tocá Buscar.",
                                style = LeoCaption,
                                color = visual.textSecondary
                            )
                        }
                    }
                    item(key = "result_count") {
                        Text(
                            text = when {
                                uiState.isLoading -> "Buscando…"
                                uiState.searchError != null -> uiState.searchError.orEmpty()
                                !uiState.hasSearched -> ""
                                else -> "${filteredServices.size} resultados"
                            },
                            style = LeoCaption,
                            color = visual.textSecondary
                        )
                    }
                    if (uiState.isLoading) {
                        item(key = "loading") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(LeoDimens.SpaceLg),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = visual.primary)
                            }
                        }
                    } else if (uiState.searchError != null) {
                        item(key = "error") {
                            LeoEmptyState(
                                title = "No pudimos buscar",
                                message = uiState.searchError.orEmpty(),
                                actionLabel = "Reintentar",
                                onAction = viewModel::search,
                                icon = Icons.Default.Storefront
                            )
                        }
                    } else if (uiState.hasSearched && filteredServices.isEmpty()) {
                        item(key = "empty") {
                            LeoEmptyState(
                                title = "No encontramos opciones con estos filtros.",
                                message = "Probá otra localidad o cambiá el servicio.",
                                actionLabel = "Cambiar filtros",
                                onAction = {
                                    searchQuery = ""
                                    viewModel.clearFilters()
                                },
                                icon = Icons.Default.Storefront
                            )
                        }
                    } else if (uiState.hasSearched) {
                        item(key = "view_toggle") {
                            Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
                                LeoFilterChip(
                                    label = "Lista",
                                    selected = uiState.resultsView == CommunityResultsView.LIST,
                                    onClick = { viewModel.setResultsView(CommunityResultsView.LIST) }
                                )
                                LeoFilterChip(
                                    label = "Mapa",
                                    selected = uiState.resultsView == CommunityResultsView.MAP,
                                    onClick = { viewModel.setResultsView(CommunityResultsView.MAP) }
                                )
                            }
                        }
                        if (uiState.resultsView == CommunityResultsView.MAP) {
                            item(key = "map") {
                                val mapMarkers = filteredServices.mapNotNull { service ->
                                    val lat = service.latitude ?: return@mapNotNull null
                                    val lng = service.longitude ?: return@mapNotNull null
                                    if (!service.geoIsPublicPremises) return@mapNotNull null
                                    val position = LeoVerGeoPoint.parseOrNull(lat, lng) ?: return@mapNotNull null
                                    LeoVerMapMarker(
                                        id = service.id,
                                        position = position,
                                        title = service.name,
                                        subtitle = communityLocalityLabel(service, catalog),
                                        category = service.category.name,
                                        rating = service.rating,
                                        locality = communityLocalityLabel(service, catalog),
                                        distanceKm = service.distanceKm
                                    )
                                }
                                Column {
                                    Box(Modifier.fillMaxWidth().height(320.dp)) {
                                        LeoVerMap(
                                            markers = mapMarkers,
                                            camera = LeoVerMapCameraState(
                                                mapMarkers.firstOrNull()?.position
                                                    ?: LeoVerGeoPoint.parseOrNull(uiState.deviceLat, uiState.deviceLng)
                                                    ?: LeoVerMapCameraState.ARGENTINA_FALLBACK
                                            ),
                                            userLocation = LeoVerGeoPoint.parseOrNull(uiState.deviceLat, uiState.deviceLng),
                                            showUserLocation = uiState.nearMeEnabled && uiState.deviceLat != null,
                                            onMarkerClick = { selectedMarker = it },
                                            onCameraIdle = { }
                                        )
                                    }
                                    selectedMarker?.let { marker ->
                                        LeoVerCard {
                                            Text(marker.title, color = visual.textPrimary)
                                            marker.locality?.let {
                                                Text(it, style = LeoCaption, color = visual.textSecondary)
                                            }
                                            marker.rating?.let {
                                                Text("★ ${"%.1f".format(it)}", style = LeoCaption, color = visual.textSecondary)
                                            }
                                            marker.distanceKm?.let {
                                                Text("%.1f km".format(it), style = LeoCaption, color = visual.textSecondary)
                                            }
                                            LeoPrimaryButton(
                                                text = "Ver perfil",
                                                onClick = { onServiceClick(marker.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            items(uiState.nearbyItems, key = { "nearby-${it.kind}-${it.id}" }) { item ->
                                LeoVerCard {
                                    Text(item.name, style = LeoCaption)
                                    Text(
                                        "${item.kind} · ${item.badge}" +
                                            (item.meters?.let { " · ${it.toInt()} m" } ?: ""),
                                        style = LeoCaption,
                                        color = visual.textSecondary
                                    )
                                }
                            }
                            items(filteredServices, key = { it.id }) { service ->
                                LeoVerProviderCard(
                                    service = service,
                                    onClick = { onServiceClick(service.id) },
                                    localityLabel = communityLocalityLabel(service, catalog)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun communityLocalityLabel(
    service: ServiceProfile,
    catalog: List<com.comunidapp.app.data.model.LocationNode>
): String? {
    if (catalog.isEmpty()) return service.location.takeIf { it.isNotBlank() }
    val locality = catalog.firstOrNull { it.id == service.localityId }
        ?: catalog.firstOrNull { service.localityIds.contains(it.id) }
    val province = catalog.firstOrNull { it.id == service.provinceId }
        ?: locality?.parentId?.let { pid -> catalog.firstOrNull { it.id == pid } }
    val parts = listOfNotNull(locality?.name, province?.name).distinct()
    return parts.joinToString(", ").ifBlank { service.location.takeIf { it.isNotBlank() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityCategoryGrid(
    selected: ServiceCategory?,
    onSelect: (ServiceCategory) -> Unit
) {
    val visual = leoVisual()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS),
        maxItemsInEachRow = 3
    ) {
        serviceCategoryChips.forEach { chip ->
            val isSelected = selected == chip.category
            Surface(
                onClick = { onSelect(chip.category) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = LeoDimens.TouchMin),
                shape = RoundedCornerShape(LeoDimens.RadiusCard),
                color = if (isSelected) visual.primary else visual.surface,
                border = BorderStroke(1.dp, if (isSelected) visual.primary else visual.borderSoft)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = LeoDimens.SpaceS, vertical = LeoDimens.SpaceM),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        chip.icon,
                        contentDescription = chip.title,
                        tint = if (isSelected) visual.onPrimary else visual.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = chip.title,
                        style = LeoCaption,
                        color = if (isSelected) visual.onPrimary else visual.textPrimary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBF8, widthDp = 390, name = "CommunityServicesPreview")
@Composable
private fun CommunityServicesPreview() {
    ComunidappTheme {
        ComunidadScreen(onServiceClick = {})
    }
}
