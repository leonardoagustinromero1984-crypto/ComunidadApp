package com.comunidapp.app.ui.files

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
fun rememberImagePicker(
    onSelected: (Uri?) -> Unit
): ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = onSelected
    )

fun imageOnlyPickerRequest(): PickVisualMediaRequest =
    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

@Composable
fun rememberPdfOrImageDocumentPicker(
    onSelected: (Uri?) -> Unit
): ManagedActivityResultLauncher<Array<String>, Uri?> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = onSelected
    )

val PdfOrImageMimeTypes: Array<String>
    get() = arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")

@Composable
fun rememberMultiImagePicker(
    maxItems: Int = 8,
    onSelected: (List<Uri>) -> Unit
): ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems),
        onResult = onSelected
    )
