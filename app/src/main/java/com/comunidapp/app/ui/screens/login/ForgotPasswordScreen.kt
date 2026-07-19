package com.comunidapp.app.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.comunidapp.app.domain.auth.EmailMasking
import com.comunidapp.app.domain.auth.validation.EmailOtpValidators
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PasswordTextField
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

    Scaffold(
        topBar = {
            ComunidappTopBar(
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
                tint = MaterialTheme.colorScheme.primary
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.emailSent || !isRemoteBackend,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            if (!uiState.emailSent) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = viewModel::sendResetEmail,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (isRemoteBackend) "Enviar link por email" else "Enviar código")
                    }
                }
            } else if (isRemoteBackend) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Volver al login")
                }
            } else {
                uiState.mockToken?.let { token ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "Código demo:", style = MaterialTheme.typography.labelMedium)
                            Text(text = token, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = uiState.token,
                    onValueChange = viewModel::onTokenChange,
                    label = { Text("Código") },
                    modifier = Modifier.fillMaxWidth(),
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
                Button(
                    onClick = viewModel::resetPassword,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Restablecer contraseña")
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
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
    val isRemoteBackend = AuthProvider.isRemoteBackendEnabled
    var otpCode by remember { mutableStateOf("") }

    LaunchedEffect(email) {
        viewModel.checkVerification(email)
    }

    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) onVerified()
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Confirmar email",
                showBackButton = true,
                onBackClick = onNavigateBack
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
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Confirmá tu email", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enviamos un link de confirmación a:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = EmailMasking.mask(email),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isRemoteBackend) {
                    "${EmailOtpValidators.PROMPT_MESSAGE}. " +
                        "Si el link no abre la app, este código es la forma más confiable de confirmar."
                } else {
                    "Modo demo: ${EmailOtpValidators.PROMPT_MESSAGE} o tocá \"Ya confirmé con el link\"."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = otpCode,
                onValueChange = { value ->
                    val sanitized = EmailOtpValidators.sanitizeInput(value)
                    if (sanitized != otpCode) {
                        viewModel.clearOtpFeedback()
                    }
                    otpCode = sanitized
                },
                label = { Text("Código") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text("Solo números · ${EmailOtpValidators.MIN_LENGTH}–${EmailOtpValidators.MAX_LENGTH} dígitos")
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.confirmWithOtp(email, otpCode) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && EmailOtpValidators.isValid(otpCode)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Confirmar con código")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.confirmVerification(email) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                Text("Ya confirmé con el link")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.resendVerification(email) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && uiState.resendCooldownSeconds == 0
            ) {
                Text(
                    if (uiState.resendCooldownSeconds > 0) {
                        "Reenviar en ${uiState.resendCooldownSeconds}s"
                    } else {
                        "Reenviar email"
                    }
                )
            }

            uiState.successMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = msg, color = MaterialTheme.colorScheme.primary)
            }
            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
