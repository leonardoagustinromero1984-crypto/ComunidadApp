package com.comunidapp.app.ui.screens.lostfound

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.comunidapp.app.domain.location.ForegroundLocation
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState
import com.comunidapp.app.domain.map.LeoVerMapMarker
import com.comunidapp.app.ui.components.v2.V2LocationCityProvincePicker
import com.comunidapp.app.ui.map.LeoVerMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.domain.alerts.AlertDateFilter
import com.comunidapp.app.domain.alerts.AlertMapTypeFilter
import com.comunidapp.app.domain.alerts.AlertMapViewMode
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.NeutralBorder
import com.comunidapp.app.viewmodel.AlertMapItem
import com.comunidapp.app.viewmodel.AlertMapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostFoundMapScreen(
    onNavigateBack: () -> Unit,
    onOpenAlert: (String) -> Unit,
    onReportLost: () -> Unit,
    onReportFound: () -> Unit,
    viewModel: AlertMapViewModel = viewModel()
) {
    val ui by viewModel.uiState.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLocationPicker by remember { mutableStateOf(false) }
    var fallbackProvince by remember { mutableStateOf("") }
    var fallbackCity by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = alerts.find { it.post.id == ui.selectedAlertId }

    fun applyGps() {
        scope.launch {
            val point = ForegroundLocation.current(context)
            if (point != null) {
                viewModel.setLocationPermission(true)
                viewModel.setDeviceLocation(point.latitude, point.longitude)
            } else {
                viewModel.setDeviceLocation(null, null, disabled = true)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        viewModel.setLocationPermission(granted)
        if (granted) {
            applyGps()
        } else {
            showLocationPicker = true
        }
    }

    fun requestLocation() {
        if (ForegroundLocation.hasForegroundPermission(context)) {
            viewModel.setLocationPermission(true)
            applyGps()
        } else {
            permissionLauncher.launch(ForegroundLocation.permissions)
        }
    }

    LaunchedEffect(Unit) {
        if (ForegroundLocation.hasForegroundPermission(context)) {
            viewModel.setLocationPermission(true)
            applyGps()
        }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Mapa de alertas",
                subtitle = "Mascotas perdidas y encontradas cerca de vos.",
                showBackButton = true,
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(onClick = { requestLocation() }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Centrar ubicación", tint = BrandText)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeoFilterChip(
                    label = "Mapa",
                    selected = ui.viewMode == AlertMapViewMode.MAP,
                    onClick = { viewModel.setViewMode(AlertMapViewMode.MAP) }
                )
                LeoFilterChip(
                    label = "Lista",
                    selected = ui.viewMode == AlertMapViewMode.LIST,
                    onClick = { viewModel.setViewMode(AlertMapViewMode.LIST) }
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LeoFilterChip(
                    label = "Todas",
                    selected = ui.typeFilter == AlertMapTypeFilter.ALL,
                    onClick = { viewModel.setTypeFilter(AlertMapTypeFilter.ALL) }
                )
                LeoFilterChip(
                    label = "Perdidas",
                    selected = ui.typeFilter == AlertMapTypeFilter.LOST,
                    onClick = { viewModel.setTypeFilter(AlertMapTypeFilter.LOST) }
                )
                LeoFilterChip(
                    label = "Encontradas",
                    selected = ui.typeFilter == AlertMapTypeFilter.FOUND,
                    onClick = { viewModel.setTypeFilter(AlertMapTypeFilter.FOUND) }
                )
                listOf(1, 5, 10, 25).forEach { km ->
                    LeoFilterChip(
                        label = "${km} km",
                        selected = ui.distanceKm == km,
                        onClick = { viewModel.setDistanceKm(km) }
                    )
                }
                LeoFilterChip(
                    label = "7 días",
                    selected = ui.dateFilter == AlertDateFilter.LAST_7_DAYS,
                    onClick = {
                        viewModel.setDateFilter(
                            if (ui.dateFilter == AlertDateFilter.LAST_7_DAYS) AlertDateFilter.ANY
                            else AlertDateFilter.LAST_7_DAYS
                        )
                    }
                )
                LeoFilterChip(
                    label = "30 días",
                    selected = ui.dateFilter == AlertDateFilter.LAST_30_DAYS,
                    onClick = {
                        viewModel.setDateFilter(
                            if (ui.dateFilter == AlertDateFilter.LAST_30_DAYS) AlertDateFilter.ANY
                            else AlertDateFilter.LAST_30_DAYS
                        )
                    }
                )
                PetSpecies.entries.take(3).forEach { species ->
                    LeoFilterChip(
                        label = species.toDisplayName(),
                        selected = ui.species == species,
                        onClick = {
                            viewModel.setSpecies(if (ui.species == species) null else species)
                        }
                    )
                }
            }
            if (!ui.locationPermissionGranted) {
                TextButton(onClick = { showLocationPicker = true }) {
                    Text(
                        if (ui.selectedZone != null || ui.zoneQuery.isNotBlank()) {
                            "Ubicación: ${ui.selectedZone?.label ?: ui.zoneQuery}"
                        } else {
                            "Elegir provincia y localidad"
                        }
                    )
                }
            }

            when {
                ui.loadError != null -> {
                    LeoEmptyState(
                        title = "No pudimos cargar las alertas",
                        message = "Probá de nuevo o elegí otra zona.",
                        actionLabel = "Reintentar",
                        onAction = viewModel::retry,
                        secondaryActionLabel = "Elegir provincia y localidad",
                        onSecondaryAction = { showLocationPicker = true },
                        icon = Icons.Default.Pets
                    )
                }
                ui.isLoading -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandOrangeSoft)
                    }
                }
                !ui.locationPermissionGranted && ui.selectedZone == null && ui.zoneQuery.isBlank() -> {
                    LeoEmptyState(
                        title = "Usá tu ubicación para ver alertas cercanas",
                        message = "También podés elegir provincia y localidad.",
                        actionLabel = "Permitir ubicación",
                        onAction = { requestLocation() },
                        secondaryActionLabel = "Elegir provincia y localidad",
                        onSecondaryAction = { showLocationPicker = true },
                        icon = Icons.Default.MyLocation
                    )
                }
                ui.locationDisabled && ui.selectedZone == null -> {
                    LeoEmptyState(
                        title = "La ubicación del dispositivo está desactivada",
                        message = "Podés activarla en configuración o elegir provincia y localidad.",
                        actionLabel = "Abrir configuración",
                        onAction = {
                            context.startActivity(
                                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            )
                        },
                        secondaryActionLabel = "Elegir provincia y localidad",
                        onSecondaryAction = { showLocationPicker = true },
                        icon = Icons.Default.MyLocation
                    )
                }
                else -> {
                    if (ui.viewMode == AlertMapViewMode.MAP) {
                        val mapItems = alerts.filter { it.onMap }
                        val markers = mapItems.mapNotNull { item ->
                            val lat = item.displayLatitude ?: return@mapNotNull null
                            val lng = item.displayLongitude ?: return@mapNotNull null
                            val position = LeoVerGeoPoint.parseOrNull(lat, lng) ?: return@mapNotNull null
                            LeoVerMapMarker(
                                id = item.post.id,
                                position = position,
                                title = item.post.petName ?: item.post.species.toDisplayName(),
                                subtitle = if (item.post.type == LostFoundType.LOST) "Perdida" else "Encontrada",
                                distanceKm = item.distanceKm
                            )
                        }
                        val cameraCenter = LeoVerGeoPoint.parseOrNull(ui.anchorLatitude, ui.anchorLongitude)
                            ?: LeoVerMapCameraState.ARGENTINA_FALLBACK
                        LeoVerMap(
                            markers = markers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            camera = LeoVerMapCameraState(center = cameraCenter, zoom = 14f),
                            userLocation = LeoVerGeoPoint.parseOrNull(ui.anchorLatitude, ui.anchorLongitude),
                            showUserLocation = ui.locationPermissionGranted &&
                                ForegroundLocation.hasForegroundPermission(context),
                            onMarkerClick = { marker -> viewModel.selectAlert(marker.id) }
                        )
                        if (alerts.isEmpty()) {
                            Text(
                                text = "No hay alertas activas cerca. Ampliá la distancia o publicá un aviso.",
                                style = LeoCaption,
                                color = MutedText
                            )
                        } else if (alerts.none { it.onMap }) {
                            Text(
                                text = "Hay alertas en lista sin coordenadas GPS. Cambiá a Lista para verlas.",
                                style = LeoCaption,
                                color = MutedText
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(alerts, key = { it.post.id }) { item ->
                                AlertListCard(
                                    item = item,
                                    onClick = { onOpenAlert(item.post.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectAlert(null) },
            sheetState = sheetState,
            containerColor = BrandBackground
        ) {
            AlertPreviewCard(
                item = item,
                onOpen = {
                    viewModel.selectAlert(null)
                    onOpenAlert(item.post.id)
                }
            )
        }
    }

    if (showLocationPicker) {
        AlertDialog(
            onDismissRequest = { showLocationPicker = false },
            title = { Text("Provincia y localidad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    V2LocationCityProvincePicker(
                        city = fallbackCity,
                        province = fallbackProvince,
                        includeZone = false,
                        onCityChange = { fallbackCity = it },
                        onProvinceChange = { fallbackProvince = it },
                        onLocalityIdChange = { localityId ->
                            viewModel.setZoneQuery(
                                listOf(fallbackCity, fallbackProvince).filter { it.isNotBlank() }.joinToString(", ")
                            )
                            val nodes = com.comunidapp.app.data.provider.DataProvider.locationCatalogRepository.nodes.value
                            val node = nodes.firstOrNull { it.id == localityId }
                            val lat = node?.centroidLat
                            val lng = node?.centroidLng
                            if (lat != null && lng != null) {
                                viewModel.setDeviceLocation(lat, lng)
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationPicker = false }) { Text("Listo") }
            }
        )
    }
}

@Composable
private fun AlertMapCanvas(
    items: List<AlertMapItem>,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lostColor = BrandOrange
    val foundColor = BrandGreen
    val lats = items.mapNotNull { it.displayLatitude }
    val lngs = items.mapNotNull { it.displayLongitude }
    val minLat = lats.minOrNull() ?: -34.7
    val maxLat = lats.maxOrNull() ?: -34.5
    val minLng = lngs.minOrNull() ?: -58.5
    val maxLng = lngs.maxOrNull() ?: -58.3

    Box(
        modifier = modifier
            .background(BrandWhite, RoundedCornerShape(LeoDimens.RadiusCard))
            .border(1.dp, NeutralBorder, RoundedCornerShape(LeoDimens.RadiusCard))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Fondo sutil
            drawRect(Color(0xFFF3F0E8))
            if (items.isEmpty()) return@Canvas
            val pad = 48f
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            val latSpan = (maxLat - minLat).takeIf { it > 0.0001 } ?: 0.05
            val lngSpan = (maxLng - minLng).takeIf { it > 0.0001 } ?: 0.05
            // Clustering simple por celda
            val cells = mutableMapOf<Pair<Int, Int>, MutableList<AlertMapItem>>()
            items.forEach { item ->
                val lat = item.displayLatitude ?: return@forEach
                val lng = item.displayLongitude ?: return@forEach
                val cx = (((lng - minLng) / lngSpan) * 8).toInt().coerceIn(0, 7)
                val cy = (((maxLat - lat) / latSpan) * 8).toInt().coerceIn(0, 7)
                cells.getOrPut(cx to cy) { mutableListOf() }.add(item)
            }
            cells.values.forEach { group ->
                val item = group.first()
                val lat = item.displayLatitude ?: return@forEach
                val lng = item.displayLongitude ?: return@forEach
                val x = pad + (((lng - minLng) / lngSpan) * w).toFloat()
                val y = pad + (((maxLat - lat) / latSpan) * h).toFloat()
                val color = if (item.post.type == LostFoundType.LOST) lostColor else foundColor
                val radius = if (group.size > 1) 18f else 14f
                drawCircle(color = color, radius = radius, center = Offset(x, y))
                if (group.size > 1) {
                    drawCircle(color = BrandWhite, radius = 6f, center = Offset(x, y))
                }
            }
        }
        // Hit targets aproximados
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(LeoDimens.SpaceSm),
            verticalArrangement = Arrangement.Bottom
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendDot(BrandOrange, "Perdida")
                LegendDot(BrandGreen, "Encontrada")
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.height(120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items.take(8), key = { it.post.id }) { item ->
                    Text(
                        text = "${if (item.post.type == LostFoundType.LOST) "Perdida" else "Encontrada"} · ${item.post.petName ?: item.post.species.toDisplayName()} · ${item.zoneLabel}",
                        style = LeoCaption,
                        color = BrandText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMarkerClick(item.post.id) }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = " $label",
            style = LeoCaption,
            color = MutedText
        )
    }
}

@Composable
private fun AlertListCard(item: AlertMapItem, onClick: () -> Unit) {
    val typeLabel = if (item.post.type == LostFoundType.LOST) "Perdida" else "Encontrada"
    val distance = item.distanceKm?.let { "≈ ${"%.1f".format(it)} km" }
    LeoListRow(
        title = item.post.petName ?: item.post.species.toDisplayName(),
        subtitle = listOfNotNull(typeLabel, item.zoneLabel, distance, item.post.date)
            .joinToString(" · "),
        leading = {
            PetImage(
                imageUrl = item.post.photoUrl,
                modifier = Modifier.size(64.dp),
                cornerRadius = 8.dp,
                contentDescription = item.post.petName
            )
        },
        onClick = onClick
    )
}

@Composable
private fun AlertPreviewCard(item: AlertMapItem, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LeoDimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item.post.photoUrl?.let {
            PetImage(
                imageUrl = it,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                cornerRadius = 12.dp,
                contentDescription = item.post.petName
            )
        }
        Text(
            text = item.post.petName ?: item.post.species.toDisplayName(),
            style = LeoCardTitle,
            color = BrandText
        )
        Text(
            text = if (item.post.type == LostFoundType.LOST) "Alerta: mascota perdida" else "Alerta: mascota encontrada",
            color = if (item.post.type == LostFoundType.LOST) BrandOrange else BrandGreen,
            style = LeoCaption
        )
        Text(text = item.zoneLabel, style = LeoCaption, color = MutedText)
        item.distanceKm?.let {
            Text(text = "Distancia estimada: ≈ ${"%.1f".format(it)} km", style = LeoCaption, color = MutedText)
        }
        Text(text = item.post.date, style = LeoCaption, color = MutedText)
        Text(
            text = item.post.description,
            style = LeoCaption,
            color = BrandText,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(text = "Estado: Activa", style = LeoCaption, color = BrandGreen)
        LeoPrimaryButton(text = "Ver alerta", onClick = onOpen)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun LostFoundDetailScreen(
    postId: String,
    onNavigateBack: () -> Unit,
    onCompleteAnimal: (String) -> Unit = {},
    viewModel: AlertMapViewModel = viewModel()
) {
    val alerts by viewModel.alerts.collectAsState()
    val item = alerts.find { it.post.id == postId }
    val posts by com.comunidapp.app.data.provider.DataProvider.lostFoundRepository
        .observeLostFoundPosts()
        .collectAsState()
    val post = item?.post ?: posts.find { it.id == postId }
    var claimMessage by remember { mutableStateOf<String?>(null) }
    var claimedPetId by remember { mutableStateOf<String?>(null) }
    var matchCandidates by remember {
        mutableStateOf<List<com.comunidapp.app.data.repository.LostFoundMatchCandidate>>(emptyList())
    }
    val currentUserId = com.comunidapp.app.data.repository.AuthProvider.repository.getCurrentUser()?.id
    val myLost = posts.filter {
        it.type == LostFoundType.LOST &&
            it.status == LostFoundStatus.ACTIVE &&
            it.authorId == currentUserId
    }
    val scope = rememberCoroutineScope()
    val repo = com.comunidapp.app.data.provider.DataProvider.lostFoundRepository
    LaunchedEffect(post?.id, post?.isCustodian, post?.status) {
        val found = post
        if (found != null && found.type == LostFoundType.FOUND && found.isCustodian) {
            matchCandidates = repo.listFoundMatchCandidates(found.id).getOrDefault(emptyList())
        }
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Detalle de alerta",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        if (post == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No encontramos esta alerta", color = BrandText)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(LeoDimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item != null) {
                    AlertPreviewCard(item = item, onOpen = {})
                }
                Text(
                    text = "Zona aproximada: ${item?.zoneLabel ?: post.location}",
                    style = LeoCaption,
                    color = MutedText
                )
                Text(
                    text = when (post.status) {
                        LostFoundStatus.CLAIMED -> "Caso tomado"
                        LostFoundStatus.IN_CARE -> "Animal en cuidado"
                        LostFoundStatus.RESOLVED -> "Resuelto"
                        LostFoundStatus.CANCELLED -> "Cancelado"
                        LostFoundStatus.ACTIVE -> "Abierto"
                    },
                    style = LeoCaption,
                    color = BrandGreen
                )
                claimMessage?.let { Text(it, style = LeoCaption, color = BrandText) }
                if (post.type == LostFoundType.FOUND && post.status == LostFoundStatus.ACTIVE && post.canClaim) {
                    LeoPrimaryButton(
                        text = if (claimMessage == "Procesando aceptación…") "Procesando aceptación…" else "Tomar caso",
                        onClick = {
                            scope.launch {
                                claimMessage = "Procesando aceptación…"
                                val result = repo.claimLostFound(post.id)
                                result.onSuccess { claimed ->
                                    if (claimed.alreadyTaken) {
                                        claimMessage = "El caso fue asignado a un colaborador más cercano."
                                    } else {
                                        claimMessage = "Tomaste el caso."
                                        claimedPetId = claimed.petId
                                    }
                                }.onFailure { error ->
                                    val raw = error.message.orEmpty()
                                    claimMessage = when {
                                        raw.contains("ALERT_CLAIM_NOT_NEAREST", true) ->
                                            "El caso fue asignado a un colaborador más cercano."
                                        raw.contains("ALERT_ALREADY_CLAIMED", true) ->
                                            "El caso fue asignado a un colaborador más cercano."
                                        else -> "No se pudo tomar el caso."
                                    }
                                }
                            }
                        }
                    )
                }
                val petId = claimedPetId ?: post.petId
                if (!petId.isNullOrBlank() && (post.isCustodian || post.status == LostFoundStatus.CLAIMED || claimedPetId != null)) {
                    LeoPrimaryButton(
                        text = "Completar datos del animal",
                        onClick = { onCompleteAnimal(petId.orEmpty()) }
                    )
                }
                if (post.type == LostFoundType.FOUND &&
                    post.status in setOf(LostFoundStatus.ACTIVE, LostFoundStatus.CLAIMED) &&
                    myLost.isNotEmpty() &&
                    currentUserId != null &&
                    currentUserId != post.authorId
                ) {
                    Text(
                        text = "Si reconocés al animal, podés avisar sin cerrar el caso.",
                        style = LeoCaption,
                        color = MutedText
                    )
                    myLost.forEach { lost ->
                        LeoPrimaryButton(
                            text = "Podría ser mi mascota" +
                                (lost.petName?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                            onClick = {
                                scope.launch {
                                    val result = repo.assertFoundMightBeMine(post.id, lost.id)
                                    claimMessage = if (result.isSuccess) {
                                        "Avisamos a quien tiene al animal. Eso no te convierte en dueño todavía."
                                    } else {
                                        "No se pudo enviar el aviso."
                                    }
                                }
                            }
                        )
                        LeoPrimaryButton(
                            text = "No es mi mascota",
                            onClick = {
                                scope.launch {
                                    val result = repo.rejectFoundMightBeMine(post.id, lost.id)
                                    claimMessage = if (result.isSuccess) {
                                        "Registramos que no es tu mascota. El caso sigue abierto."
                                    } else {
                                        "No se pudo registrar el rechazo."
                                    }
                                }
                            }
                        )
                    }
                }
                if (post.status == LostFoundStatus.CLAIMED && post.isCustodian) {
                    LeoPrimaryButton(
                        text = "Animal recibido / En cuidado",
                        onClick = {
                            scope.launch {
                                val result = repo.markLostFoundInCare(post.id)
                                claimMessage = if (result.isSuccess) {
                                    "El animal quedó registrado bajo cuidado."
                                } else {
                                    "No se pudo confirmar la recepción."
                                }
                            }
                        }
                    )
                }
                if (post.isCustodian) {
                    matchCandidates.filter { it.status.equals("PENDING", true) && it.assertedBy != null }
                        .forEach { candidate ->
                            LeoPrimaryButton(
                                text = "Sí, corresponde",
                                onClick = {
                                    scope.launch {
                                        val result = repo.confirmFoundOwnerMatch(candidate.id)
                                        claimMessage = if (result.isSuccess) {
                                            "Match confirmado. Se unificó con la mascota perdida."
                                        } else {
                                            "No se pudo confirmar el match."
                                        }
                                        matchCandidates = repo.listFoundMatchCandidates(post.id)
                                            .getOrDefault(emptyList())
                                    }
                                }
                            )
                            LeoPrimaryButton(
                                text = "No corresponde",
                                onClick = {
                                    scope.launch {
                                        val result = repo.rejectFoundOwnerMatch(candidate.id)
                                        claimMessage = if (result.isSuccess) {
                                            "Candidato rechazado. El caso y los demás avisos siguen."
                                        } else {
                                            "No se pudo rechazar."
                                        }
                                        matchCandidates = repo.listFoundMatchCandidates(post.id)
                                            .getOrDefault(emptyList())
                                    }
                                }
                            )
                        }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8)
@Composable
private fun AlertMapPreview() {
    ComunidappTheme {
        AlertListCard(
            item = AlertMapItem(
                post = LostFoundPost(
                    id = "p1",
                    authorId = "u1",
                    authorName = "Demo",
                    type = LostFoundType.LOST,
                    petName = "Pelusa",
                    species = PetSpecies.CAT,
                    location = "Palermo, CABA",
                    description = "Gata blanca",
                    contactInfo = "demo@email.com",
                    status = LostFoundStatus.ACTIVE,
                    latitude = -34.5889,
                    longitude = -58.4300,
                    date = "05/08/2026"
                ),
                zoneLabel = "Palermo, CABA",
                displayLatitude = -34.589,
                displayLongitude = -58.430,
                distanceKm = 1.2,
                onMap = true
            ),
            onClick = {}
        )
    }
}
