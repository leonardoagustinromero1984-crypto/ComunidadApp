package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.repository.AdminMfaInvalidCodeException
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.SessionViewModel
import kotlinx.coroutines.launch

@Composable
fun AdminMfaChallengeScreen(
    sessionViewModel: SessionViewModel = viewModel()
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Verificación en dos pasos") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceLg)
        ) {
            Text(
                "Ingresá el código de 6 dígitos de tu app de autenticación.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(LeoDimens.SpaceLg))
            OutlinedTextField(
                value = code,
                onValueChange = { incoming ->
                    code = incoming.filter { it.isDigit() }.take(6)
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Código de 6 dígitos") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true
            )
            error?.let {
                Spacer(Modifier.height(LeoDimens.SpaceSm))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(LeoDimens.SpaceSection))
            LeoPrimaryButton(
                text = if (busy) "Verificando…" else "Continuar",
                onClick = {
                    if (busy) return@LeoPrimaryButton
                    scope.launch {
                        busy = true
                        sessionViewModel.verifyAdminMfaCode(code)
                            .onFailure { failure ->
                                error = if (failure is AdminMfaInvalidCodeException) {
                                    "Código incorrecto. Intentá nuevamente."
                                } else {
                                    "No se pudo verificar el código. Intentá nuevamente."
                                }
                                busy = false
                            }
                    }
                },
                enabled = !busy && code.length == 6
            )
        }
    }
}
