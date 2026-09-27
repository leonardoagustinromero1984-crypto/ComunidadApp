package com.comunidapp.app.ui.screens.onboarding

import com.comunidapp.app.ui.components.DatePickerField
import com.comunidapp.app.ui.media.rememberLeoVerPhotoSourcePicker
import com.comunidapp.app.ui.theme.BrandBackground

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTextField
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.v2.V2FormImagePreview
import com.comunidapp.app.ui.components.v2.V2LocationCityProvincePicker
import com.comunidapp.app.ui.media.LeoVerAvatarCropKind
import com.comunidapp.app.ui.media.rememberLeoVerAvatarCropLauncher
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoBody
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoSectionTitle
import com.comunidapp.app.ui.theme.UrgentRed
import com.comunidapp.app.viewmodel.OnboardingStep
import com.comunidapp.app.viewmodel.ProfileOnboardingUiState
import com.comunidapp.app.viewmodel.ProfileOnboardingViewModel

@Composable
fun ProfileOnboardingScreen(
    onComplete: () -> Unit,
    sessionUserId: String,
    viewModel: ProfileOnboardingViewModel = viewModel(key = sessionUserId)
) {
    val uiState by viewModel.uiState.collectAsState()
    val cropPhoto = rememberLeoVerAvatarCropLauncher(
        kind = LeoVerAvatarCropKind.PERSON,
        onCropped = viewModel::onCroppedPhoto,
        onCancel = viewModel::cancelPhotoEditor,
        onError = viewModel::onPhotoCropFailed
    )
    val pickPhoto = rememberLeoVerPhotoSourcePicker(
        sheetTitle = "Agregar foto",
        onSourceSelected = { uri -> cropPhoto(uri) }
    )

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            viewModel.clearSuccess()
            onComplete()
        }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Configurá tu perfil",
                showBackButton = when (uiState.step) {
                    OnboardingStep.IDENTITY -> false
                    OnboardingStep.LOCATION_PRIVACY -> uiState.identityRequired
                    OnboardingStep.AVATAR_SUMMARY -> true
                },
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
                    .padding(horizontal = LeoDimens.SpaceXl)
                    .verticalScroll(rememberScrollState())
            ) {
                OnboardingProgress(step = uiState.step)
                Spacer(modifier = Modifier.height(16.dp))

                when (uiState.step) {
                    OnboardingStep.IDENTITY -> IdentityStep(uiState, viewModel)
                    OnboardingStep.LOCATION_PRIVACY -> LocationPrivacyStep(uiState, viewModel)
                    OnboardingStep.AVATAR_SUMMARY -> AvatarSummaryStep(uiState, viewModel, pickPhoto)
                }

                uiState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = UrgentRed,
                        style = LeoCaption
                    )
                    if (uiState.photoUploadFailed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.pendingImageUri != null) {
                            LeoOutlinedButton(
                                text = "Reintentar foto",
                                onClick = viewModel::retryPhotoUpload,
                                enabled = !uiState.isSubmitting
                            )
                        }
                        TextButton(
                            onClick = pickPhoto,
                            enabled = !uiState.isSubmitting
                        ) {
                            Text("Cambiar foto")
                        }
                        TextButton(
                            onClick = viewModel::skipPhotoAndContinue,
                            enabled = !uiState.isSubmitting
                        ) {
                            Text("Continuar sin foto")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = BrandOrange,
                        strokeWidth = 2.dp
                    )
                } else {
                    LeoPrimaryButton(
                        text = if (uiState.step == OnboardingStep.AVATAR_SUMMARY) {
                            "Completar perfil"
                        } else {
                            "Continuar"
                        },
                        onClick = viewModel::goNext,
                        enabled = !uiState.photoUploadFailed && !uiState.isProcessingPhoto
                    )
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
            style = LeoCaption,
            color = BrandTextSecondary
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
        style = LeoSectionTitle,
        color = BrandText
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = if (uiState.usernameLocked) {
            "Tu usuario ya está definido. Completá solo lo que falta."
        } else {
            "Tu nombre de usuario es único y te permite que otros te encuentren en LeoVer."
        },
        style = LeoCaption,
        color = BrandTextSecondary
    )
    Spacer(modifier = Modifier.height(16.dp))
    if (!uiState.displayNamePresent) {
        LeoTextField(
            value = uiState.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = "Nombre para mostrar",
            required = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSubmitting,
            isError = uiState.fieldErrors.containsKey("displayName"),
            supportingText = uiState.fieldErrors["displayName"]
        )
        Spacer(modifier = Modifier.height(12.dp))
    } else {
        SummaryRow("Nombre para mostrar", uiState.displayName)
        Spacer(modifier = Modifier.height(12.dp))
    }
    if (uiState.usernameLocked) {
        SummaryRow("Usuario", "@${uiState.username}")
    } else {
        LeoTextField(
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            label = "Nombre de usuario",
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSubmitting,
            isError = uiState.fieldErrors.containsKey("username"),
            supportingText = when {
                uiState.fieldErrors["username"] != null -> uiState.fieldErrors["username"]
                uiState.checkingUsername -> "Verificando disponibilidad…"
                uiState.usernameAvailable == true -> "Usuario disponible"
                else -> null
            },
            trailingIcon = {
                if (uiState.checkingUsername) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BrandOrange,
                        strokeWidth = 2.dp
                    )
                }
            }
        )
    }
    if (uiState.needsBirthDate) {
        Spacer(modifier = Modifier.height(12.dp))
        DatePickerField(
            label = "Fecha de nacimiento",
            isoDate = uiState.birthDate,
            onDateSelected = viewModel::onBirthDateChange,
            enabled = !uiState.isSubmitting,
            historicalOnly = true
        )
        uiState.fieldErrors["birthDate"]?.let {
            Text(
                text = it,
                color = UrgentRed,
                style = LeoCaption
            )
        }
    }
}

@Composable
private fun LocationPrivacyStep(
    uiState: ProfileOnboardingUiState,
    viewModel: ProfileOnboardingViewModel
) {
    Text(
        text = "Ubicación y privacidad",
        style = LeoSectionTitle,
        color = BrandText
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Podés ajustar qué información compartís con la comunidad.",
        style = LeoCaption,
        color = BrandTextSecondary
    )
    Spacer(modifier = Modifier.height(16.dp))
    V2LocationCityProvincePicker(
        city = uiState.city,
        province = uiState.province,
        onCityChange = viewModel::onCityChange,
        onProvinceChange = viewModel::onProvinceChange,
        onLocalityIdChange = viewModel::onHomeLocalityIdChange,
        initialLocalityId = uiState.homeLocalityId,
        enabled = !uiState.isSubmitting,
        includeZone = false
    )
    uiState.fieldErrors["province"]?.let { message ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, color = UrgentRed, style = LeoCaption)
    }
    Spacer(modifier = Modifier.height(20.dp))
    Text("Visibilidad del perfil social", style = LeoCardTitle, color = BrandText)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = if (uiState.profileVisibility == ProfileVisibility.PRIVATE) {
            "Solo los seguidores que apruebes pueden ver tu perfil social privado."
        } else {
            "Cualquier persona puede ver tu perfil social y el contenido que publiques como público."
        },
        style = LeoCaption,
        color = BrandTextSecondary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Esta configuración no modifica la privacidad de VitaCora ni la información de tus mascotas.",
        style = LeoCaption,
        color = BrandTextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    com.comunidapp.app.domain.user.SocialProfileVisibility.selectable.forEach { visibility ->
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
}

@Composable
private fun VisibilityOption(
    visibility: ProfileVisibility,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean
) {
    val label = com.comunidapp.app.domain.user.SocialProfileVisibility.label(visibility)
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
        Text(text = label, style = LeoBody, color = BrandText)
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
            Text(text = title, style = LeoCardTitle, color = BrandText)
            Text(
                text = hint,
                style = LeoCaption,
                color = BrandTextSecondary
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
    viewModel: ProfileOnboardingViewModel,
    onPickPhoto: () -> Unit
) {

    Text(
        text = "Foto y resumen",
        style = LeoSectionTitle,
        color = BrandText
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "La foto y la biografía son opcionales. Podés completar el perfil sin ellas.",
        style = LeoCaption,
        color = BrandTextSecondary
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
        if (uiState.pendingImageUri != null) {
            Spacer(modifier = Modifier.height(12.dp))
            V2FormImagePreview(
                imageUrl = uiState.pendingImageUri.toString(),
                contentDescription = "Vista previa de la foto"
            )
        }
        if (uiState.isProcessingPhoto) {
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        LeoOutlinedButton(
            text = "Elegir foto (opcional)",
            onClick = onPickPhoto,
            enabled = !uiState.isSubmitting && !uiState.isProcessingPhoto
        )
        if (uiState.pendingImageUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { viewModel.onImageSelected(null); viewModel.cancelPhotoEditor() },
                enabled = !uiState.isSubmitting
            ) {
                Text("Quitar foto")
            }
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
    LeoTextField(
        value = uiState.bio,
        onValueChange = viewModel::onBioChange,
        label = "Biografía (opcional)",
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        minLines = 3,
        enabled = !uiState.isSubmitting
    )
    Spacer(modifier = Modifier.height(20.dp))
    Text("Resumen", style = LeoCardTitle, color = BrandText)
    Spacer(modifier = Modifier.height(8.dp))
    SummaryRow("Nombre", uiState.displayName)
    SummaryRow("Usuario", "@${uiState.username}")
            val location = listOf(uiState.city, uiState.province)
                .filter { it.isNotBlank() }
                .joinToString(", ")
    if (location.isNotBlank()) {
        SummaryRow("Ubicación", location)
    }
    SummaryRow(
        "Visibilidad",
        com.comunidapp.app.domain.user.SocialProfileVisibility.label(uiState.profileVisibility)
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
            style = LeoCaption,
            color = BrandTextSecondary
        )
        Text(
            text = value,
            style = LeoBody,
            color = BrandText
        )
    }
}
