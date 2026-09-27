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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTextField
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.PasswordTextField
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.ui.theme.BrandBackground
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

    VisualDirectionPilot {
    val visual = leoVisual()
    Scaffold(
        containerColor = visual.background,
        topBar = {
            LeoTopAppBar(
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
                .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceMd)
        ) {
            Text(
                if (uiState.canCreatePassword) "Crear contraseña" else "Cambiar contraseña",
                style = LeoCardTitle,
                color = visual.textPrimary
            )
            Spacer(modifier = Modifier.height(LeoDimens.SpaceMicro))
            if (uiState.canCreatePassword) {
                Text(
                    "Tu cuenta se creó con Google. Podés agregar una contraseña LeoVer para entrar también con email.",
                    style = LeoCaption,
                    color = MutedText
                )
                Spacer(modifier = Modifier.height(LeoDimens.SpaceCompact))
            }
            Text(
                text = "Pedimos tu contraseña actual antes de guardar una nueva.",
                style = LeoCaption,
                color = MutedText
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (!uiState.canCreatePassword) {
                PasswordTextField(
                    value = uiState.currentPassword,
                    onValueChange = viewModel::onCurrentPasswordChange,
                    label = "Contraseña actual"
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
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
            if (uiState.isChangingPassword) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                LeoPrimaryButton(
                    text = if (uiState.canCreatePassword) "Crear contraseña" else "Guardar contraseña",
                    onClick = viewModel::changePassword,
                    enabled = !uiState.isDeleting
                )
            }
            if (uiState.passwordChangeSuccess) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Contraseña actualizada.", color = visual.secondary, style = LeoCaption)
            }

            Spacer(modifier = Modifier.height(LeoDimens.SpaceLg))
            LeoHairline()
            Spacer(modifier = Modifier.height(LeoDimens.SpaceMd))
            Text("Eliminar cuenta", style = LeoCardTitle, color = visual.textPrimary)
            Spacer(modifier = Modifier.height(LeoDimens.SpaceMicro))
            Text(
                text = "Se borrarán tu perfil y datos asociados en LeoVer. " +
                    "Esta acción no se puede deshacer desde la app.",
                style = LeoCaption,
                color = MutedText
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
                    style = LeoCaption,
                    color = visual.textPrimary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LeoTextField(
                value = uiState.deleteConfirmationText,
                onValueChange = viewModel::onDeleteConfirmationTextChange,
                label = "Escribí ${DeleteAccountCommand.CONFIRMATION_PHRASE}",
                enabled = !uiState.isDeleting
            )
            Spacer(modifier = Modifier.height(12.dp))
            LeoOutlinedButton(
                text = if (uiState.isDeleting) "Eliminando…" else "Eliminar mi cuenta",
                onClick = viewModel::deleteAccount,
                enabled = !uiState.isDeleting && !uiState.isChangingPassword
            )

            uiState.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
            LeoPrimaryButton(
                text = if (uiState.isLoading) "Guardando…" else "Guardar y continuar",
                onClick = viewModel::submit,
                enabled = !uiState.isLoading && !uiState.success
            )
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
            LeoPrimaryButton(
                text = "Continuar",
                onClick = {
                    sessionViewModel.acceptLegalConsents(acceptedTerms, acceptedPrivacy)
                },
                enabled = acceptedTerms && acceptedPrivacy
            )
            Spacer(modifier = Modifier.height(12.dp))
            LeoOutlinedButton(
                text = "Cerrar sesión",
                onClick = { sessionViewModel.logout() }
            )
            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
