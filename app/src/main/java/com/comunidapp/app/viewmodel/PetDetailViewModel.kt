package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PetDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val petRepository: PetRepository = DataProvider.petRepository
) : ViewModel() {

    private val petId: String = savedStateHandle["petId"] ?: ""

    private val currentUserId = MutableStateFlow<String?>(null)

    val pet: StateFlow<Pet?> = if (petId.isBlank()) {
        flowOf(null)
    } else {
        petRepository.observePet(petId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val canManage: StateFlow<Boolean> = combine(pet, currentUserId) { pet, userId ->
        pet != null && userId != null && pet.ownerId == userId
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            currentUserId.value = authRepository.getCurrentUser()?.id
        }
    }

    fun deletePet() {
        if (petId.isBlank()) return
        viewModelScope.launch {
            _errorMessage.value = null
            petRepository.deletePet(petId)
                .onSuccess { _deleteSuccess.value = true }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "No se pudo eliminar la mascota"
                }
        }
    }

    fun clearDeleteSuccess() {
        _deleteSuccess.value = false
    }
}
