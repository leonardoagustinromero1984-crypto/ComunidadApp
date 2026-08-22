package com.comunidapp.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comunidapp.app.BuildConfig
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState
import com.comunidapp.app.domain.map.LeoVerMapMarker
import com.comunidapp.app.ui.map.google.GoogleLeoVerMap
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.leoVisual

/**
 * Provider-neutral map entry. Features must call this, never GoogleMap directly.
 * Camera movement must not trigger remote search — that is a caller contract.
 */
@Composable
fun LeoVerMap(
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
    if (!mapsApiKeyConfigured()) {
        val visual = leoVisual()
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Mapa no configurado. Agregá MAPS_API_KEY en local.properties (solo Maps SDK, sin Places).",
                style = LeoCaption,
                color = visual.textSecondary,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }
    GoogleLeoVerMap(
        markers = markers,
        modifier = modifier,
        camera = camera,
        userLocation = userLocation,
        showUserLocation = showUserLocation,
        interactive = interactive,
        pinMode = pinMode,
        onMarkerClick = onMarkerClick,
        onMapClick = onMapClick,
        onCameraIdle = onCameraIdle
    )
}

fun mapsApiKeyConfigured(): Boolean {
    val key = BuildConfig.MAPS_API_KEY.trim()
    return key.isNotBlank() && key != "MAPS_API_KEY_MISSING"
}
