package com.comunidapp.app.ui.screens.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.support.SupportCategory
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
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
            modifier = contentModifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SnackbarHost(snackbar)
            Text("Categoría: ${uiState.category}")
            Button(
                onClick = { viewModel.setCategory(SupportCategory.ACCOUNT_ACCESS) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Acceso a cuenta") }
            Button(
                onClick = { viewModel.setCategory(SupportCategory.PRIVACY) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Privacidad") }
            Button(
                onClick = { viewModel.setCategory(SupportCategory.SAFETY) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Seguridad") }
            if (uiState.showSensitiveWarning) {
                Text(
                    "No incluyas contraseñas ni secretos en el mensaje.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            OutlinedTextField(
                value = uiState.subject,
                onValueChange = viewModel::setSubject,
                label = { Text("Asunto") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::setDescription,
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.submit() },
                enabled = uiState.phase != AdministrativeScreenPhase.Submitting,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Enviar") }
        }
    }
}
