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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.auth.LegalDocumentConfig
import com.comunidapp.app.domain.auth.findActivity
import com.comunidapp.app.ui.components.BrandLogo
import com.comunidapp.app.ui.components.PasswordTextField
import com.comunidapp.app.ui.components.leo.AuthMethodDivider
import com.comunidapp.app.ui.components.leo.ContinueWithGoogleButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTextField
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.v2.v2KeepVisibleOnFocus
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoBody
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.UrgentRed
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.viewmodel.RegisterViewModel
import com.comunidapp.app.viewmodel.UsernameAvailabilityUi

@Composable
fun RegisterScreen(
    onRegisterSuccess: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onGoogleAuthenticated: () -> Unit = {},
    viewModel: RegisterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.registeredEmail) {
        uiState.registeredEmail?.let { email -> onRegisterSuccess(email) }
    }
    LaunchedEffect(uiState.googleAuthenticated) {
        if (uiState.googleAuthenticated) onGoogleAuthenticated()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.onHostPaused()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onHostResumed()
    }

    VisualDirectionPilot {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(
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
                .padding(horizontal = LeoDimens.SpaceXl, vertical = LeoDimens.SpaceXl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandLogo(widthFraction = 0.65f, height = 100.dp)
            Spacer(modifier = Modifier.height(LeoDimens.Space20))
            ContinueWithGoogleButton(
                onClick = {
                    val activity = context.findActivity()
                    if (activity != null) viewModel.signInWithGoogle(activity)
                },
                enabled = !uiState.isLoading && !uiState.googleAuthenticated
            )
            Spacer(modifier = Modifier.height(LeoDimens.SpaceMd))
            AuthMethodDivider()
            Spacer(modifier = Modifier.height(LeoDimens.SpaceMd))
            LeoTextField(
                value = uiState.firstName,
                onValueChange = viewModel::onFirstNameChange,
                label = "Nombre",
                required = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .v2KeepVisibleOnFocus(),
                enabled = !uiState.isLoading,
                isError = uiState.fieldErrors.containsKey("name"),
                supportingText = uiState.fieldErrors["name"]
            )
            Spacer(modifier = Modifier.height(LeoDimens.SpaceCompact))
            LeoTextField(
                value = uiState.lastName,
                onValueChange = viewModel::onLastNameChange,
                label = "Apellido",
                required = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .v2KeepVisibleOnFocus(),
                enabled = !uiState.isLoading,
                isError = uiState.fieldErrors.containsKey("name")
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@",
                    style = LeoCardTitle,
                    color = BrandText,
                    modifier = Modifier.padding(end = 4.dp)
                )
                LeoTextField(
                    value = uiState.username.removePrefix("@"),
                    onValueChange = { viewModel.onUsernameChange(it.removePrefix("@")) },
                    label = "Nombre de usuario",
                    required = true,
                    supportingText = uiState.fieldErrors["username"] ?: when (uiState.usernameAvailability) {
                        UsernameAvailabilityUi.IDLE ->
                            "Será tu identificador público en LeoVer. Ej: veroobregon"
                        UsernameAvailabilityUi.CHECKING -> "Comprobando disponibilidad…"
                        UsernameAvailabilityUi.AVAILABLE -> "Nombre disponible."
                        UsernameAvailabilityUi.TAKEN -> "Este nombre ya está en uso."
                        UsernameAvailabilityUi.RESERVED -> "Este nombre está reservado."
                        UsernameAvailabilityUi.INVALID ->
                            uiState.fieldErrors["username"] ?: "Nombre inválido."
                        UsernameAvailabilityUi.ERROR ->
                            "No pudimos comprobar la disponibilidad. Intentá nuevamente."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .v2KeepVisibleOnFocus(),
                    enabled = !uiState.isLoading,
                    isError = uiState.fieldErrors.containsKey("username") ||
                        uiState.usernameAvailability == UsernameAvailabilityUi.TAKEN ||
                        uiState.usernameAvailability == UsernameAvailabilityUi.RESERVED ||
                        uiState.usernameAvailability == UsernameAvailabilityUi.INVALID
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            com.comunidapp.app.ui.components.DatePickerField(
                label = "Fecha de nacimiento",
                required = true,
                isoDate = uiState.birthDate,
                onDateSelected = viewModel::onBirthDateChange,
                enabled = !uiState.isLoading,
                historicalOnly = true
            )
            uiState.fieldErrors["birthDate"]?.let {
                Text(text = it, color = UrgentRed, style = LeoCaption)
            }
            Spacer(modifier = Modifier.height(LeoDimens.SpaceCompact))
            LeoTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = "Correo",
                required = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .v2KeepVisibleOnFocus(),
                enabled = !uiState.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = uiState.fieldErrors.containsKey("email"),
                supportingText = uiState.fieldErrors["email"]
            )
            Spacer(modifier = Modifier.height(12.dp))
            PasswordTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Contraseña",
                required = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            PasswordTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = "Confirmar contraseña",
                required = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mínimo ${com.comunidapp.app.domain.auth.validation.AuthValidators.MIN_PASSWORD_LENGTH} caracteres.",
                style = LeoCaption,
                color = BrandTextSecondary,
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
                Text(text = "Acepto los ", style = LeoBody, color = BrandText)
                Text(
                    text = "Términos${LegalDocumentConfig.terms.draftLabel?.let { " ($it)" } ?: ""}",
                    style = LeoBody,
                    color = leoVisual().primary,
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
                Text(text = "Acepto la ", style = LeoBody, color = BrandText)
                Text(
                    text = "Privacidad${LegalDocumentConfig.privacy.draftLabel?.let { " ($it)" } ?: ""}",
                    style = LeoBody,
                    color = leoVisual().primary,
                    modifier = Modifier.clickable(onClick = onNavigateToPrivacy)
                )
            }

            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                uiState.errorTitle?.let { title ->
                    Text(
                        text = title,
                        style = LeoCardTitle,
                        color = UrgentRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(text = error, color = UrgentRed, style = LeoCaption)
            }
            if (uiState.offerResendConfirmation) {
                TextButton(onClick = viewModel::resendConfirmation, enabled = !uiState.isLoading) {
                    Text("Reenviar código")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = leoVisual().primary,
                    strokeWidth = 2.dp
                )
            } else {
                LeoPrimaryButton(
                    text = "Crear cuenta",
                    onClick = viewModel::register,
                    enabled = uiState.canSubmit
                )
            }

            TextButton(onClick = onNavigateBack) {
                Text(
                    if (uiState.emailAlreadyRegistered) {
                        "Volver a iniciar sesión"
                    } else {
                        "Ya tengo cuenta"
                    }
                )
            }
        }
    }
    }
}
