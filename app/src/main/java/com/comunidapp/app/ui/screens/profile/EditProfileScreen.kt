package com.comunidapp.app.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.R
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.data.model.displayOf
import com.comunidapp.app.data.model.restoreSelection
import com.comunidapp.app.data.model.visibleLabel
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.v2.V2FormImagePreview
import com.comunidapp.app.ui.components.v2.V2LocationPicker
import com.comunidapp.app.ui.components.v2.v2KeepVisibleOnFocus
import com.comunidapp.app.ui.media.LeoVerAvatarCropKind
import com.comunidapp.app.ui.media.rememberLeoVerAvatarCropLauncher
import com.comunidapp.app.viewmodel.EditProfileViewModel

@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: EditProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val cropPhoto = rememberLeoVerAvatarCropLauncher(
        kind = LeoVerAvatarCropKind.PERSON,
        onCropped = viewModel::onCroppedPhoto,
        onCancel = viewModel::cancelPhotoEditor,
        onError = viewModel::onPhotoCropFailed
    )
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(cropPhoto) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.clearSaveSuccess()
            onSaveSuccess()
        }
    }

    VisualDirectionPilot {
    Scaffold(
        containerColor = leoVisual().background,
        topBar = {
            LeoTopAppBar(
                title = stringResource(R.string.edit_profile),
                showBackButton = true,
                onBackClick = onNavigateBack
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
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                PetImage(
                    imageUrl = uiState.pendingImageUri?.toString() ?: uiState.profileImageUrl,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape),
                    cornerRadius = 56.dp,
                    contentDescription = uiState.name
                )
                if (uiState.pendingImageUri != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    V2FormImagePreview(
                        imageUrl = uiState.pendingImageUri.toString(),
                        contentDescription = "Vista previa de la foto"
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !uiState.isSaving && !uiState.isProcessingPhoto
                ) {
                    Text(
                        if (uiState.pendingImageUri != null || !uiState.profileImageUrl.isNullOrBlank()) {
                            stringResource(R.string.change_photo)
                        } else {
                            "Agregar foto"
                        }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.profile_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .v2KeepVisibleOnFocus(),
                    singleLine = true,
                    enabled = !uiState.isSaving
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.bio,
                    onValueChange = viewModel::onBioChange,
                    label = { Text(stringResource(R.string.profile_bio)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .v2KeepVisibleOnFocus(),
                    minLines = 3,
                    enabled = !uiState.isSaving
                )
                Spacer(modifier = Modifier.height(12.dp))
                val catalog = DataProvider.locationCatalogRepository
                val nodes by catalog.nodes.collectAsState()
                var locationSelection by remember {
                    mutableStateOf(
                        nodes.restoreSelection(
                            localityId = uiState.homeLocalityId,
                            province = uiState.province,
                            city = uiState.city,
                            label = uiState.locationText
                        )
                    )
                }
                LaunchedEffect(
                    uiState.isLoading,
                    uiState.userId,
                    uiState.homeLocalityId,
                    nodes.size
                ) {
                    if (!uiState.isLoading) {
                        locationSelection = nodes.restoreSelection(
                            localityId = uiState.homeLocalityId,
                            province = uiState.province,
                            city = uiState.city,
                            label = uiState.locationText
                        )
                    }
                }
                V2LocationPicker(
                    selection = locationSelection,
                    onSelectionChange = { next ->
                        locationSelection = next
                        val display = nodes.displayOf(next)
                        viewModel.onAdministrativeLocationChange(
                            locationText = nodes.visibleLabel(next),
                            city = display.cityName,
                            province = display.provinceName,
                            homeLocalityId = next.localityId
                        )
                    },
                    enabled = !uiState.isSaving
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = { Text(stringResource(R.string.profile_phone)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.isSaving
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.profile_private_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.profile_private_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.profilePrivate,
                        onCheckedChange = viewModel::onProfilePrivateChange,
                        enabled = !uiState.isSaving
                    )
                }

                uiState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (uiState.photoUploadFailed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.pendingImageUri != null) {
                            OutlinedButton(
                                onClick = viewModel::saveProfile,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isSaving
                            ) {
                                Text("Reintentar")
                            }
                        }
                        TextButton(
                            onClick = {
                                pickImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !uiState.isSaving
                        ) {
                            Text("Cambiar foto")
                        }
                        TextButton(
                            onClick = viewModel::skipPhotoAndContinue,
                            enabled = !uiState.isSaving
                        ) {
                            Text("Continuar sin foto")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = viewModel::saveProfile,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving && !uiState.photoUploadFailed && !uiState.isProcessingPhoto
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.save_profile))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    }
}
