package com.comunidapp.shared.vertical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.shared.adoption.AdoptionDetail
import com.comunidapp.shared.adoption.AdoptionId
import com.comunidapp.shared.adoption.AdoptionRepository
import com.comunidapp.shared.adoption.AdoptionSummary
import com.comunidapp.shared.domain.lostfound.LostFoundCaseType
import com.comunidapp.shared.lostfound.LostFoundDetail
import com.comunidapp.shared.lostfound.LostFoundId
import com.comunidapp.shared.lostfound.LostFoundListFilter
import com.comunidapp.shared.lostfound.LostFoundRepository
import com.comunidapp.shared.lostfound.LostFoundSummary
import com.comunidapp.shared.ui.VerticalLoadState

@Composable
internal fun SharedAlertsHubScreen(
    onBack: () -> Unit,
    onOpenLost: () -> Unit,
    onOpenFound: () -> Unit
) {
    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Alertas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Avisos de mascotas perdidas y animales encontrados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onOpenLost, modifier = Modifier.fillMaxWidth()) {
                Text("Mascotas perdidas")
            }
            Button(onClick = onOpenFound, modifier = Modifier.fillMaxWidth()) {
                Text("Animales encontrados")
            }
        }
    }
}

@Composable
internal fun SharedLostFoundListScreen(
    title: String,
    filter: LostFoundListFilter,
    lostFoundRepository: LostFoundRepository,
    onBack: () -> Unit,
    onOpenDetail: (LostFoundId) -> Unit
) {
    val vm = remember(lostFoundRepository, filter) {
        LostFoundListViewModelShared(lostFoundRepository, filter)
    }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { vm.refresh() }) { Text("Actualizar") }
            when (val s = state) {
                VerticalLoadState.Loading -> VerticalCenterLoading()
                VerticalLoadState.Empty -> Text("No hay avisos por ahora.")
                is VerticalLoadState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is VerticalLoadState.Content -> {
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(s.data, key = { it.id.value }) { item ->
                            LostFoundRow(item) { onOpenDetail(item.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LostFoundRow(item: LostFoundSummary, onClick: () -> Unit) {
    val typeLabel = when (item.type) {
        LostFoundCaseType.LOST -> "PERDIDO"
        LostFoundCaseType.FOUND -> "ENCONTRADO"
    }
    val title = item.displayName?.takeIf { it.isNotBlank() } ?: item.speciesLabel
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(typeLabel, style = MaterialTheme.typography.labelMedium)
        }
        Text("${item.speciesLabel} · ${item.status.name}")
        Text(
            "Zona: ${item.approximateLocation.displayLabel()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${item.reportedAtLabel} · ${if (item.hasPhoto) "Foto: sí" else "Foto: placeholder"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SharedLostFoundDetailScreen(
    id: LostFoundId,
    lostFoundRepository: LostFoundRepository,
    onBack: () -> Unit
) {
    val vm = remember(id, lostFoundRepository) {
        LostFoundDetailViewModelShared(id, lostFoundRepository)
    }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Detalle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            when (val s = state) {
                VerticalLoadState.Loading -> VerticalCenterLoading()
                VerticalLoadState.Empty -> Text("Sin datos")
                is VerticalLoadState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is VerticalLoadState.Content -> LostFoundDetailBody(s.data)
            }
        }
    }
}

@Composable
private fun LostFoundDetailBody(detail: LostFoundDetail) {
    val typeLabel = when (detail.type) {
        LostFoundCaseType.LOST -> "PERDIDO"
        LostFoundCaseType.FOUND -> "ENCONTRADO"
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(typeLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            detail.displayName?.takeIf { it.isNotBlank() } ?: detail.speciesLabel,
            style = MaterialTheme.typography.headlineSmall
        )
        Text("Especie: ${detail.speciesLabel}")
        detail.breedText?.let { Text("Raza: $it") }
        detail.sexLabel?.let { Text("Sexo: $it") }
        Text(detail.description)
        Text(
            "Zona: ${detail.approximateLocation.displayLabel()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Fecha: ${detail.reportedAtLabel}")
        Text("Estado: ${detail.status.name}")
        detail.publicCode?.let {
            Text("Código: $it", style = MaterialTheme.typography.labelMedium)
        }
        detail.publisherDisplayName?.let {
            Text("Publicado por: $it", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (detail.hasPhoto) "Foto: disponible" else "Foto: placeholder",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SharedAdoptionsListScreen(
    adoptionRepository: AdoptionRepository,
    onBack: () -> Unit,
    onOpenDetail: (AdoptionId) -> Unit
) {
    val vm = remember(adoptionRepository) { AdoptionListViewModelShared(adoptionRepository) }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Adopciones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { vm.refresh() }) { Text("Actualizar") }
            when (val s = state) {
                VerticalLoadState.Loading -> VerticalCenterLoading()
                VerticalLoadState.Empty -> Text("No hay animales en adopción por ahora.")
                is VerticalLoadState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is VerticalLoadState.Content -> {
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(s.data, key = { it.id.value }) { item ->
                            AdoptionRow(item) { onOpenDetail(item.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdoptionRow(item: AdoptionSummary, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(item.displayName, fontWeight = FontWeight.SemiBold)
        Text("${item.speciesLabel} · ${item.status.name}")
        item.approximateAgeLabel?.let { Text("Edad: $it") }
        item.sexLabel?.let { Text("Sexo: $it") }
        Text(
            "Zona: ${item.approximateLocation.displayLabel()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (item.hasPhoto) "Foto: sí" else "Foto: placeholder",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SharedAdoptionDetailScreen(
    id: AdoptionId,
    adoptionRepository: AdoptionRepository,
    onBack: () -> Unit
) {
    val vm = remember(id, adoptionRepository) {
        AdoptionDetailViewModelShared(id, adoptionRepository)
    }
    DisposableEffect(vm) { onDispose { vm.clear() } }
    val state by vm.state.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Adopción", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            when (val s = state) {
                VerticalLoadState.Loading -> VerticalCenterLoading()
                VerticalLoadState.Empty -> Text("Sin datos")
                is VerticalLoadState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is VerticalLoadState.Content -> AdoptionDetailBody(s.data)
            }
        }
    }
}

@Composable
private fun AdoptionDetailBody(detail: AdoptionDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(detail.displayName, style = MaterialTheme.typography.headlineSmall)
        Text("Especie: ${detail.speciesLabel}")
        detail.breedText?.let { Text("Raza: $it") }
        detail.approximateAgeLabel?.let { Text("Edad: $it") }
        detail.sexLabel?.let { Text("Sexo: $it") }
        Text(detail.description)
        Text(
            "Zona: ${detail.approximateLocation.displayLabel()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Estado: ${detail.status.name}")
        detail.publisherDisplayName?.let {
            Text("Publicado por: $it", style = MaterialTheme.typography.bodySmall)
        }
        detail.publicCode?.let {
            Text("Código: $it", style = MaterialTheme.typography.labelMedium)
        }
        Text(
            if (detail.hasPhoto) "Foto: disponible" else "Foto: placeholder",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = { }, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Postularme (próximamente)")
        }
    }
}

@Composable
private fun VerticalCenterLoading() {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
    }
}
