package com.comunidapp.app.ui.screens.lostfound

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.theme.BrandCream
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
    var showZonePicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = alerts.find { it.post.id == ui.selectedAlertId }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        viewModel.setLocationPermission(granted)
        if (!granted) {
            showZonePicker = true
        } else {
            // Sin FusedLocationProvider: pedimos zona manual o usamos catálogo.
            showZonePicker = true
        }
    }

    fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            viewModel.setLocationPermission(true)
            showZonePicker = true
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    Scaffold(
        containerColor = BrandCream,
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
                FilterChip(
                    selected = ui.viewMode == AlertMapViewMode.MAP,
                    onClick = { viewModel.setViewMode(AlertMapViewMode.MAP) },
                    label = { Text("Mapa") }
                )
                FilterChip(
                    selected = ui.viewMode == AlertMapViewMode.LIST,
                    onClick = { viewModel.setViewMode(AlertMapViewMode.LIST) },
                    label = { Text("Lista") }
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = ui.typeFilter == AlertMapTypeFilter.ALL,
                    onClick = { viewModel.setTypeFilter(AlertMapTypeFilter.ALL) },
                    label = { Text("Todas") }
                )
                FilterChip(
                    selected = ui.typeFilter == AlertMapTypeFilter.LOST,
                    onClick = { viewModel.setTypeFilter(AlertMapTypeFilter.LOST) },
                    label = { Text("Perdidas") }
                )
                FilterChip(
                    selected = ui.typeFilter == AlertMapTypeFilter.FOUND,
                    onClick = { viewModel.setTypeFilter(AlertMapTypeFilter.FOUND) },
                    label = { Text("Encontradas") }
                )
                listOf(5, 10, 25, 50).forEach { km ->
                    FilterChip(
                        selected = ui.distanceKm == km,
                        onClick = { viewModel.setDistanceKm(km) },
                        label = { Text("${km} km") }
                    )
                }
                FilterChip(
                    selected = ui.dateFilter == AlertDateFilter.LAST_7_DAYS,
                    onClick = {
                        viewModel.setDateFilter(
                            if (ui.dateFilter == AlertDateFilter.LAST_7_DAYS) AlertDateFilter.ANY
                            else AlertDateFilter.LAST_7_DAYS
                        )
                    },
                    label = { Text("7 días") }
                )
                FilterChip(
                    selected = ui.dateFilter == AlertDateFilter.LAST_30_DAYS,
                    onClick = {
                        viewModel.setDateFilter(
                            if (ui.dateFilter == AlertDateFilter.LAST_30_DAYS) AlertDateFilter.ANY
                            else AlertDateFilter.LAST_30_DAYS
                        )
                    },
                    label = { Text("30 días") }
                )
                PetSpecies.entries.take(3).forEach { species ->
                    FilterChip(
                        selected = ui.species == species,
                        onClick = {
                            viewModel.setSpecies(if (ui.species == species) null else species)
                        },
                        label = { Text(species.toDisplayName()) }
                    )
                }
            }
            TextButton(onClick = { showZonePicker = true }) {
                Text(
                    if (ui.selectedZone != null || ui.zoneQuery.isNotBlank()) {
                        "Zona: ${ui.selectedZone?.label ?: ui.zoneQuery}"
                    } else {
                        "Elegir una zona"
                    }
                )
            }

            when {
                ui.loadError != null -> {
                    LeoEmptyState(
                        title = "No pudimos cargar las alertas",
                        message = "Probá de nuevo o elegí otra zona.",
                        actionLabel = "Reintentar",
                        onAction = viewModel::retry,
                        secondaryActionLabel = "Elegir otra zona",
                        onSecondaryAction = { showZonePicker = true },
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
                        message = "También podés elegir manualmente una localidad o zona.",
                        actionLabel = "Permitir ubicación",
                        onAction = { requestLocation() },
                        secondaryActionLabel = "Elegir una zona",
                        onSecondaryAction = { showZonePicker = true },
                        icon = Icons.Default.MyLocation
                    )
                }
                ui.locationDisabled && ui.selectedZone == null -> {
                    LeoEmptyState(
                        title = "La ubicación del dispositivo está desactivada",
                        message = "Podés activarla en configuración o elegir una zona manualmente.",
                        actionLabel = "Abrir configuración",
                        onAction = {
                            context.startActivity(
                                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            )
                        },
                        secondaryActionLabel = "Elegir una zona",
                        onSecondaryAction = { showZonePicker = true },
                        icon = Icons.Default.MyLocation
                    )
                }
                alerts.isEmpty() -> {
                    LeoEmptyState(
                        title = "No hay alertas activas en esta zona",
                        message = "Mové el mapa, ampliá la distancia o revisá más tarde.",
                        actionLabel = "Ampliar búsqueda",
                        onAction = viewModel::expandSearch,
                        secondaryActionLabel = "Reportar mascota perdida",
                        onSecondaryAction = onReportLost,
                        icon = Icons.Default.Pets
                    )
                    LeoOutlinedButton(
                        text = "Informar mascota encontrada",
                        onClick = onReportFound,
                        modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
                    )
                }
                else -> {
                    if (ui.viewMode == AlertMapViewMode.MAP) {
                        AlertMapCanvas(
                            items = alerts.filter { it.onMap },
                            onMarkerClick = { viewModel.selectAlert(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        if (alerts.none { it.onMap }) {
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
            containerColor = BrandCream
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

    if (showZonePicker) {
        AlertDialog(
            onDismissRequest = { showZonePicker = false },
            title = { Text("Elegir una zona") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    viewModel.zoneOptions().forEach { zone ->
                        TextButton(
                            onClick = {
                                viewModel.selectZone(zone)
                                showZonePicker = false
                            }
                        ) { Text(zone.label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showZonePicker = false }) { Text("Cerrar") }
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        color = BrandWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PetImage(
                imageUrl = item.post.photoUrl,
                modifier = Modifier.size(64.dp),
                cornerRadius = 8.dp,
                contentDescription = item.post.petName
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = if (item.post.type == LostFoundType.LOST) "Perdida" else "Encontrada",
                    color = if (item.post.type == LostFoundType.LOST) BrandOrange else BrandGreen,
                    style = LeoCaption,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.post.petName ?: item.post.species.toDisplayName(),
                    style = LeoCardTitle,
                    color = BrandText
                )
                Text(text = item.zoneLabel, style = LeoCaption, color = MutedText)
                item.distanceKm?.let {
                    Text(text = "≈ ${"%.1f".format(it)} km", style = LeoCaption, color = MutedText)
                }
                Text(text = item.post.date, style = LeoCaption, color = MutedText)
            }
        }
    }
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
    viewModel: AlertMapViewModel = viewModel()
) {
    val alerts by viewModel.alerts.collectAsState()
    val item = alerts.find { it.post.id == postId }
    Scaffold(
        containerColor = BrandCream,
        topBar = {
            LeoTopAppBar(
                title = "Detalle de alerta",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        if (item == null) {
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
                AlertPreviewCard(item = item, onOpen = {})
                Text(
                    text = "Contacto: ${item.post.contactInfo}",
                    style = LeoCaption,
                    color = BrandText
                )
                Text(
                    text = "Zona aproximada: ${item.zoneLabel}",
                    style = LeoCaption,
                    color = MutedText
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA)
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
