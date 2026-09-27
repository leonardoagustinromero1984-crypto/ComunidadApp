package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.ui.components.PetCard
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.BrandBackground

private sealed interface ConnectedPetState {
    data object Loading : ConnectedPetState
    data class Ready(val pet: Pet) : ConnectedPetState
    data object Unavailable : ConnectedPetState
}

/**
 * Limited social projection for an accepted Mi manada connection.
 *
 * It intentionally does not open holder detail and exposes no Health, VitaCora,
 * professional grants, responsibility controls, or editing capabilities.
 */
@Composable
fun ConnectedPetProfileScreen(
    ownerUserId: String,
    petId: String,
    onNavigateBack: () -> Unit,
    petRepository: PetRepository = DataProvider.petRepository
) {
    val state by produceState<ConnectedPetState>(
        initialValue = ConnectedPetState.Loading,
        ownerUserId,
        petId
    ) {
        value = petRepository.listPetsForPersonProfile(ownerUserId)
            .fold(
                onSuccess = { pets ->
                    pets.firstOrNull { it.id == petId }
                        ?.let(ConnectedPetState::Ready)
                        ?: ConnectedPetState.Unavailable
                },
                onFailure = { ConnectedPetState.Unavailable }
            )
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = (state as? ConnectedPetState.Ready)?.pet?.name ?: "Mascota",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val current = state) {
            ConnectedPetState.Loading ->
                LoadingState(contentModifier = Modifier.padding(padding))

            ConnectedPetState.Unavailable -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Esta mascota no está disponible en el perfil social.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            is ConnectedPetState.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PetCard(
                    pet = current.pet,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Vista social de Mi manada",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "La conexión permite ver esta ficha básica, pero no concede responsabilidad ni acceso a datos privados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
