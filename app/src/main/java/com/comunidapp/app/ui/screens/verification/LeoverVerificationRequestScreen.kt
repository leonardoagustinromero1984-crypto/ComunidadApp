package com.comunidapp.app.ui.screens.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.verification.VerificationDisplayPolicy
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoRequiredField
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.screens.location.LocationPinPicker
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.UrgentRed
import com.comunidapp.app.viewmodel.LeoverVerificationRequestViewModel

@Composable
fun LeoverVerificationRequestScreen(
    functionCode: String,
    organizationId: String? = null,
    onNavigateBack: () -> Unit
) {
    val viewModel: LeoverVerificationRequestViewModel = viewModel(
        key = "leover_verification_${functionCode}_${organizationId.orEmpty()}",
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return LeoverVerificationRequestViewModel(
                    SavedStateHandle(
                        mapOf(
                            "functionCode" to functionCode,
                            "organizationId" to organizationId
                        )
                    )
                ) as T
            }
        }
    )
    val ui by viewModel.ui.collectAsState()
    val needsOrg = functionCode.uppercase() in setOf("SHELTER", "NGO", "VETERINARY", "BUSINESS")
    val needsLocation = functionCode.uppercase() in setOf(
        "RESCUER", "FOSTER", "SHELTER", "NGO", "VETERINARY", "BUSINESS"
    )
    val publicOrg = functionCode.uppercase() in setOf("VETERINARY", "BUSINESS")
    val pending = ui.status.equals("PENDING", true)

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Solicitar verificación",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceMd)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            Text(
                VerificationDisplayPolicy.statusLabel(ui.status ?: "NOT_REQUESTED"),
                style = MaterialTheme.typography.titleMedium
            )
            if (ui.status.equals("VERIFIED", true)) {
                Text(VerificationDisplayPolicy.HELP_COPY, style = LeoCaption)
            } else if (ui.status.isNullOrBlank() || ui.status.equals("NOT_REQUESTED", true)) {
                Text(VerificationDisplayPolicy.UNVERIFIED_COPY, style = LeoCaption)
            }
            ui.note?.takeIf { it.isNotBlank() }?.let { Text("Revisión: $it", style = LeoCaption) }
            Text(
                "Para solicitar revisión necesitás perfil completo, contacto, ubicación base si la función la requiere, y aceptar los términos.",
                style = LeoCaption
            )
            if (needsLocation && !ui.pendingOrVerified) {
                LocationPinPicker(
                    selected = ui.pin,
                    onSelected = viewModel::updatePin,
                    required = true,
                    showError = ui.fieldErrors.any { it.contains("Ubicación") },
                    address = ui.address,
                    onAddressChange = { viewModel.updateAddress(it.label) },
                    privacyNote = if (publicOrg) {
                        "Esta dirección puede mostrarse en el perfil si así lo declaraste."
                    } else {
                        "LeoVer guarda la ubicación exacta para operar. En público solo se muestra la zona permitida."
                    }
                )
            }
            if (!ui.pendingOrVerified) {
                OutlinedTextField(
                    value = ui.evidence,
                    onValueChange = viewModel::updateEvidence,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Evidencia opcional (redes, matrícula, fotos…)") },
                    minLines = 3
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ui.terms, onCheckedChange = viewModel::updateTerms)
                    Text(LeoRequiredField.label("Acepto los términos de esta función"))
                }
            }
            if (ui.fieldErrors.isNotEmpty()) {
                Text(
                    "Para solicitar verificación completá:\n" + ui.fieldErrors.joinToString("\n") { "- $it" },
                    style = LeoCaption,
                    color = UrgentRed
                )
            }
            ui.message?.let {
                Text(
                    it,
                    style = LeoCaption,
                    color = if (it.contains("pendiente", true)) MaterialTheme.colorScheme.primary else UrgentRed
                )
            }
            if (pending) {
                Text(VerificationDisplayPolicy.PENDING_COPY, style = MaterialTheme.typography.titleSmall)
            } else if (!ui.pendingOrVerified) {
                LeoPrimaryButton(
                    text = if (ui.submitting) "Enviando…" else "Enviar solicitud",
                    onClick = { viewModel.submit(needsLocation, needsOrg) },
                    enabled = !ui.submitting
                )
            }
            if (ui.status.equals("REQUIRES_CORRECTION", true) && ui.requestId != null) {
                LeoOutlinedButton(
                    text = "Volver a enviar",
                    onClick = { viewModel.submit(needsLocation, needsOrg) }
                )
            }
        }
    }
}
