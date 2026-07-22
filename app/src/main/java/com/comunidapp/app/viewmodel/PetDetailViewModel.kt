package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.ClinicalRecordType
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetClinicalRecord
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
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

/**
 * LeoVer M08 — detalle de mascota + Etapa 6 (fallecimiento / restore / gating).
 * Autorización solo por PetAccessContext; nunca por ownerId.
 */
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
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        if (petId.isBlank()) null else petRepository.getPetById(petId)
    )

    val clinicalRecords: StateFlow<List<PetClinicalRecord>> =
        if (petId.isBlank()) {
            flowOf(emptyList())
        } else {
            platformRepository.observeClinicalRecords(petId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val access: StateFlow<PetAccessContext?> = accessContext.asStateFlow()

    /** Capability-based manage gate via PetAccessContext (not legacy owner equality). */
    val canManage: StateFlow<Boolean> = combine(accessContext, currentUserId, pet) { ctx, userId, p ->
        userId != null && ctx != null && p?.status == "ACTIVE" &&
            (ctx.canUpdate || ctx.canArchive)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * M08 Etapa 5: entrada "Responsables y permisos" solo con lectura confirmada
     * por el backend (canRead del PetAccessContext), nunca por ownerId.
     * Etapa 6: bloqueada si DECEASED (solo lectura / historial / fotos / clínico).
     */
    val canViewGovernance: StateFlow<Boolean> = combine(accessContext, currentUserId, pet) { ctx, userId, p ->
        userId != null && ctx != null && ctx.canRead && p?.status != "DECEASED"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canMarkDeceased: StateFlow<Boolean> = combine(accessContext, pet) { ctx, p ->
        ctx?.canMarkDeceased == true && p?.status == "ACTIVE"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canRestore: StateFlow<Boolean> = combine(accessContext, pet) { ctx, p ->
        ctx?.canRestore == true && p?.status == "ARCHIVED"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canViewHistory: StateFlow<Boolean> = combine(accessContext, currentUserId) { ctx, userId ->
        userId != null && ctx != null && (ctx.canViewHistory || ctx.canRead)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    private val _lifecycleSuccess = MutableStateFlow(false)
    val lifecycleSuccess: StateFlow<Boolean> = _lifecycleSuccess.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _clinicalNote = MutableStateFlow("")
    val clinicalNote: StateFlow<String> = _clinicalNote.asStateFlow()

    private val _clinicalTitle = MutableStateFlow("")
    val clinicalTitle: StateFlow<String> = _clinicalTitle.asStateFlow()

    init {
        viewModelScope.launch {
            currentUserId.value = authRepository.getCurrentUser()?.id
            refreshAccess()
        }
    }

    fun refreshAccess() {
        if (petId.isBlank()) return
        viewModelScope.launch {
            petRepository.getPetAccessContext(petId)
                .onSuccess { accessContext.value = it }
                .onFailure { /* keep gates false */ }
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
        val ctx = accessContext.value
        if (ctx?.canArchive != true) {
            _errorMessage.value = M08PetErrorMapper.userMessage("FORBIDDEN")
            return
        }
        if (pet.value?.status == "DECEASED") {
            _errorMessage.value = M08PetErrorMapper.userMessage("PET_DECEASED_CANNOT_ARCHIVE")
            return
        }
        viewModelScope.launch {
            _errorMessage.value = null
            _isSubmitting.value = true
            petRepository.deletePet(petId)
                .onSuccess { _deleteSuccess.value = true }
                .onFailure { error ->
                    _errorMessage.value = M08PetErrorMapper.userMessage(M08PetErrorMapper.codeOf(error))
                }
            _isSubmitting.value = false
        }
    }

    fun markPetDeceased(reason: String? = null) {
        if (petId.isBlank()) return
        if (accessContext.value?.canMarkDeceased != true) {
            _errorMessage.value = M08PetErrorMapper.userMessage("FORBIDDEN")
            return
        }
        if (pet.value?.status != "ACTIVE") {
            _errorMessage.value = M08PetErrorMapper.userMessage("PET_NOT_ACTIVE")
            return
        }
        viewModelScope.launch {
            _errorMessage.value = null
            _isSubmitting.value = true
            petRepository.markPetDeceased(petId, reason?.trim()?.takeIf { it.isNotEmpty() })
                .onSuccess {
                    refreshAccess()
                    _lifecycleSuccess.value = true
                }
                .onFailure { error ->
                    _errorMessage.value = M08PetErrorMapper.userMessage(M08PetErrorMapper.codeOf(error))
                }
            _isSubmitting.value = false
        }
    }

    fun restorePet() {
        if (petId.isBlank()) return
        if (accessContext.value?.canRestore != true) {
            _errorMessage.value = M08PetErrorMapper.userMessage("FORBIDDEN")
            return
        }
        if (pet.value?.status != "ARCHIVED") {
            _errorMessage.value = M08PetErrorMapper.userMessage("PET_NOT_ARCHIVED")
            return
        }
        viewModelScope.launch {
            _errorMessage.value = null
            _isSubmitting.value = true
            petRepository.restorePet(petId)
                .onSuccess {
                    refreshAccess()
                    _lifecycleSuccess.value = true
                }
                .onFailure { error ->
                    _errorMessage.value = M08PetErrorMapper.userMessage(M08PetErrorMapper.codeOf(error))
                }
            _isSubmitting.value = false
        }
    }

    fun clearDeleteSuccess() {
        _deleteSuccess.value = false
    }

    fun clearLifecycleSuccess() {
        _lifecycleSuccess.value = false
    }
}
