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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.comunidapp.app.data.local.LocationConsentStoreProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.CanonicalLostFoundRepository
import com.comunidapp.app.domain.location.ForegroundLocation
import com.comunidapp.app.domain.location.LocationConsentContracts
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
    val alreadyGranted = ForegroundLocation.hasForegroundPermission(context)
    LaunchedEffect(alreadyGranted) {
        if (alreadyGranted) onGranted()
    }
    if (alreadyGranted) return
    val scope = rememberCoroutineScope()
    val userId = remember { AuthProvider.repository.getCurrentUser()?.id.orEmpty() }
    val store = LocationConsentStoreProvider.instance
    var denied by remember {
        mutableStateOf(!ForegroundLocation.hasForegroundPermission(context) && store.rationaleAccepted(userId))
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            store.markPermanentlyDeniedHint(userId, false)
            onGranted()
        } else {
            denied = true
            val activity = context as? Activity
            val persist = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            store.markPermanentlyDeniedHint(userId, persist)
            onContinueWithout()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(LeoDimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
        Text(LocationConsentContracts.TITLE, style = LeoCardTitle, color = BrandText)
        Text(LocationConsentContracts.BODY, style = LeoBody, color = BrandText)
        Text(LocationConsentContracts.TREATMENT, style = LeoCaption, color = BrandTextSecondary)
        Text(LocationConsentContracts.NO_BACKGROUND, style = LeoCaption, color = BrandTextSecondary)
        if (denied) {
            Text(LocationConsentContracts.DENIED_HINT, style = LeoCaption, color = BrandTextSecondary)
        }
        Spacer(Modifier.height(LeoDimens.SpaceSm))
        LeoPrimaryButton(
            text = if (denied) LocationConsentContracts.CTA_ENABLE else LocationConsentContracts.CTA_ALLOW,
            onClick = {
                if (userId.isNotBlank()) store.markRationaleAccepted(userId)
                scope.launch {
                    runCatching { CanonicalLostFoundRepository.recordLocationConsent() }
                }
                if (ForegroundLocation.hasForegroundPermission(context)) {
                    onGranted()
                } else if (denied && store.permanentlyDeniedHint(userId)) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                } else {
                    launcher.launch(ForegroundLocation.permissions)
                }
            }
        )
        if (denied) {
            LeoOutlinedButton(
                text = "Continuar sin ubicación",
                onClick = onContinueWithout
            )
        }
    }
}
