package com.comunidapp.app.ui.screens.foster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.leo.LeoVerCard
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FosterNewPlacementViewModel(
    private val petRepository: com.comunidapp.app.data.repository.PetRepository = DataProvider.petRepository,
    private val placementRepository: com.comunidapp.app.data.repository.FosterPlacementRepository =
        DataProvider.fosterPlacementRepository,
    private val authRepository: com.comunidapp.app.data.repository.AuthRepository = AuthProvider.repository
) : ViewModel() {
    val pets: StateFlow<List<Pet>> = petRepository.observePets()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _created = MutableStateFlow(false)
    val created: StateFlow<Boolean> = _created.asStateFlow()

    fun create(pet: Pet) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            placementRepository.createDirectPlacement(
                petId = pet.id,
                startsAtMillis = System.currentTimeMillis(),
                endsAtMillis = null
            ).onSuccess {
                _created.value = true
                _message.value = null
            }.onFailure {
                _message.value = it.message ?: "No se pudo registrar el tránsito."
            }
            _busy.value = false
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FosterNewPlacementViewModel() as T
        }
    }
}

@Composable
fun FosterNewPlacementScreen(
    onNavigateBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: FosterNewPlacementViewModel = viewModel(factory = FosterNewPlacementViewModel.factory())
) {
    val pets by viewModel.pets.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val created by viewModel.created.collectAsState()
    var selected by remember { mutableStateOf<Pet?>(null) }
    if (created) {
        onCreated()
        return
    }
    VisualDirectionPilot {
        val visual = leoVisual()
        Scaffold(
            containerColor = visual.background,
            topBar = {
                LeoTopAppBar(
                    title = "Nuevo tránsito",
                    subtitle = "Alojamientos temporales de mascotas que están a tu cuidado.",
                    showBackButton = true,
                    onBackClick = onNavigateBack
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(LeoDimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
            ) {
                Text(
                    "Elegí una mascota ya registrada en LeoVer. No se crea una mascota nueva ni otra VitaCora.",
                    style = LeoCaption,
                    color = visual.textSecondary
                )
                message?.let { Text(it, color = visual.error) }
                if (pets.isEmpty()) {
                    LeoEmptyState(
                        title = "No hay mascotas canónicas para vincular",
                        message = "Si la mascota todavía no existe en LeoVer, usá el alta o invitación canónica. No escribas un nombre suelto."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
                    ) {
                        items(pets, key = { it.id }) { pet ->
                            LeoVerCard(onClick = { selected = pet }) {
                                Text(pet.name, color = visual.textPrimary)
                                if (selected?.id == pet.id) {
                                    Text("Seleccionada", style = LeoCaption, color = visual.primary)
                                }
                            }
                        }
                    }
                    LeoPrimaryButton(
                        text = "Registrar tránsito",
                        onClick = { selected?.let(viewModel::create) },
                        enabled = selected != null && !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
