package com.comunidapp.app.ui.screens.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.R
import com.comunidapp.app.domain.auth.AuthDeepLinkKind
import com.comunidapp.app.domain.auth.AuthLinkNoticeStore
import com.comunidapp.app.domain.auth.findActivity
import com.comunidapp.app.ui.components.BrandLogo
import com.comunidapp.app.ui.components.PasswordTextField
import com.comunidapp.app.ui.components.leo.AuthMethodDivider
import com.comunidapp.app.ui.components.leo.ContinueWithGoogleButton
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTextField
import com.comunidapp.app.ui.components.v2.v2KeepVisibleOnFocus
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoSecondary
import com.comunidapp.app.ui.theme.UrgentRed
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToEmailVerification: (String) -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val linkNotice by AuthLinkNoticeStore.notice.collectAsState()
    val focusManager = LocalFocusManager.current
    val linkError = linkNotice
        ?.takeIf { it.kind == AuthDeepLinkKind.LinkError }
        ?.userMessage

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onLoginSuccess()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.onHostPaused()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onHostResumed()
    }

    LaunchedEffect(uiState.needsEmailVerification) {
        uiState.needsEmailVerification?.let { email ->
            onNavigateToEmailVerification(email)
            viewModel.clearEmailVerificationRedirect()
        }
    }

    VisualDirectionPilot {
    Scaffold(containerColor = leoVisual().background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LeoDimens.SpaceLg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandLogo(widthFraction = 0.82f, height = 150.dp)
            Spacer(modifier = Modifier.height(LeoDimens.SpaceSm))
            Text(
                text = stringResource(R.string.brand_tagline),
                style = LeoSecondary,
                color = leoVisual().textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LeoDimens.SpaceMd)
            )
            Spacer(modifier = Modifier.height(LeoDimens.SpaceLg + LeoDimens.SpaceMicro))

            ContinueWithGoogleButton(
                onClick = {
                    val activity = context.findActivity()
                    if (activity != null) viewModel.signInWithGoogle(activity)
                },
                enabled = !uiState.isBusy
            )
            Spacer(modifier = Modifier.height(LeoDimens.SpaceMd))
            AuthMethodDivider()
            Spacer(modifier = Modifier.height(LeoDimens.SpaceMd))

            LeoTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = "Correo o usuario",
                modifier = Modifier
                    .fillMaxWidth()
                    .v2KeepVisibleOnFocus(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )
            Spacer(modifier = Modifier.height(LeoDimens.SpaceCompact))
            PasswordTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Contraseña"
            )

            TextButton(
                onClick = onNavigateToForgotPassword,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("¿Olvidaste tu contraseña?")
            }

            (uiState.errorMessage ?: linkError)?.let { error ->
                Spacer(modifier = Modifier.height(LeoDimens.SpaceSm))
                Text(
                    text = error,
                    color = UrgentRed,
                    style = LeoCaption
                )
            }

            Spacer(modifier = Modifier.height(LeoDimens.SpaceSection))

            if (uiState.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = leoVisual().primary,
                    strokeWidth = 2.dp
                )
            } else {
                LeoPrimaryButton(
                    text = "Iniciar sesión",
                    onClick = viewModel::login
                )
            }
            Spacer(modifier = Modifier.height(LeoDimens.SpaceCompact))
            LeoOutlinedButton(
                text = "Crear cuenta",
                onClick = onNavigateToRegister,
                enabled = !uiState.isBusy
            )
            Spacer(modifier = Modifier.height(LeoDimens.SpaceLg))
        }
    }
    }
}
