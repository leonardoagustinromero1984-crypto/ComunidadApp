package com.comunidapp.app.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.model.VaccinationRecord
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.storage.StoragePaths
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetFormUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isEditMode: Boolean = false,
    val petId: String = "",
    val ownerId: String = "",
    val name: String = "",
    val species: PetSpecies = PetSpecies.DOG,
    val sex: PetSex = PetSex.UNKNOWN,
    val ageYears: Int = 1,
    val ageMonths: Int = 0,
    val size: PetSize = PetSize.MEDIUM,
    val description: String = "",
    val vaccinationName: String = "",
    val vaccinationDate: String = "",
    val lastDeworming: String = "",
    val lastFleaTreatment: String = "",
    val photoUrl: String? = null,
    val pendingImageUri: Uri? = null,
    val existingVaccinations: List<VaccinationRecord> = emptyList(),
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false,
    val deleteSuccess: Boolean = false
)

class PetFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val petRepository: PetRepository = DataProvider.petRepository
) : ViewModel() {

    private val editPetId: String? = savedStateHandle.get<String>("petId")?.takeIf { it.isNotBlank() }
    private var loadedPet: Pet? = null

    private val _uiState = MutableStateFlow(PetFormUiState())
    val uiState: StateFlow<PetFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No hay sesión activa") }
                return@launch
            }
            if (editPetId != null) {
                val pet = petRepository.getPetById(editPetId)
                if (pet == null || pet.ownerId != authUser.id) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Mascota no encontrada")
                    }
                    return@launch
                }
                loadedPet = pet
                _uiState.update {
                    PetFormUiState(
                        isLoading = false,
                        isEditMode = true,
                        petId = pet.id,
                        ownerId = pet.ownerId,
                        name = pet.name,
                        species = pet.species,
                        sex = pet.sex,
                        ageYears = pet.ageYears,
                        ageMonths = pet.ageMonths,
                        size = pet.size,
                        description = pet.description,
                        lastDeworming = pet.lastDeworming.orEmpty(),
                        lastFleaTreatment = pet.lastFleaTreatment.orEmpty(),
                        photoUrl = pet.photoUrl,
                        existingVaccinations = pet.vaccinations
                    )
                }
            } else {
                _uiState.update {
                    PetFormUiState(isLoading = false, ownerId = authUser.id)
                }
            }
        }
    }

    fun onNameChange(value: String) = updateForm { copy(name = value, errorMessage = null) }
    fun onSpeciesChange(value: PetSpecies) = updateForm { copy(species = value, errorMessage = null) }
    fun onSexChange(value: PetSex) = updateForm { copy(sex = value, errorMessage = null) }
    fun onAgeYearsChange(value: Int) = updateForm { copy(ageYears = value.coerceAtLeast(0), errorMessage = null) }
    fun onAgeMonthsChange(value: Int) = updateForm { copy(ageMonths = value.coerceIn(0, 11), errorMessage = null) }
    fun onSizeChange(value: PetSize) = updateForm { copy(size = value, errorMessage = null) }
    fun onDescriptionChange(value: String) = updateForm { copy(description = value, errorMessage = null) }
    fun onVaccinationNameChange(value: String) = updateForm { copy(vaccinationName = value, errorMessage = null) }
    fun onVaccinationDateChange(value: String) = updateForm { copy(vaccinationDate = value, errorMessage = null) }
    fun onLastDewormingChange(value: String) = updateForm { copy(lastDeworming = value, errorMessage = null) }
    fun onLastFleaTreatmentChange(value: String) = updateForm { copy(lastFleaTreatment = value, errorMessage = null) }
    fun onImageSelected(uri: Uri?) = updateForm { copy(pendingImageUri = uri, errorMessage = null) }

    fun savePet() {
        val state = _uiState.value
        if (state.name.isBlank() || state.description.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Nombre y descripción son obligatorios") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccess = false) }

            val vaccinations = buildVaccinations(state)
            var pet = Pet(
                id = state.petId,
                ownerId = state.ownerId,
                name = state.name.trim(),
                species = state.species,
                sex = state.sex,
                ageYears = state.ageYears,
                ageMonths = state.ageMonths,
                size = state.size,
                description = state.description.trim(),
                photoUrl = state.photoUrl,
                vaccinations = vaccinations,
                lastDeworming = state.lastDeworming.trim().ifBlank { null },
                lastFleaTreatment = state.lastFleaTreatment.trim().ifBlank { null },
                reminders = loadedPet?.reminders.orEmpty(),
                createdAt = loadedPet?.createdAt
            )

            val petIdResult = if (state.isEditMode) {
                petRepository.updatePet(pet).map { pet.id }
            } else {
                petRepository.createPet(pet)
            }

            petIdResult
                .onSuccess { petId ->
                    pet = pet.copy(id = petId)
                    uploadPhotoIfNeeded(state, petId)?.let { url ->
                        pet = pet.copy(photoUrl = url)
                        petRepository.updatePet(pet)
                    }
                    loadedPet = pet
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true, petId = petId) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "No se pudo guardar la mascota"
                        )
                    }
                }
        }
    }

    fun deletePet() {
        val petId = _uiState.value.petId
        if (petId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            petRepository.deletePet(petId)
                .onSuccess {
                    _uiState.update { it.copy(isDeleting = false, deleteSuccess = true) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = error.message ?: "No se pudo eliminar la mascota"
                        )
                    }
                }
        }
    }

    fun clearSaveSuccess() = _uiState.update { it.copy(saveSuccess = false) }
    fun clearDeleteSuccess() = _uiState.update { it.copy(deleteSuccess = false) }

    private suspend fun uploadPhotoIfNeeded(state: PetFormUiState, petId: String): String? {
        val uri = state.pendingImageUri ?: return null
        val storage = DataProvider.storageService ?: return uri.toString()
        return storage.uploadImage(StoragePaths.petPhoto(state.ownerId, petId), uri)
            .getOrElse { throw it }
    }

    private fun buildVaccinations(state: PetFormUiState): List<VaccinationRecord> {
        val base = state.existingVaccinations.toMutableList()
        if (state.vaccinationName.isNotBlank() && state.vaccinationDate.isNotBlank()) {
            base.add(0, VaccinationRecord(state.vaccinationName.trim(), state.vaccinationDate.trim()))
        }
        return base
    }

    private inline fun updateForm(block: PetFormUiState.() -> PetFormUiState) {
        _uiState.update { it.block() }
    }
}

private fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    fold(onSuccess = { Result.success(transform(it)) }, onFailure = { Result.failure(it) })
