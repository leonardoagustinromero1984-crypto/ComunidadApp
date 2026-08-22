package com.comunidapp.app.ui.screens.social

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import java.io.File
import java.util.concurrent.Executors

@Composable
fun StoryCameraScreen(
    onCaptured: (Uri, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var recording by remember { mutableStateOf(false) }
    var permissionOk by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        permissionOk = granted[Manifest.permission.CAMERA] == true
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
            ColumnButtons(
                onRequest = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    )
                },
                onCancel = onCancel
            )
        }
        return
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val recorder = remember {
        Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { previewView ->
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder()
                        .setTargetResolution(Size(1080, 1920))
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture,
                        videoCapture
                    )
                }, ContextCompat.getMainExecutor(context))
            }
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCancel) { Text("Volver", color = Color.White) }
            TextButton(onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            }) { Text("Cambiar cámara", color = Color.White) }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = {
                val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(
                    options,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onCaptured(Uri.fromFile(file), false)
                        }
                        override fun onError(exception: ImageCaptureException) = Unit
                    }
                )
            }) { Text("Foto") }
            Button(onClick = {
                if (recording) {
                    activeRecording?.stop()
                    activeRecording = null
                    recording = false
                } else {
                    val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.mp4")
                    val pending = videoCapture.output
                        .prepareRecording(context, FileOutputOptions.Builder(file).build())
                    val rec = if (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) pending.withAudioEnabled().start(executor) { event ->
                        if (event is VideoRecordEvent.Finalize && !event.hasError()) {
                            onCaptured(Uri.fromFile(file), true)
                        }
                    } else pending.start(executor) { event ->
                        if (event is VideoRecordEvent.Finalize && !event.hasError()) {
                            onCaptured(Uri.fromFile(file), true)
                        }
                    }
                    activeRecording = rec
                    recording = true
                }
            }) { Text(if (recording) "Detener" else "Video") }
        }
    }
}

@Composable
private fun ColumnButtons(onRequest: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("LeoVer necesita la cámara para grabar tu historia.", color = Color.White)
        Button(onClick = onRequest, modifier = Modifier.padding(top = 12.dp)) { Text("Permitir cámara") }
        TextButton(onClick = onCancel) { Text("Cancelar", color = Color.White) }
    }
}
