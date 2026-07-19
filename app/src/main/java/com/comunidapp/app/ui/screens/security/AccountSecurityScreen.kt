package com.comunidapp.app.ui.screens.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.auth.DeleteAccountCommand
import com.comunidapp.app.domain.auth.LegalDocumentConfig
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PasswordTextField
import com.comunidapp.app.viewmodel.AccountSecurityViewModel
import com.comunidapp.app.viewmodel.PasswordResetActiveViewModel
import com.comunidapp.app.viewmodel.SessionViewModel

@Composable
fun AccountSecurityScreen(
    onNavigateBack: () -> Unit,
    onAccountDeleted: () -> Unit,
    onNavigateToTerms: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    viewModel: AccountSecurityViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.deleteSuccess) {
        if (uiState.deleteSuccess) onAccountDeleted()
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Seguridad de la cuenta",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Cambiar contraseña", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pedimos tu contraseña actual antes de guardar una nueva.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            PasswordTextField(
                value = uiState.currentPassword,
                onValueChange = viewModel::onCurrentPasswordChange,
                label = "Contraseña actual"
            )
            Spacer(modifier = Modifier.height(8.dp))
            PasswordTextField(
                value = uiState.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = "Nueva contraseña"
            )
            Spacer(modifier = Modifier.height(8.dp))
            PasswordTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = "Confirmar nueva contraseña"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = viewModel::changePassword,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isChangingPassword && !uiState.isDeleting
            ) {
                if (uiState.isChangingPassword) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar contraseña")
                }
            }
            if (uiState.passwordChangeSuccess) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Contraseña actualizada.", color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("Eliminar cuenta", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Se borrarán tu perfil y datos asociados en Leover. " +
                    "Esta acción no se puede deshacer desde la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onNavigateToTerms) {
                Text("Ver términos (${LegalDocumentConfig.terms.version})")
            }
            TextButton(onClick = onNavigateToPrivacy) {
                Text("Ver privacidad (${LegalDocumentConfig.privacy.version})")
            }
            Spacer(modifier = Modifier.height(8.dp))
            PasswordTextField(
                value = uiState.deleteCurrentPassword,
                onValueChange = viewModel::onDeletePasswordChange,
                label = "Contraseña actual"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = uiState.deleteAcknowledged,
                    onCheckedChange = viewModel::onDeleteAcknowledgedChange,
                    enabled = !uiState.isDeleting
                )
                Text(
                    text = "Entiendo que eliminaré mi cuenta de forma permanente.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.deleteConfirmationText,
                onValueChange = viewModel::onDeleteConfirmationTextChange,
                label = { Text("Escribí ${DeleteAccountCommand.CONFIRMATION_PHRASE}") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isDeleting
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = viewModel::deleteAccount,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isDeleting && !uiState.isChangingPassword
            ) {
                if (uiState.isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Eliminar mi cuenta", color = MaterialTheme.colorScheme.error)
                }
            }

            uiState.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun PasswordResetActiveScreen(
    onSuccess: () -> Unit,
    onInvalidLink: () -> Unit,
    sessionViewModel: SessionViewModel,
    viewModel: PasswordResetActiveViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            sessionViewModel.clearPasswordResetActive()
            onSuccess()
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Nueva contraseña",
                showBackButton = true,
                onBackClick = {
                    sessionViewModel.clearPasswordResetActive()
                    onInvalidLink()
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Elegí una contraseña nueva (mínimo 8 caracteres).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            PasswordTextField(
                value = uiState.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = "Nueva contraseña"
            )
            Spacer(modifier = Modifier.height(12.dp))
            PasswordTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = "Confirmar contraseña"
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && !uiState.success
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar y continuar")
                }
            }
            uiState.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun LegalConsentRequiredScreen(
    sessionViewModel: SessionViewModel,
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacy: () -> Unit
) {
    var acceptedTerms by remember { mutableStateOf(false) }
    var acceptedPrivacy by remember { mutableStateOf(false) }
    val authState by sessionViewModel.authState.collectAsState()
    val errorMessage = (authState as? com.comunidapp.app.domain.auth.AuthState.AuthError)
        ?.error?.userMessage

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Actualizá tus consentimientos", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Para continuar necesitamos tu aceptación de las versiones vigentes. " +
                    (LegalDocumentConfig.terms.draftLabel ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = acceptedTerms, onCheckedChange = { acceptedTerms = it })
                TextButton(onClick = onNavigateToTerms) { Text("Acepto los términos") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = acceptedPrivacy, onCheckedChange = { acceptedPrivacy = it })
                TextButton(onClick = onNavigateToPrivacy) { Text("Acepto la privacidad") }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    sessionViewModel.acceptLegalConsents(acceptedTerms, acceptedPrivacy)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = acceptedTerms && acceptedPrivacy
            ) {
                Text("Continuar")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { sessionViewModel.logout() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar sesión")
            }
            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
