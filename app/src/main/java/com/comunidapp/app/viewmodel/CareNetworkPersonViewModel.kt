package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.data.repository.CareNetworkRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.SupabaseCareNetworkRepository
import com.comunidapp.app.domain.pets.CareNetworkInvite
import com.comunidapp.app.domain.pets.CareNetworkPet
import com.comunidapp.app.domain.pets.PetSpeciesCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CareNetworkPersonUiState(
    val isLoading: Boolean = false,
    val loadErrorMessage: String? = null,
    val pendingInvites: List<CareNetworkInvite> = emptyList(),
    val carePets: List<CareNetworkPet> = emptyList(),
    val actionMessage: String? = null,
    val isSubmitting: Boolean = false
) {
    val hasContent: Boolean get() = pendingInvites.isNotEmpty() || carePets.isNotEmpty()
}

class CareNetworkPersonViewModel(
    private val repository: CareNetworkRepository = SupabaseCareNetworkRepository(),
    private val petRepository: PetRepository = DataProvider.petRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CareNetworkPersonUiState())
    val uiState: StateFlow<CareNetworkPersonUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadErrorMessage = null) }
            val invitesResult = repository.listMyInvites()
            val petsResult = repository.listMyCarePets()
            val invitesError = invitesResult.exceptionOrNull()
            val petsError = petsResult.exceptionOrNull()
            if (invitesError != null && petsError != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadErrorMessage = M08PetErrorMapper.userMessage(
                            M08PetErrorMapper.codeOf(invitesError)
                        )
                    )
                }
                return@launch
            }
            val invites = invitesResult.getOrDefault(emptyList())
            val listedPets = petsResult.getOrDefault(emptyList())
            val carePets = listedPets.map { listed ->
                val full = runCatching { petRepository.fetchPetById(listed.petId) }.getOrNull()
                listed.copy(
                    petName = full?.name?.takeIf { it.isNotBlank() } ?: listed.petName,
                    speciesLabel = full?.let { PetSpeciesCatalog.displayLabel(it.species) }.orEmpty(),
                    photoUrl = full?.photoUrl ?: listed.photoUrl
                )
            }
            val partialError = when {
                invitesError != null -> M08PetErrorMapper.userMessage(M08PetErrorMapper.codeOf(invitesError))
                petsError != null -> M08PetErrorMapper.userMessage(M08PetErrorMapper.codeOf(petsError))
                else -> null
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadErrorMessage = partialError,
                    pendingInvites = invites,
                    carePets = carePets
                )
            }
        }
    }

    fun accept(linkId: String) = mutate(linkId) { repository.accept(it) }

    fun reject(linkId: String) = mutate(linkId) { repository.reject(it) }

    fun leave(linkId: String) = mutate(linkId) { repository.leave(it) }

    private fun mutate(linkId: String, action: suspend (String) -> Result<Unit>) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, actionMessage = null) }
            action(linkId)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                    refresh()
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "No se pudo completar la acción. Intentá de nuevo."
                        )
                    }
                }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
