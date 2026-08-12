package com.comunidapp.shared.poc.m08.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.comunidapp.shared.poc.m08.data.PetPocRepository
import com.comunidapp.shared.poc.m08.navigation.PetDetailRoute
import com.comunidapp.shared.poc.m08.navigation.PetListRoute
import com.comunidapp.shared.poc.m08.navigation.PetMediaRoute
import com.comunidapp.shared.poc.m08.platform.ImagePicker
import com.comunidapp.shared.poc.m08.viewmodel.PetDetailViewModel
import com.comunidapp.shared.poc.m08.viewmodel.PetListViewModel
import com.comunidapp.shared.poc.m08.viewmodel.PetMediaViewModel

/**
 * Real multiplatform Navigation Compose graph (JetBrains 2.9.2).
 * Destinations: PetListRoute → PetDetailRoute(id) → PetMediaRoute(id).
 */
@Composable
fun M08PocApp(
    repository: PetPocRepository,
    imagePicker: ImagePicker,
    onClose: (() -> Unit)? = null,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = PetListRoute
    ) {
        composable<PetListRoute> {
            PetListScreen(
                repository = repository,
                onOpenDetail = { petId -> navController.navigate(PetDetailRoute(petId)) },
                onClose = onClose
            )
        }
        composable<PetDetailRoute> { entry ->
            val route = entry.toRoute<PetDetailRoute>()
            PetDetailScreen(
                petId = route.petId,
                repository = repository,
                onOpenMedia = { navController.navigate(PetMediaRoute(route.petId)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable<PetMediaRoute> { entry ->
            val route = entry.toRoute<PetMediaRoute>()
            PetMediaScreen(
                petId = route.petId,
                repository = repository,
                imagePicker = imagePicker,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun PetListScreen(
    repository: PetPocRepository,
    onOpenDetail: (String) -> Unit,
    onClose: (() -> Unit)? = null
) {
    val viewModel = remember(repository) { PetListViewModel(repository) }
    DisposableEffect(viewModel) { onDispose { viewModel.clear() } }
    val state by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "LeoVer KMP POC 2 — Mascotas + Media",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Nav: LIST → DETAIL/{id} → MEDIA/{id} · backend=${state.backendModeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.pets, key = { it.id }) { pet ->
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDetail(pet.id) },
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            Modifier.padding(12.dp)
                        ) {
                            Text(pet.name, fontWeight = FontWeight.SemiBold)
                            Text(pet.speciesLabel, style = MaterialTheme.typography.bodySmall)
                            if (pet.pendingMedia != null) {
                                Text(
                                    "Media local: ${pet.pendingMedia.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            if (onClose != null) {
                TextButton(onClick = onClose) { Text("Cerrar POC") }
            }
        }
    }
}

@Composable
fun PetDetailScreen(
    petId: String,
    repository: PetPocRepository,
    onOpenMedia: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel = remember(petId, repository) { PetDetailViewModel(petId, repository) }
    val state by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text(
                "Detalle · id=$petId",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            val pet = state.pet
            if (pet == null) {
                Text(state.errorMessage ?: "Error", color = MaterialTheme.colorScheme.error)
            } else {
                Text(pet.name, style = MaterialTheme.typography.headlineSmall)
                Text("Especie: ${pet.speciesLabel}")
                Text("Foto remota: ${pet.photoUrl ?: "(ninguna)"}")
                pet.pendingMedia?.let {
                    Text("Pendiente local: ${it.name} (${it.mimeType ?: "?"}, ${it.sizeBytes} B)")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenMedia, modifier = Modifier.fillMaxWidth()) {
                    Text("Editar / seleccionar foto")
                }
            }
        }
    }
}

@Composable
fun PetMediaScreen(
    petId: String,
    repository: PetPocRepository,
    imagePicker: ImagePicker,
    onBack: () -> Unit
) {
    val viewModel = remember(petId, repository) { PetMediaViewModel(petId, repository) }
    DisposableEffect(viewModel) { onDispose { viewModel.clear() } }
    val state by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start)
            ) { Text("← Volver al detalle") }

            Text(
                "Media · id=$petId",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                state.pet?.name ?: "…",
                style = MaterialTheme.typography.bodyLarge
            )

            if (state.isPicking) {
                CircularProgressIndicator()
                Text("Abriendo selector…")
            }

            Button(
                onClick = { viewModel.pickVia(imagePicker) },
                enabled = !state.isPicking && state.pet != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Elegir imagen")
            }

            OutlinedButton(
                onClick = { viewModel.clearSelection() },
                enabled = state.selectedFile != null && !state.isPicking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Quitar selección local")
            }

            val file = state.selectedFile
            if (file != null) {
                Surface(
                    Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Archivo seleccionado", fontWeight = FontWeight.SemiBold)
                        Text("Nombre: ${file.name}")
                        Text("MIME: ${file.mimeType ?: "(desconocido)"}")
                        Text("Tamaño: ${file.sizeBytes} bytes")
                        Text(
                            "platformIdentifier: (opaco, ${file.platformIdentifier.length} chars)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    "Sin archivo seleccionado",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.infoMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Text(
                "Sin upload remoto · FAKE_FOR_NATIVE_POC",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
