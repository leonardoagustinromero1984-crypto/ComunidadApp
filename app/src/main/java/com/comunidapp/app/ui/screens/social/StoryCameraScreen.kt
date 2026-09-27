package com.comunidapp.app.ui.screens.social

import android.net.Uri
import androidx.compose.runtime.Composable
import com.comunidapp.app.ui.media.LeoVerCaptureCamera
import com.comunidapp.app.ui.media.LeoVerCaptureMode

@Composable
fun StoryCameraScreen(
    onCaptured: (Uri, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    LeoVerCaptureCamera(
        mode = LeoVerCaptureMode.BOTH,
        onCaptured = onCaptured,
        onCancel = onCancel,
        purposeLabel = "LeoVer"
    )
}
