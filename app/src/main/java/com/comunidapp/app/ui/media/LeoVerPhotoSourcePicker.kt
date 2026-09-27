package com.comunidapp.app.ui.media

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeoVerPhotoSourceSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
            Text(
                "Tomar foto",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onTakePhoto()
                    }
                    .padding(vertical = 14.dp)
            )
            Text(
                "Elegir de galería",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onPickGallery()
                    }
                    .padding(vertical = 14.dp)
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar")
            }
        }
    }
}

/**
 * Bottom sheet: Tomar foto / Elegir de galería / Cancelar → [onSourceSelected] (crop pipeline).
 * Camera uses a fresh cache file + URI per capture and requests [Manifest.permission.CAMERA]
 * before [ActivityResultContracts.TakePicture] — never OAuth or gallery handlers.
 */
@Composable
fun rememberLeoVerPhotoSourcePicker(
    sheetTitle: String = "Agregar foto",
    onSourceSelected: (Uri) -> Unit
): () -> Unit {
    var showSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            onSourceSelected(uri)
        }
    }

    val requestCameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = prepareCameraUri(context) ?: return@rememberLauncherForActivityResult
            pendingCameraUri = uri
            takePicture.launch(uri)
        }
    }

    val pickGallery = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onSourceSelected) }

    fun launchCameraCapture() {
        val uri = prepareCameraUri(context) ?: return
        pendingCameraUri = uri
        takePicture.launch(uri)
    }

    fun onTakePhoto() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraCapture()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    LeoVerPhotoSourceSheet(
        visible = showSheet,
        title = sheetTitle,
        onDismiss = { showSheet = false },
        onTakePhoto = ::onTakePhoto,
        onPickGallery = {
            pickGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    )
    return remember { { showSheet = true } }
}

private fun prepareCameraUri(context: android.content.Context): Uri? {
    val file = File(context.cacheDir, "leover_camera_${System.currentTimeMillis()}.jpg")
    return runCatching {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            require(file.createNewFile()) { "CAMERA_FILE_CREATE_FAILED" }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}
