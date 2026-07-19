package com.comunidapp.app.ui.screens.organization

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.viewmodel.EditOrganizationViewModel
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.ui.files.FileUploadProgressSection
import com.comunidapp.app.ui.files.PdfOrImageMimeTypes
import com.comunidapp.app.ui.files.rememberPdfOrImageDocumentPicker
import kotlinx.coroutines.launch

@Composable
fun EditOrganizationScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: EditOrganizationViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val uploadState by DataProvider.fileUploadCoordinator.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.onLogoSelected(uri) }
    val documentPicker = rememberPdfOrImageDocumentPicker { uri ->
        uri?.let { viewModel.attachDocument(it) }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.clearSaveSuccess()
            onSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Editar organización",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            !uiState.canEdit -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Acceso denegado",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uiState.publicName,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Verificación: ${uiState.verificationStatus.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                PetImage(
                    imageUrl = uiState.pendingLogoUri?.toString() ?: uiState.logoUrl,
                    modifier = Modifier.size(96.dp),
                    cornerRadius = 16.dp,
                    contentDescription = "Logo"
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !uiState.isSaving,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cambiar logo")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { documentPicker.launch(PdfOrImageMimeTypes) },
                    enabled = !uiState.isSaving,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Adjuntar documento")
                }
                FileUploadProgressSection(
                    state = uploadState,
                    onCancel = {
                        uploadState.sessionId?.let { id ->
                            scope.launch { DataProvider.fileUploadCoordinator.cancel(id) }
                        }
                    },
                    onRetry = { scope.launch { DataProvider.fileUploadCoordinator.retry() } }
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    enabled = !uiState.isSaving
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.contactEmail,
                    onValueChange = viewModel::onContactEmailChange,
                    label = { Text("Email institucional") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.isSaving
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mostrar email públicamente", modifier = Modifier.weight(1f))
                    Switch(
                        checked = uiState.showEmail,
                        onCheckedChange = viewModel::onShowEmailChange,
                        enabled = !uiState.isSaving
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.contactPhone,
                    onValueChange = viewModel::onContactPhoneChange,
                    label = { Text("Teléfono institucional") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.isSaving
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mostrar teléfono públicamente", modifier = Modifier.weight(1f))
                    Switch(
                        checked = uiState.showPhone,
                        onCheckedChange = viewModel::onShowPhoneChange,
                        enabled = !uiState.isSaving
                    )
                }
                uiState.errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = msg, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = viewModel::save,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Guardar")
                    }
                }
                if (uiState.canRequestVerification) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = viewModel::requestVerification,
                        enabled = !uiState.isRequestingVerification && !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            if (uiState.isRequestingVerification) {
                                "Solicitando…"
                            } else {
                                "Solicitar verificación"
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
