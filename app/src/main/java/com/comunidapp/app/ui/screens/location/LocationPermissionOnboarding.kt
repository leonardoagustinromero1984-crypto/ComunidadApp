package com.comunidapp.app.ui.screens.location

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.comunidapp.app.data.local.LocationConsentStoreProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.CanonicalLostFoundRepository
import com.comunidapp.app.domain.location.ForegroundLocation
import com.comunidapp.app.domain.location.LocationConsentContracts
import com.comunidapp.app.domain.location.LocationPermissionNext
import com.comunidapp.app.domain.location.LocationPermissionPolicy
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoBody
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import kotlinx.coroutines.launch

@Composable
fun LocationPermissionOnboarding(
    onGranted: () -> Unit,
    onContinueWithout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val userId = remember { AuthProvider.repository.getCurrentUser()?.id.orEmpty() }
    val store = LocationConsentStoreProvider.instance
    var revision by remember { mutableStateOf(0) }
    val grantedNow = ForegroundLocation.hasForegroundPermission(context)
    val servicesEnabled = ForegroundLocation.isDeviceLocationEnabled(context)
    val activity = context as? Activity
    val shouldShowRationale = activity != null && (
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) ||
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    val hasRequestedBefore = store.permissionRequested(userId)
    val next = LocationPermissionPolicy.next(
        granted = grantedNow,
        shouldShowRationale = shouldShowRationale,
        hasRequestedBefore = hasRequestedBefore,
        locationServicesEnabled = servicesEnabled
    )

    fun revalidate() {
        revision += 1
        if (ForegroundLocation.hasForegroundPermission(context)) {
            store.markPermanentlyDeniedHint(userId, false)
            if (ForegroundLocation.isDeviceLocationEnabled(context)) {
                onGranted()
            }
        }
    }

    DisposableEffect(lifecycleOwner, revision) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revalidate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            store.markPermanentlyDeniedHint(userId, false)
            revalidate()
        } else {
            val showAgain = activity != null && (
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            store.markPermanentlyDeniedHint(userId, !showAgain && store.permissionRequested(userId))
            revision += 1
        }
    }

    LaunchedEffect(next) {
        if (next == LocationPermissionNext.ALREADY_GRANTED) onGranted()
    }
    if (next == LocationPermissionNext.ALREADY_GRANTED) return

    val explain = next == LocationPermissionNext.EXPLAIN_AND_REQUEST ||
        next == LocationPermissionNext.OPEN_APP_SETTINGS ||
        next == LocationPermissionNext.OPEN_LOCATION_SOURCE_SETTINGS

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(LeoDimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
        Text(LocationConsentContracts.TITLE, style = LeoCardTitle, color = BrandText)
        Text(LocationConsentContracts.BODY, style = LeoBody, color = BrandText)
        if (explain) {
            Text(
                when (next) {
                    LocationPermissionNext.OPEN_LOCATION_SOURCE_SETTINGS ->
                        "Activá la ubicación del dispositivo para usar este aviso."
                    LocationPermissionNext.OPEN_APP_SETTINGS ->
                        "El permiso quedó bloqueado. Podés activarlo en la configuración de la app."
                    else -> LocationConsentContracts.DENIED_HINT
                },
                style = LeoCaption,
                color = BrandTextSecondary
            )
        }
        Spacer(Modifier.height(LeoDimens.SpaceSm))
        LeoPrimaryButton(
            text = if (explain) {
                LocationConsentContracts.CTA_ENABLE
            } else {
                LocationConsentContracts.CTA_ALLOW
            },
            onClick = {
                if (userId.isNotBlank()) store.markRationaleAccepted(userId)
                scope.launch {
                    runCatching { CanonicalLostFoundRepository.recordLocationConsent() }
                }
                when (LocationPermissionPolicy.next(
                    granted = ForegroundLocation.hasForegroundPermission(context),
                    shouldShowRationale = shouldShowRationale,
                    hasRequestedBefore = store.permissionRequested(userId),
                    locationServicesEnabled = ForegroundLocation.isDeviceLocationEnabled(context)
                )) {
                    LocationPermissionNext.ALREADY_GRANTED -> onGranted()
                    LocationPermissionNext.OPEN_LOCATION_SOURCE_SETTINGS -> {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                    LocationPermissionNext.OPEN_APP_SETTINGS -> {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    }
                    LocationPermissionNext.REQUEST_RUNTIME,
                    LocationPermissionNext.EXPLAIN_AND_REQUEST -> {
                        if (userId.isNotBlank()) store.markPermissionRequested(userId)
                        launcher.launch(ForegroundLocation.permissions)
                    }
                }
            }
        )
        if (explain) {
            LeoOutlinedButton(
                text = "Continuar sin ubicación",
                onClick = onContinueWithout
            )
        }
    }
}
