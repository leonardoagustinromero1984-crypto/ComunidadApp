package com.comunidapp.app.ui.map.google

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState
import com.comunidapp.app.domain.map.LeoVerMapMarker
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

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
    onMarkerClick: (LeoVerMapMarker) -> Unit = {},
    onMapClick: (LeoVerGeoPoint) -> Unit = {},
    onCameraIdle: (LeoVerMapCameraState) -> Unit = {}
) {
    val context = LocalContext.current
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
            val target = cameraPositionState.position.target
            val point = LeoVerGeoPoint.parseOrNull(target.latitude, target.longitude)
                ?: return@LaunchedEffect
            onCameraIdle(
                LeoVerMapCameraState(point, cameraPositionState.position.zoom)
            )
        }
    }
    GoogleMap(
        modifier = modifier.fillMaxSize(),
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
                state = MarkerState(position = safeCamera.center.toLatLng()),
                title = "Ubicación"
            )
        }
    }
}

private fun LeoVerGeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)
