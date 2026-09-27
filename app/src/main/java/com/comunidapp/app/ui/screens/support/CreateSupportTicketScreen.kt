package com.comunidapp.app.ui.screens.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.support.SupportCategory
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTextField
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.UrgentRed
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.support.CreateSupportTicketViewModel

@Composable
fun CreateSupportTicketScreen(
    onNavigateBack: () -> Unit,
    onCreated: (String) -> Unit = {},
    viewModel: CreateSupportTicketViewModel = viewModel(factory = CreateSupportTicketViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    LaunchedEffect(uiState.createdTicketId) {
        uiState.createdTicketId?.let { onCreated(it) }
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    AdministrativePhaseHost(
        title = "Nuevo ticket",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            SnackbarHost(snackbar)
            Text("¿Sobre qué necesitás ayuda?", style = LeoCaption, color = BrandTextSecondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
            ) {
                LeoFilterChip(
                    label = "Acceso",
                    selected = uiState.category == SupportCategory.ACCOUNT_ACCESS,
                    onClick = { viewModel.setCategory(SupportCategory.ACCOUNT_ACCESS) }
                )
                LeoFilterChip(
                    label = "Privacidad",
                    selected = uiState.category == SupportCategory.PRIVACY,
                    onClick = { viewModel.setCategory(SupportCategory.PRIVACY) }
                )
                LeoFilterChip(
                    label = "Seguridad",
                    selected = uiState.category == SupportCategory.SAFETY,
                    onClick = { viewModel.setCategory(SupportCategory.SAFETY) }
                )
            }
            if (uiState.showSensitiveWarning) {
                Text(
                    "No incluyas contraseñas ni secretos en el mensaje.",
                    color = UrgentRed,
                    style = LeoCaption
                )
            }
            LeoTextField(
                value = uiState.subject,
                onValueChange = viewModel::setSubject,
                label = "Asunto"
            )
            LeoTextField(
                value = uiState.description,
                onValueChange = viewModel::setDescription,
                label = "Descripción",
                singleLine = false,
                minLines = 4
            )
            LeoPrimaryButton(
                text = "Enviar",
                onClick = { viewModel.submit() },
                enabled = uiState.phase != AdministrativeScreenPhase.Submitting
            )
        }
    }
}
