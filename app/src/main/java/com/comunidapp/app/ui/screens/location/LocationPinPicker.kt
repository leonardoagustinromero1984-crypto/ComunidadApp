package com.comunidapp.app.ui.screens.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.location.AddressGeocoder
import com.comunidapp.app.domain.location.AddressSuggestion
import com.comunidapp.app.domain.location.ForegroundLocation
import com.comunidapp.app.domain.location.SharedLocationCapture
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoRequiredField
import com.comunidapp.app.ui.map.LeoVerMap
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.UrgentRed
import kotlinx.coroutines.launch

@Composable
fun LocationPinPicker(
    selected: LeoVerGeoPoint?,
    onSelected: (LeoVerGeoPoint) -> Unit,
    zoneLabel: String? = null,
    modifier: Modifier = Modifier,
    required: Boolean = true,
    showError: Boolean = false,
    address: String? = null,
    onAddressChange: (AddressSuggestion) -> Unit = {},
    privacyNote: String? = null,
    allowAddressSearch: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var camera by remember {
        mutableStateOf(
            LeoVerMapCameraState.cameraOrFallback(selected?.latitude, selected?.longitude, zoom = 15f)
        )
    }
    var locating by remember { mutableStateOf(false) }
    var locateError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
    var confirmed by remember { mutableStateOf<AddressSuggestion?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                fetchAndApply(context, onSelected, onAddressChange) { point, suggestion, err ->
                    if (point != null) {
                        camera = LeoVerMapCameraState(point, zoom = 16f)
                        confirmed = suggestion
                        locateError = null
                    } else {
                        locateError = err
                    }
                    locating = false
                }
            }
        } else {
            locating = false
            locateError = "Sin permiso de ubicación. Marcá el punto en el mapa."
        }
    }

    LaunchedEffect(address) {
        if (!address.isNullOrBlank() && confirmed == null) {
            query = address
        }
    }

    LaunchedEffect(Unit) {
        if (selected != null && !SharedLocationCapture.isFallback(selected)) return@LaunchedEffect
        if (!ForegroundLocation.hasForegroundPermission(context)) {
            locateError = "Sin permiso de ubicación. Marcá el punto en el mapa."
            return@LaunchedEffect
        }
        locating = true
        fetchAndApply(context, onSelected, onAddressChange) { point, suggestion, err ->
            if (point != null && !SharedLocationCapture.isFallback(point)) {
                camera = LeoVerMapCameraState(point, zoom = 16f)
                confirmed = suggestion
                if (suggestion != null) query = suggestion.label
                locateError = null
            } else {
                locateError = err ?: "No pudimos leer tu ubicación. Marcá el punto en el mapa."
            }
            locating = false
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)) {
        Text(
            text = zoneLabel?.takeIf { it.isNotBlank() }
                ?: LeoRequiredField.label("Dirección / domicilio", required),
            style = LeoCaption,
            color = if (showError && selected == null) UrgentRed else BrandTextSecondary
        )
        if (allowAddressSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { value ->
                    query = value
                    scope.launch {
                        suggestions = AddressGeocoder.suggest(context, value)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar domicilio") },
                singleLine = true
            )
            suggestions.forEach { suggestion ->
                Text(
                    text = suggestion.label,
                    style = LeoCaption,
                    color = BrandText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            confirmed = suggestion
                            query = suggestion.label
                            suggestions = emptyList()
                            camera = LeoVerMapCameraState(suggestion.point, zoom = 16f)
                            emitRealPoint(suggestion.point, onSelected)
                            onAddressChange(suggestion)
                            locateError = null
                        }
                        .padding(vertical = 4.dp)
                )
            }
        }
        Text(
            text = "Mové el mapa o tocá para marcar el lugar.",
            style = LeoCaption,
            color = BrandTextSecondary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            LeoVerMap(
                markers = emptyList(),
                camera = camera,
                userLocation = selected,
                showUserLocation = selected != null,
                pinMode = true,
                onMapClick = { point ->
                    locateError = null
                    if (!emitRealPoint(point, onSelected)) return@LeoVerMap
                    scope.launch {
                        val suggestion = AddressGeocoder.reverse(context, point)
                        if (suggestion != null) {
                            confirmed = suggestion
                            query = suggestion.label
                            onAddressChange(suggestion)
                        }
                    }
                },
                onCameraIdle = { idle ->
                    emitRealPoint(idle.center, onSelected)
                }
            )
        }
        LeoOutlinedButton(
            text = if (locating) "Obteniendo ubicación…" else "Usar mi ubicación",
            onClick = {
                locating = true
                locateError = null
                if (ForegroundLocation.hasForegroundPermission(context)) {
                    scope.launch {
                        fetchAndApply(context, onSelected, onAddressChange) { point, suggestion, err ->
                            if (point != null) {
                                camera = LeoVerMapCameraState(point, zoom = 16f)
                                confirmed = suggestion
                                if (suggestion != null) query = suggestion.label
                                locateError = null
                            } else {
                                locateError = err
                            }
                            locating = false
                        }
                    }
                } else {
                    permissionLauncher.launch(ForegroundLocation.permissions)
                }
            },
            enabled = !locating
        )
        val shown = confirmed
        if (shown != null || selected != null) {
            Text("Dirección seleccionada", style = LeoCaption, color = BrandText)
            Text(shown?.label ?: address ?: "Punto en el mapa", style = LeoCaption, color = BrandTextSecondary)
            shown?.locality?.let { Text("Localidad: $it", style = LeoCaption, color = BrandTextSecondary) }
            shown?.province?.let { Text("Provincia: $it", style = LeoCaption, color = BrandTextSecondary) }
        }
        privacyNote?.let { Text(it, style = LeoCaption, color = BrandTextSecondary) }
        if (showError && selected == null) {
            Text("Completá domicilio o marcá el lugar en el mapa.", style = LeoCaption, color = UrgentRed)
        }
        locateError?.let { Text(it, style = LeoCaption, color = UrgentRed) }
    }
}

private fun emitRealPoint(
    point: LeoVerGeoPoint,
    onSelected: (LeoVerGeoPoint) -> Unit
): Boolean {
    val real = SharedLocationCapture.realFixOrNull(point) ?: return false
    onSelected(real)
    return true
}

private suspend fun fetchAndApply(
    context: android.content.Context,
    onSelected: (LeoVerGeoPoint) -> Unit,
    onAddressChange: (AddressSuggestion) -> Unit,
    done: (LeoVerGeoPoint?, AddressSuggestion?, String?) -> Unit
) {
    if (!ForegroundLocation.isDeviceLocationEnabled(context)) {
        done(null, null, "Activá la ubicación del dispositivo o marcá el punto en el mapa.")
        return
    }
    val point = ForegroundLocation.current(context)
    if (point != null && SharedLocationCapture.realFixOrNull(point) != null) {
        onSelected(point)
        val suggestion = AddressGeocoder.reverse(context, point)
        if (suggestion != null) onAddressChange(suggestion)
        done(point, suggestion, null)
    } else {
        done(null, null, "No pudimos leer tu ubicación. Marcá el punto en el mapa.")
    }
}
