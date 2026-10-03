package com.comunidapp.app.ui.map.google

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState
import com.comunidapp.app.domain.map.LeoVerMapMarker
import com.comunidapp.app.ui.map.MapsTileDiagnostics
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.leoVisual
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay

/**
 * Google Maps renderer only. No Places, Geocoding, Routes, Navigation, or Street View.
 */
@Composable
fun GoogleLeoVerMap(
    markers: List<LeoVerMapMarker>,
    modifier: Modifier = Modifier,
    camera: LeoVerMapCameraState = LeoVerMapCameraState(LeoVerMapCameraState.ARGENTINA_FALLBACK),
    userLocation: LeoVerGeoPoint? = null,
    showUserLocation: Boolean = false,
    interactive: Boolean = true,
    pinMode: Boolean = false,
    idleOnGestureOnly: Boolean = false,
    onMarkerClick: (LeoVerMapMarker) -> Unit = {},
    onMapClick: (LeoVerGeoPoint) -> Unit = {},
    onCameraIdle: (LeoVerMapCameraState) -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Log.w("LeoVerMaps", "package=${context.packageName}")
    }
    var tilesLoaded by remember { mutableStateOf(false) }
    var tilesTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(12_000)
        if (!tilesLoaded) {
            tilesTimedOut = true
            MapsTileDiagnostics.logLoadOnce(loaded = false)
        }
    }
    val myLocationAllowed = showUserLocation &&
        userLocation != null &&
        (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    val safeCamera = LeoVerGeoPoint.parseOrNull(camera.center.latitude, camera.center.longitude)
        ?.let { camera.copy(center = it) }
        ?: LeoVerMapCameraState(LeoVerMapCameraState.ARGENTINA_FALLBACK, camera.zoom)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(safeCamera.center.toLatLng(), safeCamera.zoom)
    }
    LaunchedEffect(safeCamera.center.latitude, safeCamera.center.longitude, safeCamera.zoom) {
        runCatching {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(safeCamera.center.toLatLng(), safeCamera.zoom)
            )
        }
    }
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            if (idleOnGestureOnly &&
                cameraPositionState.cameraMoveStartedReason != CameraMoveStartedReason.GESTURE
            ) {
                return@LaunchedEffect
            }
            val target = cameraPositionState.position.target
            val point = LeoVerGeoPoint.parseOrNull(target.latitude, target.longitude)
                ?: return@LaunchedEffect
            onCameraIdle(
                LeoVerMapCameraState(point, cameraPositionState.position.zoom)
            )
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = myLocationAllowed
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = interactive,
            scrollGesturesEnabled = interactive,
            zoomGesturesEnabled = interactive,
            rotationGesturesEnabled = interactive,
            myLocationButtonEnabled = showUserLocation
        ),
        onMapLoaded = {
            tilesLoaded = true
            MapsTileDiagnostics.logLoadOnce(loaded = true)
        },
        onMapClick = { latLng ->
            LeoVerGeoPoint.parseOrNull(latLng.latitude, latLng.longitude)?.let(onMapClick)
        }
    ) {
        markers.forEach { marker ->
            Marker(
                state = remember(marker.id, marker.position) {
                    MarkerState(position = marker.position.toLatLng())
                },
                title = marker.title,
                snippet = marker.subtitle,
                onClick = {
                    onMarkerClick(marker)
                    true
                }
            )
        }
        if (pinMode && markers.isEmpty()) {
            Marker(
                state = remember(safeCamera.center) {
                    MarkerState(position = safeCamera.center.toLatLng())
                },
                title = "Ubicación"
            )
        }
    }
    if (tilesTimedOut && !tilesLoaded) {
        val visual = leoVisual()
        androidx.compose.material3.Text(
            text = "No se pudieron cargar los tiles del mapa. Revisá Maps SDK for Android, billing y las restricciones de paquete/SHA en Google Cloud.",
            style = LeoCaption,
            color = visual.textSecondary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
    }
}

private fun LeoVerGeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)
