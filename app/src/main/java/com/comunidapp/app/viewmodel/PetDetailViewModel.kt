package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.ClinicalRecordType
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetClinicalRecord
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.PlatformRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PetDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val platformRepository: PlatformRepository = DataProvider.platformRepository
) : ViewModel() {

    private val petId: String = savedStateHandle["petId"] ?: ""

    private val currentUserId = MutableStateFlow<String?>(null)
    private val accessContext = MutableStateFlow<PetAccessContext?>(null)

    val pet: StateFlow<Pet?> = if (petId.isBlank()) {
        flowOf(null)
    } else {
        petRepository.observePet(petId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val clinicalRecords: StateFlow<List<PetClinicalRecord>> =
        if (petId.isBlank()) {
            flowOf(emptyList())
        } else {
            platformRepository.observeClinicalRecords(petId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Capability-based manage gate via PetAccessContext (not legacy owner equality). */
    val canManage: StateFlow<Boolean> = combine(accessContext, currentUserId) { ctx, userId ->
        userId != null && ctx != null && (ctx.canUpdate || ctx.canArchive)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * M08 Etapa 5: entrada "Responsables y permisos" solo con lectura confirmada
     * por el backend (canRead del PetAccessContext), nunca por ownerId.
     */
    val canViewGovernance: StateFlow<Boolean> = combine(accessContext, currentUserId) { ctx, userId ->
        userId != null && ctx != null && ctx.canRead
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _clinicalNote = MutableStateFlow("")
    val clinicalNote: StateFlow<String> = _clinicalNote.asStateFlow()

    private val _clinicalTitle = MutableStateFlow("")
    val clinicalTitle: StateFlow<String> = _clinicalTitle.asStateFlow()

    init {
        viewModelScope.launch {
            currentUserId.value = authRepository.getCurrentUser()?.id
            if (petId.isNotBlank()) {
                petRepository.getPetAccessContext(petId)
                    .onSuccess { accessContext.value = it }
                    .onFailure { /* keep canManage false */ }
            }
        }
    }

    fun updateClinicalTitle(value: String) = _clinicalTitle.update { value }
    fun updateClinicalNote(value: String) = _clinicalNote.update { value }

    fun addClinicalNote() {
        val user = authRepository.getCurrentUser() ?: return
        if (petId.isBlank()) return
        val title = _clinicalTitle.value.trim().ifBlank { "Nota clínica" }
        val notes = _clinicalNote.value.trim()
        if (notes.isBlank()) {
            _errorMessage.value = "Escribí una nota clínica"
            return
        }
        viewModelScope.launch {
            _errorMessage.value = null
            platformRepository.addClinicalRecord(
                PetClinicalRecord(
                    id = "",
                    petId = petId,
                    authorId = user.id,
                    authorName = user.name,
                    recordType = ClinicalRecordType.NOTE,
                    title = title,
                    notes = notes
                )
            ).onSuccess {
                _clinicalTitle.value = ""
                _clinicalNote.value = ""
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "No se pudo guardar la nota"
            }
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
