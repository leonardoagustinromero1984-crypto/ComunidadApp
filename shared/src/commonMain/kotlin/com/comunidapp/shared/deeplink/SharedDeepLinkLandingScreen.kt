package com.comunidapp.shared.deeplink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Landing mínima para códigos públicos — sin RPC inventada.
 */
@Composable
fun SharedDeepLinkLandingScreen(
    target: DeepLinkTarget,
    onBack: () -> Unit,
    onOpenAdoptions: () -> Unit,
    onOpenLost: () -> Unit,
    onOpenFound: () -> Unit,
    onOpenPetsHub: () -> Unit,
    onOpenHome: () -> Unit
) {
    val (title, code) = when (target) {
        is DeepLinkTarget.PetPublic -> "Mascota pública" to target.publicCode
        is DeepLinkTarget.AdoptionPublic -> "Adopción pública" to target.publicCode
        is DeepLinkTarget.LostCase -> "Caso perdido" to target.publicCode
        is DeepLinkTarget.FoundCase -> "Caso encontrado" to target.publicCode
        is DeepLinkTarget.Passport -> "Pasaporte" to target.publicCode
        DeepLinkTarget.SafeHome -> "Inicio" to null
        is DeepLinkTarget.Unsupported -> "Enlace no soportado" to null
    }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (code != null) {
                Text(
                    "Abrí el contenido público: $code",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "La app compartida muestra el código; el detalle remoto por código público " +
                        "se cablea cuando exista el contrato.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (target is DeepLinkTarget.Unsupported) {
                Text(
                    "No pudimos abrir ese enlace (${target.reason}).",
                    color = MaterialTheme.colorScheme.error
                )
            }
            when (target) {
                is DeepLinkTarget.AdoptionPublic -> Button(
                    onClick = onOpenAdoptions,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ir a Adopciones") }
                is DeepLinkTarget.LostCase -> Button(
                    onClick = onOpenLost,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ir a Perdidos") }
                is DeepLinkTarget.FoundCase -> Button(
                    onClick = onOpenFound,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ir a Encontrados") }
                is DeepLinkTarget.PetPublic, is DeepLinkTarget.Passport -> Button(
                    onClick = onOpenPetsHub,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ir a Mascotas") }
                else -> Unit
            }
            OutlinedButton(onClick = onOpenHome, modifier = Modifier.fillMaxWidth()) {
                Text("Ir al inicio")
            }
        }
    }
}

fun deepLinkTypeLabel(target: DeepLinkTarget): String = when (target) {
    is DeepLinkTarget.PetPublic -> "PET"
    is DeepLinkTarget.AdoptionPublic -> "ADOPTION"
    is DeepLinkTarget.LostCase -> "LOST"
    is DeepLinkTarget.FoundCase -> "FOUND"
    is DeepLinkTarget.Passport -> "PASSPORT"
    DeepLinkTarget.SafeHome -> "SAFE_HOME"
    is DeepLinkTarget.Unsupported -> "UNSUPPORTED"
}
