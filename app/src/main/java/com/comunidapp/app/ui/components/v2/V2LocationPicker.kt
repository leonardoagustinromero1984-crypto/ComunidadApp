package com.comunidapp.app.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.LocationDisplay
import com.comunidapp.app.data.model.LocationLevel
import com.comunidapp.app.data.model.LocationNode
import com.comunidapp.app.data.model.LocationSelection
import com.comunidapp.app.data.model.clearIncompatible
import com.comunidapp.app.data.model.displayOf
import com.comunidapp.app.data.model.localityContextLabel
import com.comunidapp.app.data.model.resolveSelection
import com.comunidapp.app.data.model.restoreSelection
import com.comunidapp.app.data.model.search
import com.comunidapp.app.data.model.searchLocalitiesInProvince
import com.comunidapp.app.data.model.selectionForLocality
import com.comunidapp.app.data.model.visibleLabel
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.LocationCatalogLoadState
import com.comunidapp.app.data.repository.LocationCatalogRepository
import com.comunidapp.app.domain.i18n.CountryCatalog
import com.comunidapp.app.domain.i18n.GeoDivisionLabels
import com.comunidapp.app.domain.i18n.MarketUxPolicy
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.NeutralBorder
import kotlinx.coroutines.launch

/**
 * Selector de ubicación visible: Provincia + Localidad (buscable).
 * Partido/Municipio y Zona quedan en el catálogo interno; no se piden al usuario.
 * [showFullHierarchy] reserva la jerarquía completa para administración.
 */
@Composable
fun V2LocationPicker(
    selection: LocationSelection,
    onSelectionChange: (LocationSelection) -> Unit,
    modifier: Modifier = Modifier,
    includeZone: Boolean = false,
    showFullHierarchy: Boolean = false,
    enabled: Boolean = true,
    catalog: LocationCatalogRepository = DataProvider.locationCatalogRepository
) {
    val nodes by catalog.nodes.collectAsState()
    val loadState by catalog.loadState.collectAsState()
    val loadError by catalog.loadErrorMessage.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(catalog) {
        catalog.refresh()
    }
    val safe = remember(nodes, selection) { nodes.clearIncompatible(selection) }
    val defaultCountry = remember(nodes) {
        nodes.firstOrNull {
            it.level == LocationLevel.COUNTRY &&
                it.active &&
                (it.code == CountryCatalog.INITIAL_COUNTRY_ISO || it.id == CountryCatalog.ARGENTINA.locationNodeId)
        }
    }
    LaunchedEffect(safe) {
        if (safe != selection) onSelectionChange(safe)
    }
    LaunchedEffect(defaultCountry?.id, safe.countryId, enabled) {
        if (enabled && safe.countryId == null && defaultCountry != null) {
            onSelectionChange(safe.copy(countryId = defaultCountry.id))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
    ) {
        when (loadState) {
            LocationCatalogLoadState.IDLE,
            LocationCatalogLoadState.LOADING -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Cargando provincias…", style = LeoCaption, color = BrandTextSecondary)
                }
            }
            LocationCatalogLoadState.ERROR -> {
                Text(
                    text = loadError ?: "No se pudieron cargar las provincias. Reintentá.",
                    color = BrandText,
                    style = LeoCaption
                )
                Button(onClick = { scope.launch { catalog.refresh() } }) {
                    Text("Reintentar")
                }
            }
            LocationCatalogLoadState.EMPTY -> {
                Text(
                    text = "No hay provincias disponibles en el catálogo.",
                    color = BrandTextSecondary,
                    style = LeoCaption
                )
            }
            LocationCatalogLoadState.LOADED -> {
                val countryIso = safe.countryId?.let { id -> nodes.firstOrNull { it.id == id }?.code }
                    ?: MarketUxPolicy.initialCountryIso()
                val operationalCountries = nodes.filter { node ->
                    node.level == LocationLevel.COUNTRY &&
                        node.active &&
                        CountryCatalog.canSelectForOnboarding(node.code)
                }
                if (showFullHierarchy || MarketUxPolicy.COUNTRY_UI_VISIBLE) {
                    V2SearchableLocationField(
                        label = GeoDivisionLabels.countryLabel(),
                        selected = safe.countryId?.let { id -> nodes.firstOrNull { it.id == id } },
                        options = { query ->
                            operationalCountries.filter { it.matchesQuery(query) }
                        },
                        enabled = enabled && operationalCountries.size > 1,
                        emptyHint = MarketUxPolicy.defaultCountryDisplayName(),
                        onSelect = { node ->
                            val id = node?.id ?: defaultCountry?.id
                            if (id != null && CountryCatalog.canSelectForOnboarding(
                                    node?.code ?: MarketUxPolicy.initialCountryIso()
                                )
                            ) {
                                onSelectionChange(safe.withCountry(id))
                            }
                        }
                    )
                }
                V2SearchableLocationField(
                    label = GeoDivisionLabels.administrativeAreaLabel(countryIso),
                    selected = safe.provinceId?.let { id -> nodes.firstOrNull { it.id == id } },
                    options = { query ->
                        nodes.search(query, LocationLevel.PROVINCE, parentId = safe.countryId)
                    },
                    enabled = enabled,
                    emptyHint = "No hay ${GeoDivisionLabels.administrativeAreaLabel(countryIso).lowercase()}",
                    onSelect = { onSelectionChange(safe.withProvince(it?.id)) }
                )
        if (showFullHierarchy) {
            V2SearchableLocationField(
                label = "Municipio / Partido",
                selected = safe.municipalityId?.let { id -> nodes.firstOrNull { it.id == id } },
                options = { query ->
                    nodes.search(query, LocationLevel.MUNICIPALITY, parentId = safe.provinceId)
                },
                enabled = enabled && safe.provinceId != null,
                emptyHint = if (safe.provinceId == null) {
                    "Elegí una provincia"
                } else {
                    "No hay municipios para esta provincia"
                },
                onSelect = { onSelectionChange(safe.withMunicipality(it?.id)) }
            )
            V2SearchableLocationField(
                label = "Localidad",
                selected = safe.localityId?.let { id -> nodes.firstOrNull { it.id == id } },
                options = { query ->
                    nodes.search(query, LocationLevel.LOCALITY, parentId = safe.municipalityId)
                },
                enabled = enabled && safe.municipalityId != null,
                emptyHint = if (safe.municipalityId == null) {
                    "Elegí un municipio o partido"
                } else {
                    "No hay localidades para este municipio"
                },
                onSelect = { onSelectionChange(safe.withLocality(it?.id)) }
            )
            if (includeZone) {
                V2SearchableLocationField(
                    label = "Zona / Barrio (opcional)",
                    selected = safe.zoneId?.let { id -> nodes.firstOrNull { it.id == id } },
                    options = { query ->
                        nodes.search(query, LocationLevel.ZONE, parentId = safe.localityId)
                    },
                    enabled = enabled && safe.localityId != null,
                    emptyHint = if (safe.localityId == null) {
                        "Elegí una localidad"
                    } else {
                        "Sin zonas cargadas para esta localidad"
                    },
                    onSelect = { onSelectionChange(safe.withZone(it?.id)) }
                )
            }
        } else {
            V2SearchableLocationField(
                label = GeoDivisionLabels.localityLabel(countryIso),
                selected = safe.localityId?.let { id -> nodes.firstOrNull { it.id == id } },
                options = { query ->
                    nodes.searchLocalitiesInProvince(safe.provinceId, query)
                },
                enabled = enabled && safe.provinceId != null,
                emptyHint = if (safe.provinceId == null) {
                    "Elegí ${GeoDivisionLabels.administrativeAreaLabel(countryIso).lowercase()}"
                } else {
                    "No hay ${GeoDivisionLabels.localityLabel(countryIso).lowercase()} para esta selección"
                },
                subtitleOf = { node -> nodes.localityContextLabel(node) },
                onSelect = { node ->
                    if (node == null) {
                        onSelectionChange(
                            safe.copy(municipalityId = null, localityId = null, zoneId = null)
                        )
                    } else {
                        onSelectionChange(nodes.selectionForLocality(node.id))
                    }
                }
            )
                }
            }
        }
    }
}

@Composable
fun V2LocationStringPicker(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    includeZone: Boolean = false,
    enabled: Boolean = true,
    catalog: LocationCatalogRepository = DataProvider.locationCatalogRepository
) {
    val nodes by catalog.nodes.collectAsState()
    var selection by remember {
        mutableStateOf(nodes.resolveSelection(label = value))
    }
    LaunchedEffect(value, nodes) {
        val currentLabel = nodes.visibleLabel(selection)
        if (value.isBlank() && !selection.isEmpty) return@LaunchedEffect
        if (value != currentLabel) {
            selection = nodes.resolveSelection(label = value)
        }
    }
    V2LocationPicker(
        selection = selection,
        onSelectionChange = { next ->
            selection = next
            onValueChange(nodes.visibleLabel(next))
        },
        modifier = modifier,
        includeZone = includeZone,
        enabled = enabled,
        catalog = catalog
    )
}

@Composable
fun V2LocationCityProvincePicker(
    city: String,
    province: String,
    onCityChange: (String) -> Unit,
    onProvinceChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    includeZone: Boolean = false,
    catalog: LocationCatalogRepository = DataProvider.locationCatalogRepository,
    initialLocalityId: String? = null,
    onLocalityIdChange: (String?) -> Unit = {}
) {
    val nodes by catalog.nodes.collectAsState()
    var selection by remember {
        mutableStateOf(
            nodes.restoreSelection(
                localityId = initialLocalityId,
                province = province,
                city = city
            )
        )
    }
    LaunchedEffect(nodes, city, province, initialLocalityId) {
        val restored = nodes.restoreSelection(
            localityId = initialLocalityId,
            province = province,
            city = city
        )
        if (selection.localityId.isNullOrBlank() && !restored.localityId.isNullOrBlank()) {
            selection = restored
        }
    }
    fun emit(next: LocationSelection) {
        selection = next
        val display: LocationDisplay = nodes.displayOf(next)
        onProvinceChange(display.provinceName)
        onCityChange(display.cityName)
        onLocalityIdChange(next.localityId)
    }
    V2LocationPicker(
        selection = selection,
        onSelectionChange = { emit(it) },
        modifier = modifier,
        includeZone = includeZone,
        enabled = enabled,
        catalog = catalog
    )
}

@Composable
fun V2SearchableCatalogField(
    label: String,
    selected: LocationNode?,
    options: List<LocationNode>,
    onSelect: (LocationNode?) -> Unit,
    enabled: Boolean = true,
    emptyHint: String = "Sin resultados"
) {
    V2SearchableLocationField(
        label = label,
        selected = selected,
        options = { query ->
            val q = query.trim()
            if (q.isEmpty()) options.take(64) else options.filter { it.matchesQuery(q) }.take(64)
        },
        enabled = enabled,
        emptyHint = emptyHint,
        onSelect = onSelect
    )
}

@Composable
internal fun V2SearchableLocationField(
    label: String,
    selected: LocationNode?,
    options: (String) -> List<LocationNode>,
    enabled: Boolean,
    emptyHint: String,
    onSelect: (LocationNode?) -> Unit,
    subtitleOf: (LocationNode) -> String? = { null }
) {
    var query by remember(selected?.id, enabled) { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val results = if (!enabled || !expanded) emptyList() else options(query)

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (expanded) query else selected?.name.orEmpty(),
            onValueChange = {
                query = it
                expanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .v2KeepVisibleOnFocus()
                .onFocusChanged { focus ->
                    if (focus.isFocused && enabled) {
                        expanded = true
                        query = ""
                    } else {
                        expanded = false
                    }
                },
            label = { Text(label) },
            enabled = enabled,
            singleLine = true,
            placeholder = { Text("Buscar…") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (selected != null && enabled) {
                    IconButton(
                        onClick = {
                            query = ""
                            expanded = false
                            onSelect(null)
                        }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                    }
                }
            }
        )
        if (expanded && enabled) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = BrandWhite,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .background(BrandWhite)
                ) {
                    if (results.isEmpty()) {
                        Text(
                            text = if (query.isBlank()) emptyHint else "Sin resultados para “$query”",
                            style = LeoCaption,
                            color = BrandTextSecondary,
                            modifier = Modifier.padding(LeoDimens.SpaceMd)
                        )
                    } else {
                        results.forEach { node ->
                            val subtitle = subtitleOf(node)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(node)
                                        query = ""
                                        expanded = false
                                    }
                                    .padding(horizontal = LeoDimens.SpaceMd, vertical = 12.dp)
                            ) {
                                Text(node.name, color = BrandText)
                                if (!subtitle.isNullOrBlank()) {
                                    Text(subtitle, style = LeoCaption, color = BrandTextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
