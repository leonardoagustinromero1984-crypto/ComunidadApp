package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.domain.auth.validation.AuthValidators
import com.comunidapp.app.ui.components.PasswordTextField
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.SessionViewModel
import kotlinx.coroutines.launch

@Composable
fun AdminPasswordChangeScreen(
    sessionViewModel: SessionViewModel = viewModel()
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Cambiar contraseña")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceLg)
        ) {
            Text(
                "Antes de continuar, elegí una contraseña nueva.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(LeoDimens.SpaceLg))
            PasswordTextField(
                value = current,
                onValueChange = { current = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = "Contraseña actual"
            )
            Spacer(Modifier.height(LeoDimens.SpaceSm))
            PasswordTextField(
                value = next,
                onValueChange = { next = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = "Nueva contraseña"
            )
            Spacer(Modifier.height(LeoDimens.SpaceSm))
            PasswordTextField(
                value = confirm,
                onValueChange = { confirm = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = "Confirmar"
            )
            error?.let {
                Spacer(Modifier.height(LeoDimens.SpaceSm))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(LeoDimens.SpaceSection))
            LeoPrimaryButton(
                text = if (busy) "Guardando…" else "Guardar",
                onClick = {
                    if (busy) return@LeoPrimaryButton
                    if (next != confirm) {
                        error = "Las contraseñas no coinciden."
                        return@LeoPrimaryButton
                    }
                    AuthValidators.validatePassword(next).getOrElse {
                        error = "La contraseña nueva no cumple los requisitos."
                        return@LeoPrimaryButton
                    }
                    scope.launch {
                        busy = true
                        AuthProvider.repository.changePassword(current, next)
                            .onSuccess {
                                DataProvider.adminSessionRepository.clearMustChangePassword()
                                sessionViewModel.onAdminPasswordChanged()
                            }
                            .onFailure {
                                error = "No se pudo actualizar la contraseña."
                                busy = false
                            }
                    }
                },
                enabled = !busy
            )
        }
    }
}
