package com.comunidapp.app.ui.screens.comunidad

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoSearchBar
import com.comunidapp.app.ui.components.leo.LeoSectionHeader
import com.comunidapp.app.ui.components.leo.LeoServiceTile
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandGreenContainer
import com.comunidapp.app.ui.theme.BrandGreenDark
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.NeutralBorder
import com.comunidapp.app.viewmodel.ComunidadViewModel
import kotlinx.coroutines.launch

private data class ServiceCategoryTile(
    val category: ServiceCategory,
    val title: String,
    val nearTitle: String,
    val emptyTitle: String,
    val icon: ImageVector,
    /** Contenedor tonal del icono (determinista; no cambia por recomposición). */
    val iconTone: androidx.compose.ui.graphics.Color,
    val iconToneTint: androidx.compose.ui.graphics.Color
)

private val serviceTiles = listOf(
    ServiceCategoryTile(
        ServiceCategory.VET, "Veterinarias", "Veterinarias cerca de vos",
        "No encontramos resultados en esta categoría", Icons.Default.LocalHospital,
        BrandGreenContainer, BrandGreenDark
    ),
    ServiceCategoryTile(
        ServiceCategory.WALKER, "Paseadores", "Paseadores cerca de vos",
        "No encontramos resultados en esta categoría", Icons.Default.Pets,
        BrandOrangeContainer, BrandOrange
    ),
    ServiceCategoryTile(
        ServiceCategory.TRAINER, "Educadores", "Educadores cerca de vos",
        "No encontramos resultados en esta categoría", Icons.Default.School,
        BrandGreenContainer, BrandGreenDark
    ),
    ServiceCategoryTile(
        ServiceCategory.SHOP, "Tiendas", "Tiendas cerca de vos",
        "No encontramos resultados en esta categoría", Icons.Default.ShoppingBag,
        BrandOrangeContainer, BrandOrange
    )
)

@OptIn(ExperimentalMaterial3Api::class)
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
    var showFilters by remember { mutableStateOf(false) }
    var draftLocation by remember { mutableStateOf(uiState.locationQuery) }
    var draftActiveOnly by remember { mutableStateOf(uiState.activeOnly) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val selectedTile = serviceTiles.find { it.category == uiState.selectedCategory }
        ?: serviceTiles.first()

    val filteredServices = remember(
        services, searchQuery, uiState.selectedCategory, uiState.locationQuery, uiState.activeOnly
    ) {
        services.filter { service ->
            val matchesCategory = service.category == uiState.selectedCategory
            val q = searchQuery.trim()
            val loc = uiState.locationQuery.trim()
            val matchesQuery = q.isEmpty() ||
                service.name.contains(q, ignoreCase = true) ||
                service.description.contains(q, ignoreCase = true)
            val matchesLocation = loc.isEmpty() ||
                service.location.contains(loc, ignoreCase = true)
            val matchesActive = !uiState.activeOnly || service.active
            matchesCategory && matchesQuery && matchesLocation && matchesActive
        }
    }

    Scaffold(
        containerColor = BrandCream,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            LeoTopAppBar(
                title = "Servicios",
                subtitle = "Encontrá profesionales y comercios para tu mascota."
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
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            item {
                LeoSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Buscar servicios o profesionales",
                    onFilterClick = {
                        draftLocation = uiState.locationQuery
                        draftActiveOnly = uiState.activeOnly
                        showFilters = true
                    },
                    activeFiltersCount = uiState.activeFilterCount
                )
            }

            item {
                LeoSectionHeader(
                    title = "Categorías",
                    subtitle = "Elegí un tipo de servicio"
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
                ) {
                    serviceTiles.take(2).forEach { tile ->
                        val selected = uiState.selectedCategory == tile.category
                        LeoServiceTile(
                            title = tile.title,
                            icon = tile.icon,
                            onClick = { viewModel.selectCategory(tile.category) },
                            modifier = Modifier.weight(1f),
                            containerColor = if (selected) BrandOrangeContainer.copy(alpha = 0.55f) else BrandWhite,
                            iconContainerColor = if (selected) BrandOrangeContainer else tile.iconTone,
                            iconTint = if (selected) BrandOrange else tile.iconToneTint,
                            borderColor = if (selected) BrandOrange else NeutralBorder
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
                ) {
                    serviceTiles.drop(2).forEach { tile ->
                        val selected = uiState.selectedCategory == tile.category
                        LeoServiceTile(
                            title = tile.title,
                            icon = tile.icon,
                            onClick = { viewModel.selectCategory(tile.category) },
                            modifier = Modifier.weight(1f),
                            containerColor = if (selected) BrandOrangeContainer.copy(alpha = 0.55f) else BrandWhite,
                            iconContainerColor = if (selected) BrandOrangeContainer else tile.iconTone,
                            iconTint = if (selected) BrandOrange else tile.iconToneTint,
                            borderColor = if (selected) BrandOrange else NeutralBorder
                        )
                    }
                }
            }

            item {
                LeoSectionHeader(
                    title = selectedTile.nearTitle,
                    subtitle = when {
                        uiState.isLoading -> "Buscando…"
                        filteredServices.isEmpty() -> "Sin resultados en esta categoría"
                        else -> "${filteredServices.size} disponibles"
                    }
                )
            }

            if (uiState.isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LeoDimens.SpaceLg),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = BrandOrangeSoft)
                    }
                }
            } else if (filteredServices.isEmpty()) {
                item {
                    LeoEmptyState(
                        title = selectedTile.emptyTitle,
                        message = "Probá cambiando la ubicación o los filtros.",
                        actionLabel = "Limpiar filtros",
                        onAction = {
                            searchQuery = ""
                            viewModel.clearFilters()
                        },
                        icon = Icons.Default.Storefront
                    )
                }
            } else {
                items(filteredServices, key = { it.id }) { service ->
                    ServiceProfileCard(
                        service = service,
                        onClick = { onServiceClick(service.id) }
                    )
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = sheetState,
            containerColor = BrandCream
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LeoDimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
            ) {
                Text(text = "Filtrar servicios", style = LeoCardTitle, color = BrandText)
                Text(
                    text = if (uiState.activeFilterCount == 0) "Sin filtros activos"
                    else "${uiState.activeFilterCount} filtro(s) activos",
                    style = LeoCaption,
                    color = MutedText
                )
                OutlinedTextField(
                    value = draftLocation,
                    onValueChange = { draftLocation = it },
                    label = { Text("Localidad o zona") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                FilterChip(
                    selected = draftActiveOnly,
                    onClick = { draftActiveOnly = !draftActiveOnly },
                    label = { Text("Solo activos") }
                )
                LeoPrimaryButton(
                    text = "Aplicar",
                    onClick = {
                        viewModel.applyFilters(draftLocation, draftActiveOnly)
                        scope.launch {
                            runCatching { sheetState.hide() }
                            showFilters = false
                        }
                    }
                )
                LeoOutlinedButton(
                    text = "Limpiar",
                    onClick = {
                        draftLocation = ""
                        draftActiveOnly = false
                        viewModel.clearFilters()
                    }
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { sheetState.hide() }
                            showFilters = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancelar") }
            }
        }
    }
}

private fun categoryLabel(category: ServiceCategory): String = when (category) {
    ServiceCategory.VET -> "Veterinaria"
    ServiceCategory.WALKER -> "Paseador"
    ServiceCategory.TRAINER -> "Educador"
    ServiceCategory.SHOP -> "Tienda"
}

@Composable
fun ServiceProfileCard(
    service: ServiceProfile,
    onClick: () -> Unit
) {
    Surface(
        color = BrandWhite,
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        border = BorderStroke(1.dp, NeutralBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(LeoDimens.SpaceCompact),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PetImage(
                imageUrl = service.photoUrl,
                modifier = Modifier.size(64.dp),
                cornerRadius = 12.dp,
                contentDescription = service.name
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = LeoDimens.SpaceCompact)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = service.name,
                        style = LeoCardTitle,
                        color = BrandText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (service.active) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Activo",
                            tint = BrandGreen,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(16.dp)
                        )
                    }
                }
                Text(
                    text = "${categoryLabel(service.category)} · ${service.location}",
                    style = LeoCaption,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = service.description,
                    style = LeoCaption,
                    color = BrandText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Ver perfil",
                tint = MutedText
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390, name = "CommunityServicesPreview")
@Composable
private fun CommunityServicesPreview() {
    ComunidappTheme {
        ComunidadScreen(onServiceClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390, name = "CommunitySelectedCategoryPreview")
@Composable
private fun CommunitySelectedCategoryPreview() {
    ComunidappTheme {
        Row(
            modifier = Modifier.padding(LeoDimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            LeoServiceTile(
                title = "Veterinarias",
                icon = Icons.Default.LocalHospital,
                onClick = {},
                modifier = Modifier.weight(1f),
                containerColor = BrandOrangeContainer.copy(alpha = 0.55f),
                iconContainerColor = BrandOrangeContainer,
                iconTint = BrandOrange,
                borderColor = BrandOrange
            )
            LeoServiceTile(
                title = "Paseadores",
                icon = Icons.Default.Pets,
                onClick = {},
                modifier = Modifier.weight(1f),
                iconContainerColor = BrandOrangeContainer,
                iconTint = BrandOrange
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390, name = "CommunityEmptyPreview")
@Composable
private fun CommunityEmptyPreview() {
    ComunidappTheme {
        LeoEmptyState(
            title = "No encontramos resultados",
            message = "Probá cambiando la ubicación o los filtros.",
            actionLabel = "Cambiar ubicación",
            onAction = {},
            secondaryActionLabel = "Limpiar filtros",
            onSecondaryAction = {}
        )
    }
}
