package com.comunidapp.app.ui.screens.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.domain.auth.AuthDeepLinkKind
import com.comunidapp.app.domain.auth.AuthLinkNoticeStore
import com.comunidapp.app.domain.auth.validation.EmailOtpValidators
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.PasswordTextField
import com.comunidapp.app.ui.components.v2.V2SurfaceCard
import com.comunidapp.app.ui.components.v2.v2KeepVisibleOnFocus
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.viewmodel.EmailVerificationViewModel
import com.comunidapp.app.viewmodel.ForgotPasswordViewModel

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    onResetSuccess: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRemoteBackend = AuthProvider.isRemoteBackendEnabled

    LaunchedEffect(uiState.resetSuccess) {
        if (uiState.resetSuccess) onResetSuccess()
    }

    VisualDirectionPilot {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(
                title = "Recuperar contraseña",
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
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = BrandOrange
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (!uiState.emailSent) {
                    "Ingresá tu email y te enviaremos instrucciones para restablecer tu contraseña."
                } else if (isRemoteBackend) {
                    "Te enviamos un email con un link para crear una nueva contraseña. Revisá tu bandeja de entrada y spam."
                } else {
                    "Ingresá el código que recibiste con tu nueva contraseña."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = BrandTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                modifier = Modifier
                    .fillMaxWidth()
                    .v2KeepVisibleOnFocus(),
                singleLine = true,
                enabled = !uiState.emailSent || !isRemoteBackend,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            if (!uiState.emailSent) {
                Spacer(modifier = Modifier.height(24.dp))
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = BrandOrange,
                        strokeWidth = 2.dp
                    )
                } else {
                    LeoPrimaryButton(
                        text = if (isRemoteBackend) "Enviar link por email" else "Enviar código",
                        onClick = viewModel::sendResetEmail
                    )
                }
            } else if (isRemoteBackend) {
                Spacer(modifier = Modifier.height(24.dp))
                LeoPrimaryButton(text = "Volver al login", onClick = onNavigateBack)
            } else {
                uiState.mockToken?.let { token ->
                    Spacer(modifier = Modifier.height(12.dp))
                    V2SurfaceCard {
                        Text(text = "Código demo:", style = MaterialTheme.typography.labelMedium, color = BrandText)
                        Text(text = token, style = MaterialTheme.typography.titleLarge, color = BrandText)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = uiState.token,
                    onValueChange = viewModel::onTokenChange,
                    label = { Text("Código") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .v2KeepVisibleOnFocus(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
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
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = BrandOrange,
                        strokeWidth = 2.dp
                    )
                } else {
                    LeoPrimaryButton(text = "Restablecer contraseña", onClick = viewModel::resetPassword)
                }
            }

            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    }
}

@Composable
fun EmailVerificationScreen(
    email: String,
    onNavigateBack: () -> Unit,
    onVerified: () -> Unit,
    viewModel: EmailVerificationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val linkNotice by AuthLinkNoticeStore.notice.collectAsState()
    var otpCode by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(email) {
        viewModel.checkVerification(email)
    }

    LaunchedEffect(linkNotice) {
        val notice = linkNotice ?: return@LaunchedEffect
        if (notice.kind == AuthDeepLinkKind.LinkError) {
            linkError = notice.userMessage ?: "El enlace venció o ya fue utilizado."
        } else if (
            notice.kind == AuthDeepLinkKind.EmailConfirmation ||
            notice.kind == AuthDeepLinkKind.SessionCallback
        ) {
            viewModel.checkVerification(email)
        }
    }

    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) onVerified()
    }

    val visibleError = uiState.errorMessage ?: linkError

    VisualDirectionPilot {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(
                title = "Verificá tu correo",
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
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = BrandOrange
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Verificá tu correo", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Te enviamos un código de verificación a $email. Ingresalo para confirmar tu cuenta.",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = otpCode,
                onValueChange = { value ->
                    val sanitized = EmailOtpValidators.sanitizeInput(value)
                    if (sanitized != otpCode) {
                        viewModel.clearOtpFeedback()
                        linkError = null
                    }
                    otpCode = sanitized
                },
                label = { Text("Código de verificación") },
                modifier = Modifier
                    .fillMaxWidth()
                    .v2KeepVisibleOnFocus(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                supportingText = {
                    Text("Solo números")
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = BrandOrange,
                    strokeWidth = 2.dp
                )
            } else {
                LeoPrimaryButton(
                    text = "Verificar",
                    onClick = { viewModel.confirmWithOtp(email, otpCode) },
                    enabled = EmailOtpValidators.isValid(otpCode)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LeoOutlinedButton(
                text = if (uiState.resendCooldownSeconds > 0) {
                    "Reenviar en ${uiState.resendCooldownSeconds}s"
                } else {
                    "Reenviar código"
                },
                onClick = { viewModel.resendVerification(email) },
                enabled = !uiState.isLoading && uiState.resendCooldownSeconds == 0
            )
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.TextButton(
                onClick = onNavigateBack,
                enabled = !uiState.isLoading
            ) {
                Text("Cambiar correo")
            }

            uiState.successMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = msg, color = BrandOrange)
            }
            visibleError?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    }
}
