package com.comunidapp.shared.vertical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.shared.domain.lostfound.LostFoundCaseType
import com.comunidapp.shared.lostfound.LostFoundId
import com.comunidapp.shared.lostfound.LostFoundPublishUiState
import com.comunidapp.shared.lostfound.LostFoundPublishViewModelShared
import com.comunidapp.shared.lostfound.LostFoundRepository
import com.comunidapp.shared.poc.m08.platform.ImagePicker

@Composable
internal fun SharedLostFoundPublishScreen(
    lostFoundRepository: LostFoundRepository,
    imagePicker: ImagePicker?,
    onBack: () -> Unit,
    onPublished: (LostFoundId) -> Unit
) {
    val vm = remember(lostFoundRepository, imagePicker) {
        LostFoundPublishViewModelShared(lostFoundRepository, imagePicker)
    }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val form by vm.form.collectAsState()

    LaunchedEffect(form.ui) {
        val ui = form.ui
        if (ui is LostFoundPublishUiState.Success) {
            onPublished(ui.id)
        }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Publicar alerta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Zona aproximada. Podés adjuntar una foto (jpeg/png/webp, máx. 8 MB).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (form.type == LostFoundCaseType.LOST) {
                    Button(onClick = { vm.setType(LostFoundCaseType.LOST) }) { Text("Perdido") }
                    OutlinedButton(onClick = { vm.setType(LostFoundCaseType.FOUND) }) { Text("Encontrado") }
                } else {
                    OutlinedButton(onClick = { vm.setType(LostFoundCaseType.LOST) }) { Text("Perdido") }
                    Button(onClick = { vm.setType(LostFoundCaseType.FOUND) }) { Text("Encontrado") }
                }
            }

            OutlinedTextField(
                value = form.displayName,
                onValueChange = vm::setDisplayName,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(if (form.type == LostFoundCaseType.LOST) "Nombre (obligatorio)" else "Nombre (opcional)")
                },
                singleLine = true,
                enabled = form.canSubmit
            )
            OutlinedTextField(
                value = form.speciesLabel,
                onValueChange = vm::setSpeciesLabel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Especie (Perro / Gato / Otro)") },
                singleLine = true,
                enabled = form.canSubmit
            )
            OutlinedTextField(
                value = form.locality,
                onValueChange = vm::setLocality,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Zona / localidad aproximada") },
                singleLine = true,
                enabled = form.canSubmit
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = vm::setDescription,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descripción") },
                minLines = 3,
                enabled = form.canSubmit
            )
            OutlinedTextField(
                value = form.contactNote,
                onValueChange = vm::setContactNote,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nota de contacto (opcional)") },
                singleLine = true,
                enabled = form.canSubmit
            )

            if (imagePicker != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.pickMedia() }, enabled = form.canSubmit) {
                        Text(if (form.mediaLabel != null) "Cambiar foto" else "Agregar foto (opcional)")
                    }
                    if (form.mediaLabel != null) {
                        TextButton(onClick = { vm.clearMedia() }, enabled = form.canSubmit) {
                            Text("Quitar")
                        }
                    }
                }
                form.mediaLabel?.let {
                    Text("Foto: $it", style = MaterialTheme.typography.labelMedium)
                }
            }

            when (val ui = form.ui) {
                LostFoundPublishUiState.Publishing, LostFoundPublishUiState.Validating -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text("Publicando…", Modifier.padding(start = 12.dp))
                    }
                }
                is LostFoundPublishUiState.Error -> Text(ui.message, color = MaterialTheme.colorScheme.error)
                is LostFoundPublishUiState.Success -> {
                    Text("Publicado correctamente.")
                    ui.publicCode?.let { Text("Código: $it") }
                    if (ui.mediaDeferred) {
                        Text(
                            "La alerta fue publicada, pero no pudimos subir la foto.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> Unit
            }

            Button(
                onClick = { vm.publish() },
                modifier = Modifier.fillMaxWidth(),
                enabled = form.canSubmit
            ) {
                Text("Publicar")
            }
        }
    }
}
