package com.comunidapp.shared.vertical

import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.comunidapp.shared.auth.AuthRepository
import com.comunidapp.shared.auth.LoginUiState
import com.comunidapp.shared.auth.LoginViewModelShared
import com.comunidapp.shared.auth.isAppleSignInAvailable
import com.comunidapp.shared.session.SessionDataMode

@Composable
internal fun SharedLoginScreen(
    authRepository: AuthRepository,
    sessionHint: String?,
    appleSignInController: com.comunidapp.shared.auth.AppleSignInController? = null
) {
    val vm = remember(authRepository, appleSignInController) {
        LoginViewModelShared(
            authRepository = authRepository,
            appleSignInController = appleSignInController?.takeIf { isAppleSignInAvailable() }
        )
    }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val ui by vm.uiState.collectAsState()

    // Password only in Compose local state — never in ViewModel StateFlow.
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(ui) {
        if (ui is LoginUiState.Authenticated) {
            password = ""
        }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("LeoVer", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Iniciá sesión",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Sesión: ${authRepository.dataMode.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (authRepository.dataMode == SessionDataMode.REAL_REMOTE && sessionHint != null) {
                Text(
                    sessionHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    vm.resetError()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = ui !is LoginUiState.Loading
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    vm.resetError()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = ui !is LoginUiState.Loading
            )
            when (val s = ui) {
                is LoginUiState.Error -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                LoginUiState.Loading -> CircularProgressIndicator()
                else -> Unit
            }
            Button(
                onClick = { vm.signIn(email, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = ui !is LoginUiState.Loading && email.isNotBlank() && password.isNotEmpty()
            ) {
                Text("Iniciar sesión")
            }
            if (vm.appleSignInVisible) {
                OutlinedButton(
                    onClick = { vm.signInWithApple() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ui !is LoginUiState.Loading
                ) {
                    Text("Continuar con Apple")
                }
            }
        }
    }
}
