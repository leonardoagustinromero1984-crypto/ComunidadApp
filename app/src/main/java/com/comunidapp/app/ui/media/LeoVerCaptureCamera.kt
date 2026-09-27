package com.comunidapp.app.ui.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.theme.LeoDimens
import java.io.File

enum class LeoVerCaptureMode { PHOTO, VIDEO, BOTH }

@Composable
fun LeoVerCaptureCamera(
    mode: LeoVerCaptureMode,
    onCaptured: (Uri, Boolean) -> Unit,
    onCancel: () -> Unit,
    purposeLabel: String = "LeoVer"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lenses = remember { availableLensFacings(context) }
    var lensFacing by remember {
        mutableStateOf(lenses.firstOrNull() ?: CameraSelector.LENS_FACING_BACK)
    }
    var recording by remember { mutableStateOf(false) }
    var permissionOk by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        permissionOk = granted[Manifest.permission.CAMERA] == true
        if (!permissionOk) {
            errorMessage = "LeoVer necesita la cámara para continuar."
        }
    }
    DisposableEffect(Unit) {
        if (!permissionOk) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
        onDispose { }
    }
    if (!permissionOk) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(LeoDimens.SpaceMd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
            ) {
                Text(
                    errorMessage ?: "$purposeLabel necesita la cámara.",
                    color = Color.White
                )
                LeoPrimaryButton(
                    text = "Permitir cámara",
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        )
                    }
                )
                LeoOutlinedButton(text = "Cancelar", onClick = onCancel)
            }
        }
        return
    }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember(lensFacing) { ImageCapture.Builder().build() }
    val videoCapture = remember(lensFacing) {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        VideoCapture.withOutput(recorder)
    }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var captureAlive by remember { mutableStateOf(true) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(Unit) {
        captureAlive = true
        onDispose { captureAlive = false }
    }

    DisposableEffect(lensFacing, imageCapture, videoCapture) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = runCatching { providerFuture.get() }.getOrNull() ?: return@Runnable
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val bound = runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, videoCapture)
            }.isSuccess
            if (!bound) {
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                }.onFailure {
                    errorMessage = "No pudimos abrir esta cámara."
                }
            }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            if (providerFuture.isDone) {
                runCatching { providerFuture.get().unbindAll() }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(LeoDimens.SpaceMd),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCancel) { Text("Volver", color = Color.White) }
            if (lenses.size > 1) {
                TextButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    enabled = !recording
                ) { Text("Girar cámara", color = Color.White) }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            if (recording) {
                Text("Grabando…", color = Color.White)
            }
            errorMessage?.let { Text(it, color = Color.White) }
            Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)) {
                if (mode == LeoVerCaptureMode.PHOTO || mode == LeoVerCaptureMode.BOTH) {
                    Box(Modifier.weight(1f)) {
                        LeoPrimaryButton(
                            text = "Foto",
                            onClick = {
                                val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                                imageCapture.takePicture(
                                    options,
                                    mainExecutor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            if (captureAlive) onCaptured(fileProviderUri(context, file), false)
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            errorMessage = "No pudimos tomar la foto."
                                        }
                                    }
                                )
                            },
                            enabled = !recording
                        )
                    }
                }
                if (mode == LeoVerCaptureMode.VIDEO || mode == LeoVerCaptureMode.BOTH) {
                    Box(Modifier.weight(1f)) {
                        LeoPrimaryButton(
                            text = if (recording) "Detener" else "Video",
                            onClick = {
                                if (recording) {
                                    activeRecording?.stop()
                                    activeRecording = null
                                    recording = false
                                } else {
                                    val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.mp4")
                                    val pending = videoCapture.output.prepareRecording(
                                        context,
                                        FileOutputOptions.Builder(file).build()
                                    )
                                    val hasAudio = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val onFinalize: (VideoRecordEvent) -> Unit = { event ->
                                        if (event is VideoRecordEvent.Finalize) {
                                            recording = false
                                            activeRecording = null
                                            if (!event.hasError() && captureAlive) {
                                                onCaptured(fileProviderUri(context, file), true)
                                            } else if (event.hasError()) {
                                                errorMessage = "No pudimos grabar el video."
                                            }
                                        }
                                    }
                                    val rec = if (hasAudio) {
                                        pending.withAudioEnabled().start(mainExecutor, onFinalize)
                                    } else {
                                        pending.start(mainExecutor, onFinalize)
                                    }
                                    activeRecording = rec
                                    recording = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun availableLensFacings(context: Context): List<Int> {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return listOf(
        CameraSelector.LENS_FACING_BACK
    )
    val found = linkedSetOf<Int>()
    manager.cameraIdList.forEach { id ->
        val facing = manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.LENS_FACING)
        if (facing == CameraCharacteristics.LENS_FACING_BACK) found += CameraSelector.LENS_FACING_BACK
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) found += CameraSelector.LENS_FACING_FRONT
    }
    return found.toList().ifEmpty { listOf(CameraSelector.LENS_FACING_BACK) }
}

private fun fileProviderUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
