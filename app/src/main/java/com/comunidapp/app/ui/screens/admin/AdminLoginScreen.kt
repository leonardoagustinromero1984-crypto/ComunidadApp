package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.PasswordTextField
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.viewmodel.AdminLoginViewModel

@Composable
fun AdminLoginScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminLoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    VisualDirectionPilot {
        val visual = leoVisual()
        Scaffold(
            containerColor = visual.background,
            topBar = {
                LeoTopAppBar(
                    title = "Acceso administrativo",
                    showBackButton = true,
                    onBackClick = onNavigateBack
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = LeoDimens.SpaceLg)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(LeoDimens.SpaceSection))
                Text(
                    "Gestión de la plataforma LeoVer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(LeoDimens.SpaceLg))
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Usuario") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(Modifier.height(LeoDimens.SpaceSm))
                PasswordTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Contraseña"
                )
                uiState.error?.let { error ->
                    Spacer(Modifier.height(LeoDimens.SpaceSm))
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(LeoDimens.SpaceSection))
                if (uiState.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = visual.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    LeoPrimaryButton(
                        text = "Ingresar",
                        onClick = viewModel::login
                    )
                }
            }
        }
    }
}
