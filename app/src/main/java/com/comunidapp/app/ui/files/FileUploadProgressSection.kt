package com.comunidapp.app.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.files.FileUploadPhase
import com.comunidapp.app.domain.files.FileUploadUiState

@Composable
fun FileUploadProgressSection(
    state: FileUploadUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.phase == FileUploadPhase.Idle) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.phase in setOf(
                FileUploadPhase.Validating,
                FileUploadPhase.Preparing,
                FileUploadPhase.Uploading,
                FileUploadPhase.Completing
            )
        ) {
            LinearProgressIndicator(
                progress = { state.progressPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Cargando archivo: ${state.progressPercent.coerceIn(0, 100)}%")
        }
        state.userMessage?.takeIf { it.isNotBlank() }?.let { Text(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.canCancel) {
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            }
            if (state.canRetry) {
                Button(onClick = onRetry) { Text("Reintentar") }
            }
        }
    }
}
