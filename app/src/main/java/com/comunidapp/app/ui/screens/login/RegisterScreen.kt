package com.comunidapp.app.ui.screens.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.auth.LegalDocumentConfig
import com.comunidapp.app.ui.components.BrandLogo
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PasswordTextField
import com.comunidapp.app.viewmodel.RegisterViewModel

@Composable
fun RegisterScreen(
    onRegisterSuccess: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    viewModel: RegisterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.registeredEmail) {
        uiState.registeredEmail?.let { email -> onRegisterSuccess(email) }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Crear cuenta",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandLogo(widthFraction = 0.65f, height = 100.dp)
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nombre completo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isLoading,
                isError = uiState.fieldErrors.containsKey("name"),
                supportingText = {
                    uiState.fieldErrors["name"]?.let { Text(it) }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = uiState.fieldErrors.containsKey("email"),
                supportingText = {
                    uiState.fieldErrors["email"]?.let { Text(it) }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            PasswordTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Contraseña"
            )
            Spacer(modifier = Modifier.height(12.dp))
            PasswordTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = "Confirmar contraseña"
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mínimo ${com.comunidapp.app.domain.auth.validation.AuthValidators.MIN_PASSWORD_LENGTH} caracteres.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = uiState.acceptedTerms,
                    onCheckedChange = viewModel::onAcceptedTermsChange,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.semantics { contentDescription = "Aceptar términos" }
                )
                Text(
                    text = "Acepto los ",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Términos${LegalDocumentConfig.terms.draftLabel?.let { " ($it)" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onNavigateToTerms)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = uiState.acceptedPrivacy,
                    onCheckedChange = viewModel::onAcceptedPrivacyChange,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.semantics { contentDescription = "Aceptar privacidad" }
                )
                Text(
                    text = "Acepto la ",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Privacidad${LegalDocumentConfig.privacy.draftLabel?.let { " ($it)" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onNavigateToPrivacy)
                )
            }

            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::register,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Registrarse")
                }
            }

            TextButton(onClick = onNavigateBack) {
                Text("Ya tengo cuenta")
            }
        }
    }
}
