package com.comunidapp.shared.publiccontent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.shared.deeplink.DeepLinkTarget
import com.comunidapp.shared.deeplink.deepLinkTypeLabel
import com.comunidapp.shared.media.MediaResolver
import com.comunidapp.shared.media.SharedRemoteImage

/**
 * Resuelve deep links públicos vía [PublicContentRepository] y muestra detalle SAFE.
 */
@Composable
fun SharedPublicContentScreen(
    target: DeepLinkTarget,
    repository: PublicContentRepository,
    mediaResolver: MediaResolver? = null,
    onBack: () -> Unit,
    onOpenAdoptions: () -> Unit,
    onOpenLost: () -> Unit,
    onOpenFound: () -> Unit,
    onOpenPetsHub: () -> Unit,
    onOpenHome: () -> Unit
) {
    var result by remember(target, repository) {
        mutableStateOf<PublicContentResult?>(null)
    }

    LaunchedEffect(target, repository) {
        result = null
        result = repository.resolve(target)
    }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text(
                "Contenido público",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Tipo: ${deepLinkTypeLabel(target)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (val r = result) {
                null -> {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text("Cargando…", Modifier.padding(top = 12.dp))
                    }
                }
                is PublicContentResult.Success -> PublicContentBody(r.content, mediaResolver)
                PublicContentResult.NotFound -> {
                    Text(
                        "Contenido no disponible",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                    HubCtas(
                        target = target,
                        onOpenAdoptions = onOpenAdoptions,
                        onOpenLost = onOpenLost,
                        onOpenFound = onOpenFound,
                        onOpenPetsHub = onOpenPetsHub,
                        onOpenHome = onOpenHome
                    )
                }
                is PublicContentResult.Unavailable -> {
                    Text(r.message, color = MaterialTheme.colorScheme.error)
                    HubCtas(
                        target = target,
                        onOpenAdoptions = onOpenAdoptions,
                        onOpenLost = onOpenLost,
                        onOpenFound = onOpenFound,
                        onOpenPetsHub = onOpenPetsHub,
                        onOpenHome = onOpenHome
                    )
                }
                is PublicContentResult.NetworkError -> {
                    Text(r.message, color = MaterialTheme.colorScheme.error)
                    HubCtas(
                        target = target,
                        onOpenAdoptions = onOpenAdoptions,
                        onOpenLost = onOpenLost,
                        onOpenFound = onOpenFound,
                        onOpenPetsHub = onOpenPetsHub,
                        onOpenHome = onOpenHome
                    )
                }
                is PublicContentResult.Unconfigured -> {
                    Text(r.message, color = MaterialTheme.colorScheme.error)
                    HubCtas(
                        target = target,
                        onOpenAdoptions = onOpenAdoptions,
                        onOpenLost = onOpenLost,
                        onOpenFound = onOpenFound,
                        onOpenPetsHub = onOpenPetsHub,
                        onOpenHome = onOpenHome
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicContentBody(content: PublicContent, mediaResolver: MediaResolver?) {
    when (content) {
        is PublicContent.Pet -> {
            SharedRemoteImage(
                mediaRef = content.photo,
                mediaResolver = mediaResolver,
                contentDescription = "Foto de ${content.displayName}",
                size = 160.dp
            )
            Text(content.displayName, style = MaterialTheme.typography.headlineSmall)
            content.species?.let { Text("Especie: $it") }
            content.breedText?.let { Text("Raza: $it") }
            content.sex?.let { Text("Sexo: $it") }
            Text("Estado: ${content.status}")
            content.primaryColor?.let { Text("Color: $it") }
            content.distinctiveMarks?.let { Text("Marcas: $it") }
            content.microchipMasked?.let { Text("Microchip: $it") }
            Text("Código: ${content.publicCode}", style = MaterialTheme.typography.labelMedium)
        }
        is PublicContent.Adoption -> {
            SharedRemoteImage(
                mediaRef = content.photo,
                mediaResolver = mediaResolver,
                contentDescription = content.name?.let { "Foto de $it" } ?: "Foto de adopción",
                size = 160.dp
            )
            Text(
                content.name ?: content.title ?: "Adopción",
                style = MaterialTheme.typography.headlineSmall
            )
            content.title?.takeIf { it != content.name }?.let { Text(it) }
            content.species?.let { Text("Especie: $it") }
            content.sex?.let { Text("Sexo: $it") }
            content.size?.let { Text("Tamaño: $it") }
            content.description?.let { Text(it) }
            content.locationText?.let {
                Text("Zona: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content.publisherDisplayName?.let {
                Text("Publicado por: $it", style = MaterialTheme.typography.bodySmall)
            }
            Text("Estado: ${content.status}")
            Text("Código: ${content.publicCode}", style = MaterialTheme.typography.labelMedium)
        }
        is PublicContent.LostFound -> {
            val typeLabel = when (content.caseType) {
                PublicLostFoundCaseType.LOST -> "PERDIDO"
                PublicLostFoundCaseType.FOUND -> "ENCONTRADO"
            }
            SharedRemoteImage(
                mediaRef = content.photo,
                mediaResolver = mediaResolver,
                contentDescription = content.petName?.let { "Foto de $it" } ?: "Foto del aviso",
                size = 160.dp
            )
            Text(typeLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                content.petName ?: content.species ?: typeLabel,
                style = MaterialTheme.typography.headlineSmall
            )
            content.species?.let { Text("Especie: $it") }
            content.description?.let { Text(it) }
            content.zoneText?.let {
                Text("Zona: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Estado: ${content.status}")
            Text("Código: ${content.publicCode}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun HubCtas(
    target: DeepLinkTarget,
    onOpenAdoptions: () -> Unit,
    onOpenLost: () -> Unit,
    onOpenFound: () -> Unit,
    onOpenPetsHub: () -> Unit,
    onOpenHome: () -> Unit
) {
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
