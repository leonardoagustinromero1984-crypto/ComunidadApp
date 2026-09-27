package com.comunidapp.app.ui.screens.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.comunidapp.app.data.repository.CanonicalLostFoundRepository
import com.comunidapp.app.domain.location.LocationConsentContracts
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import kotlinx.coroutines.launch

@Composable
fun ResponderBaseLocationScreen(
    onNavigateBack: () -> Unit
) {
    var pin by remember { mutableStateOf<LeoVerGeoPoint?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val active by com.comunidapp.app.domain.context.OperationalContextProvider.active.collectAsState()
    val organizationId = when (active) {
        is com.comunidapp.app.domain.context.OperationalContext.Organization,
        is com.comunidapp.app.domain.context.OperationalContext.Veterinary,
        is com.comunidapp.app.domain.context.OperationalContext.Shop -> active.entityId
        else -> null
    }
    androidx.compose.runtime.LaunchedEffect(organizationId) {
        CanonicalLostFoundRepository.getMyResponderBase(organizationId).onSuccess { snap ->
            if (snap.latitude != null && snap.longitude != null) {
                pin = LeoVerGeoPoint(snap.latitude, snap.longitude)
            }
            address = snap.address
        }
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Ubicación base",
                subtitle = "Zona de actividad para casos cercanos",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            Text(LocationConsentContracts.RESPONDER_BASE, style = LeoCaption, color = BrandTextSecondary)
            Text(
                "La coordenada exacta no se publica en tu perfil. La comunidad ve solo la zona.",
                style = LeoCaption,
                color = BrandTextSecondary
            )
            LocationPermissionOnboarding(
                onGranted = {},
                onContinueWithout = {}
            )
            LocationPinPicker(
                selected = pin,
                onSelected = { pin = it },
                address = address,
                onAddressChange = { address = it.label },
                privacyNote = "La coordenada exacta no se publica. La comunidad ve solo la zona."
            )
            message?.let { Text(it, style = LeoCaption, color = BrandText) }
            LeoPrimaryButton(
                text = "Disponible para recibir casos",
                onClick = {
                    scope.launch {
                        CanonicalLostFoundRepository.setReceiveNearbyCases(true)
                            .onSuccess { message = "Vas a recibir casos cercanos." }
                            .onFailure { message = "No se pudo activar la disponibilidad." }
                    }
                }
            )
            LeoPrimaryButton(
                text = "No recibir casos por ahora",
                onClick = {
                    scope.launch {
                        CanonicalLostFoundRepository.setReceiveNearbyCases(false)
                            .onSuccess { message = "Dejaste de recibir casos. Seguís verificado." }
                            .onFailure { message = "No se pudo actualizar la disponibilidad." }
                    }
                }
            )
            LeoPrimaryButton(
                text = "Guardar ubicación base",
                onClick = {
                    val point = pin
                    if (point == null) {
                        message = "Marcá tu zona de actividad en el mapa."
                        return@LeoPrimaryButton
                    }
                    scope.launch {
                        CanonicalLostFoundRepository.upsertResponderBase(
                            point.latitude,
                            point.longitude,
                            organizationId,
                            address
                        )
                            .onSuccess { message = "Ubicación base guardada." }
                            .onFailure { message = "No se pudo guardar la ubicación base." }
                    }
                }
            )
        }
    }
}
