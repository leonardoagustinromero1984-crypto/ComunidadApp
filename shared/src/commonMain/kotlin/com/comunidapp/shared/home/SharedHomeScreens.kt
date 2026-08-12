package com.comunidapp.shared.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.pets.PetAggregate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@Composable
fun SharedHomeScreen(
    repository: SharedPetHomeRepository,
    session: SharedSessionState,
    onOpenPocLauncher: () -> Unit,
    onClose: (() -> Unit)? = null
) {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val stateFlow = remember(repository) {
        repository.observePets().stateIn(scope, SharingStarted.Eagerly, SharedHomeLoadState.Loading)
    }
    val state by stateFlow.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "LeoVer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Home KMP · dominio pets compartido",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Sesión: ${session.displayLabel}",
                style = MaterialTheme.typography.labelMedium
            )

            when (val s = state) {
                SharedHomeLoadState.Loading -> {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text("Cargando mascotas…")
                    }
                }
                SharedHomeLoadState.Empty -> Text("No hay mascotas para mostrar.")
                is SharedHomeLoadState.Error -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error
                )
                is SharedHomeLoadState.Content -> {
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(s.pets, key = { it.id.value }) { pet ->
                            PetHomeRow(pet)
                        }
                    }
                }
            }

            Button(onClick = onOpenPocLauncher, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir POCs (M22 / M08)")
            }
            if (onClose != null) {
                TextButton(onClick = onClose) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun PetHomeRow(pet: PetAggregate) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(pet.displayName, fontWeight = FontWeight.SemiBold)
        Text("id=${pet.id.value}")
        Text("estado=${pet.status.name}")
    }
}
