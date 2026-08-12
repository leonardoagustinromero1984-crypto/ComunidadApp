package com.comunidapp.shared.poc.m08.viewmodel

import com.comunidapp.shared.poc.m08.data.PetPocRepository
import com.comunidapp.shared.poc.m08.domain.FileRefRules
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.poc.m08.model.ImagePickResult
import com.comunidapp.shared.poc.m08.model.PocPet
import com.comunidapp.shared.poc.m08.platform.ImagePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared ViewModels — no Android Context / Uri / Intent / ContentResolver.
 *
 * Diff vs Android PetFormViewModel:
 * - pendingImageUri: Uri?  →  selectedFile: FileRef?
 * - onImageSelected(Uri?)  →  applyPickResult(ImagePickResult) / pickVia(ImagePicker)
 * - save/upload paths omitted (FAKE_FOR_NATIVE_POC)
 */
data class PetListUiState(
    val pets: List<PocPet> = emptyList(),
    val backendModeLabel: String = ""
)

class PetListViewModel(
    private val repository: PetPocRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val uiState: StateFlow<PetListUiState> = repository.observePets()
        .map { pets ->
            PetListUiState(
                pets = pets,
                backendModeLabel = repository.backendMode.name
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, PetListUiState(backendModeLabel = repository.backendMode.name))

    fun clear() {
        scope.cancel()
    }
}

data class PetDetailUiState(
    val pet: PocPet? = null,
    val errorMessage: String? = null
)

class PetDetailViewModel(
    private val petId: String,
    private val repository: PetPocRepository
) {
    private val _uiState = MutableStateFlow(PetDetailUiState())
    val uiState: StateFlow<PetDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val pet = repository.getPet(petId)
        _uiState.value = if (pet == null) {
            PetDetailUiState(errorMessage = "PET_NOT_FOUND")
        } else {
            PetDetailUiState(pet = pet)
        }
    }
}

data class PetMediaUiState(
    val pet: PocPet? = null,
    val selectedFile: FileRef? = null,
    val isPicking: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class PetMediaViewModel(
    private val petId: String,
    private val repository: PetPocRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow(PetMediaUiState())
    val uiState: StateFlow<PetMediaUiState> = _uiState.asStateFlow()

    init {
        val pet = repository.getPet(petId)
        _uiState.value = PetMediaUiState(
            pet = pet,
            selectedFile = pet?.pendingMedia,
            errorMessage = if (pet == null) "PET_NOT_FOUND" else null
        )
    }

    fun pickVia(picker: ImagePicker) {
        if (_uiState.value.isPicking) return
        scope.launch {
            _uiState.update { it.copy(isPicking = true, errorMessage = null, infoMessage = null) }
            val result = picker.pickImage()
            applyPickResult(result)
            _uiState.update { it.copy(isPicking = false) }
        }
    }

    fun applyPickResult(result: ImagePickResult) {
        when (result) {
            ImagePickResult.Cancelled -> {
                _uiState.update {
                    it.copy(infoMessage = "Selección cancelada", errorMessage = null)
                }
            }
            is ImagePickResult.Failure -> {
                _uiState.update {
                    it.copy(errorMessage = result.message, infoMessage = null)
                }
            }
            is ImagePickResult.Success -> {
                FileRefRules.validateForPetAvatar(result.file).fold(
                    onSuccess = { file ->
                        repository.attachLocalMedia(petId, file).fold(
                            onSuccess = { pet ->
                                _uiState.update {
                                    it.copy(
                                        pet = pet,
                                        selectedFile = file,
                                        errorMessage = null,
                                        infoMessage = "Archivo listo (local, sin upload)"
                                    )
                                }
                            },
                            onFailure = { err ->
                                _uiState.update {
                                    it.copy(errorMessage = err.message ?: "ATTACH_FAILED")
                                }
                            }
                        )
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(errorMessage = err.message ?: "INVALID_FILE")
                        }
                    }
                )
            }
        }
    }

    fun clearSelection() {
        repository.clearLocalMedia(petId).fold(
            onSuccess = { pet ->
                _uiState.update {
                    it.copy(pet = pet, selectedFile = null, infoMessage = "Selección limpiada")
                }
            },
            onFailure = { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        )
    }

    fun clear() {
        scope.cancel()
    }
}
