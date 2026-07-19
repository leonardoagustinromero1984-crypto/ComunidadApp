package com.comunidapp.app.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.user.ProfileVisibility
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.viewmodel.OnboardingStep
import com.comunidapp.app.viewmodel.ProfileOnboardingUiState
import com.comunidapp.app.viewmodel.ProfileOnboardingViewModel

@Composable
fun ProfileOnboardingScreen(
    onComplete: () -> Unit,
    viewModel: ProfileOnboardingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            viewModel.clearSuccess()
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Configurá tu perfil",
                showBackButton = uiState.step != OnboardingStep.IDENTITY,
                onBackClick = viewModel::goBack
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OnboardingProgress(step = uiState.step)
                Spacer(modifier = Modifier.height(16.dp))

                when (uiState.step) {
                    OnboardingStep.IDENTITY -> IdentityStep(uiState, viewModel)
                    OnboardingStep.LOCATION_PRIVACY -> LocationPrivacyStep(uiState, viewModel)
                    OnboardingStep.AVATAR_SUMMARY -> AvatarSummaryStep(uiState, viewModel)
                }

                uiState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = viewModel::goNext,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSubmitting
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (uiState.step == OnboardingStep.AVATAR_SUMMARY) {
                                "Completar perfil"
                            } else {
                                "Continuar"
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun OnboardingProgress(step: OnboardingStep) {
    val stepIndex = when (step) {
        OnboardingStep.IDENTITY -> 1
        OnboardingStep.LOCATION_PRIVACY -> 2
        OnboardingStep.AVATAR_SUMMARY -> 3
    }
    val progress = stepIndex / 3f
    Column(
        modifier = Modifier.semantics {
            contentDescription = "Paso $stepIndex de 3"
        }
    ) {
        Text(
            text = "Paso $stepIndex de 3",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun IdentityStep(
    uiState: ProfileOnboardingUiState,
    viewModel: ProfileOnboardingViewModel
) {
    Text(
        text = "Tu identidad pública",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Tu nombre de usuario es único y te permite que otros te encuentren en LeoVer.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = uiState.displayName,
        onValueChange = viewModel::onDisplayNameChange,
        label = { Text("Nombre para mostrar") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !uiState.isSubmitting,
        isError = uiState.fieldErrors.containsKey("displayName"),
        supportingText = {
            uiState.fieldErrors["displayName"]?.let { Text(it) }
        }
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = uiState.username,
        onValueChange = viewModel::onUsernameChange,
        label = { Text("Nombre de usuario") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !uiState.isSubmitting,
        isError = uiState.fieldErrors.containsKey("username"),
        supportingText = {
            when {
                uiState.fieldErrors["username"] != null -> Text(uiState.fieldErrors["username"]!!)
                uiState.checkingUsername -> Text("Verificando disponibilidad…")
                uiState.usernameAvailable == true -> Text("Usuario disponible")
            }
        },
        trailingIcon = {
            if (uiState.checkingUsername) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    )
}

@Composable
private fun LocationPrivacyStep(
    uiState: ProfileOnboardingUiState,
    viewModel: ProfileOnboardingViewModel
) {
    Text(
        text = "Ubicación y privacidad",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Podés ajustar qué información compartís con la comunidad.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = uiState.city,
        onValueChange = viewModel::onCityChange,
        label = { Text("Ciudad (opcional)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !uiState.isSubmitting
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = uiState.province,
        onValueChange = viewModel::onProvinceChange,
        label = { Text("Provincia (opcional)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !uiState.isSubmitting
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = uiState.countryCode,
        onValueChange = viewModel::onCountryCodeChange,
        label = { Text("País (código ISO, ej. AR)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !uiState.isSubmitting,
        isError = uiState.fieldErrors.containsKey("countryCode"),
        supportingText = {
            uiState.fieldErrors["countryCode"]?.let { Text(it) }
        }
    )
    Spacer(modifier = Modifier.height(20.dp))
    Text("Visibilidad del perfil", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(8.dp))
    ProfileVisibility.entries.forEach { visibility ->
        VisibilityOption(
            visibility = visibility,
            selected = uiState.profileVisibility == visibility,
            onSelect = { viewModel.onProfileVisibilityChange(visibility) },
            enabled = !uiState.isSubmitting
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    PrivacyToggle(
        title = "Mostrar ubicación",
        hint = "Si está activo, otros podrán ver tu ciudad y provincia.",
        checked = uiState.showLocation,
        onCheckedChange = viewModel::onShowLocationChange,
        enabled = !uiState.isSubmitting
    )
    Spacer(modifier = Modifier.height(12.dp))
    PrivacyToggle(
        title = "Mostrar teléfono",
        hint = "Solo si decidís compartirlo en tu perfil.",
        checked = uiState.showPhone,
        onCheckedChange = viewModel::onShowPhoneChange,
        enabled = !uiState.isSubmitting
    )
    Spacer(modifier = Modifier.height(12.dp))
    PrivacyToggle(
        title = "Permitir solicitudes de amistad",
        hint = "Otros usuarios podrán enviarte solicitudes.",
        checked = uiState.allowFriendRequests,
        onCheckedChange = viewModel::onAllowFriendRequestsChange,
        enabled = !uiState.isSubmitting
    )
}

@Composable
private fun VisibilityOption(
    visibility: ProfileVisibility,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean
) {
    val label = when (visibility) {
        ProfileVisibility.PUBLIC -> "Público"
        ProfileVisibility.FRIENDS -> "Solo amigos"
        ProfileVisibility.PRIVATE -> "Privado"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onSelect
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PrivacyToggle(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun AvatarSummaryStep(
    uiState: ProfileOnboardingUiState,
    viewModel: ProfileOnboardingViewModel
) {
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.onImageSelected(uri) }

    Text(
        text = "Foto y resumen",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "La foto y la biografía son opcionales. Podés completar el perfil sin ellas.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PetImage(
            imageUrl = uiState.pendingImageUri?.toString(),
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape),
            cornerRadius = 56.dp,
            contentDescription = uiState.displayName
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            enabled = !uiState.isSubmitting
        ) {
            Text("Elegir foto (opcional)")
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
    OutlinedTextField(
        value = uiState.bio,
        onValueChange = viewModel::onBioChange,
        label = { Text("Biografía (opcional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        enabled = !uiState.isSubmitting
    )
    Spacer(modifier = Modifier.height(20.dp))
    Text("Resumen", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(8.dp))
    SummaryRow("Nombre", uiState.displayName)
    SummaryRow("Usuario", "@${uiState.username}")
    val location = listOf(uiState.city, uiState.province, uiState.countryCode)
        .filter { it.isNotBlank() }
        .joinToString(", ")
    if (location.isNotBlank()) {
        SummaryRow("Ubicación", location)
    }
    SummaryRow(
        "Visibilidad",
        when (uiState.profileVisibility) {
            ProfileVisibility.PUBLIC -> "Público"
            ProfileVisibility.FRIENDS -> "Solo amigos"
            ProfileVisibility.PRIVATE -> "Privado"
        }
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
