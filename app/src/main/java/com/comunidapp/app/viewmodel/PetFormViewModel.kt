package com.comunidapp.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.data.model.VaccinationRecord
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.storage.StoragePaths
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
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
    val sterilized: SterilizationStatus? = null,
    val microchipId: String = "",
    val lastVetVisit: String = "",
    val vaccinations: List<VaccinationRecord> = emptyList(),
    val pendingVaccineName: String = "",
    val pendingVaccineDate: String = "",
    val pendingVaccineNextDate: String = "",
    val dewormingProduct: String = "",
    val lastDeworming: String = "",
    val fleaTreatmentProduct: String = "",
    val lastFleaTreatment: String = "",
    val healthNotes: String = "",
    val photoUrl: String? = null,
    val pendingImageUri: Uri? = null,
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false,
    val deleteSuccess: Boolean = false
)

class PetFormViewModel(
    private val editPetId: String? = null,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val petRepository: PetRepository = DataProvider.petRepository
) : ViewModel() {

    private var loadedPet: Pet? = null

    private val _uiState = MutableStateFlow(PetFormUiState())
    val uiState: StateFlow<PetFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(saveSuccess = false, deleteSuccess = false, errorMessage = null)
            }

            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No hay sesión activa") }
                return@launch
            }

            val petIdToEdit = editPetId?.takeIf { it.isNotBlank() }
            if (petIdToEdit != null) {
                val pet = petRepository.fetchPetById(petIdToEdit)
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
                        sterilized = pet.sterilized,
                        microchipId = pet.microchipId.orEmpty(),
                        lastVetVisit = pet.lastVetVisit.orEmpty(),
                        vaccinations = pet.vaccinations,
                        dewormingProduct = pet.dewormingProduct.orEmpty(),
                        lastDeworming = pet.lastDeworming.orEmpty(),
                        fleaTreatmentProduct = pet.fleaTreatmentProduct.orEmpty(),
                        lastFleaTreatment = pet.lastFleaTreatment.orEmpty(),
                        healthNotes = pet.healthNotes.orEmpty(),
                        photoUrl = pet.photoUrl
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
    fun onSpeciesChange(value: PetSpecies) = updateForm {
        copy(species = value, pendingVaccineName = "", errorMessage = null)
    }
    fun onSexChange(value: PetSex) = updateForm { copy(sex = value, errorMessage = null) }
    fun onAgeYearsChange(value: Int) = updateForm { copy(ageYears = value.coerceAtLeast(0), errorMessage = null) }
    fun onAgeMonthsChange(value: Int) = updateForm { copy(ageMonths = value.coerceIn(0, 11), errorMessage = null) }
    fun onSizeChange(value: PetSize) = updateForm { copy(size = value, errorMessage = null) }
    fun onDescriptionChange(value: String) = updateForm { copy(description = value, errorMessage = null) }
    fun onSterilizedChange(value: SterilizationStatus) = updateForm { copy(sterilized = value, errorMessage = null) }
    fun onMicrochipChange(value: String) = updateForm { copy(microchipId = value, errorMessage = null) }
    fun onLastVetVisitChange(value: String) = updateForm { copy(lastVetVisit = value, errorMessage = null) }
    fun onPendingVaccineNameChange(value: String) = updateForm { copy(pendingVaccineName = value, errorMessage = null) }
    fun onPendingVaccineDateChange(value: String) = updateForm { copy(pendingVaccineDate = value, errorMessage = null) }
    fun onPendingVaccineNextDateChange(value: String) = updateForm { copy(pendingVaccineNextDate = value, errorMessage = null) }
    fun onDewormingProductChange(value: String) = updateForm { copy(dewormingProduct = value, errorMessage = null) }
    fun onLastDewormingChange(value: String) = updateForm { copy(lastDeworming = value, errorMessage = null) }
    fun onFleaProductChange(value: String) = updateForm { copy(fleaTreatmentProduct = value, errorMessage = null) }
    fun onLastFleaTreatmentChange(value: String) = updateForm { copy(lastFleaTreatment = value, errorMessage = null) }
    fun onHealthNotesChange(value: String) = updateForm { copy(healthNotes = value, errorMessage = null) }
    fun onImageSelected(uri: Uri?) = updateForm { copy(pendingImageUri = uri, errorMessage = null) }

    fun addPendingVaccination() {
        val state = _uiState.value
        if (state.pendingVaccineName.isBlank() || state.pendingVaccineDate.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Seleccioná vacuna y fecha de aplicación") }
            return
        }
        val record = VaccinationRecord(
            name = state.pendingVaccineName,
            date = state.pendingVaccineDate,
            nextDueDate = state.pendingVaccineNextDate.takeIf { it.isNotBlank() }
        )
        _uiState.update {
            it.copy(
                vaccinations = listOf(record) + it.vaccinations,
                pendingVaccineName = "",
                pendingVaccineDate = "",
                pendingVaccineNextDate = "",
                errorMessage = null
            )
        }
    }

    fun removeVaccination(index: Int) {
        _uiState.update { state ->
            state.copy(
                vaccinations = state.vaccinations.filterIndexed { i, _ -> i != index },
                errorMessage = null
            )
        }
    }

    fun savePet() {
        val state = _uiState.value
        if (state.name.isBlank() || state.description.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Nombre y descripción son obligatorios") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccess = false) }

            val vaccinations = buildFinalVaccinations(state)
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
                lastDeworming = state.lastDeworming.takeIf { it.isNotBlank() },
                dewormingProduct = state.dewormingProduct.takeIf { it.isNotBlank() },
                lastFleaTreatment = state.lastFleaTreatment.takeIf { it.isNotBlank() },
                fleaTreatmentProduct = state.fleaTreatmentProduct.takeIf { it.isNotBlank() },
                sterilized = state.sterilized,
                microchipId = state.microchipId.trim().ifBlank { null },
                lastVetVisit = state.lastVetVisit.takeIf { it.isNotBlank() },
                healthNotes = state.healthNotes.trim().ifBlank { null },
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

                    state.pendingImageUri?.let { uri ->
                        val storage = DataProvider.storageService
                        if (storage != null) {
                            storage.uploadImage(StoragePaths.petPhoto(state.ownerId, petId), uri)
                                .onSuccess { url ->
                                    pet = pet.copy(photoUrl = url)
                                    petRepository.updatePet(pet)
                                }
                                .onFailure { error ->
                                    _uiState.update {
                                        it.copy(
                                            isSaving = false,
                                            errorMessage = error.message ?: "No se pudo subir la foto"
                                        )
                                    }
                                    return@launch
                                }
                        } else {
                            pet = pet.copy(photoUrl = uri.toString())
                            petRepository.updatePet(pet)
                        }
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

    private fun buildFinalVaccinations(state: PetFormUiState): List<VaccinationRecord> {
        if (state.pendingVaccineName.isBlank() || state.pendingVaccineDate.isBlank()) {
            return state.vaccinations
        }
        return listOf(
            VaccinationRecord(
                name = state.pendingVaccineName,
                date = state.pendingVaccineDate,
                nextDueDate = state.pendingVaccineNextDate.takeIf { it.isNotBlank() }
            )
        ) + state.vaccinations
    }

    private inline fun updateForm(block: PetFormUiState.() -> PetFormUiState) {
        _uiState.update { it.block() }
    }

    companion object {
        fun factory(editPetId: String? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PetFormViewModel(editPetId = editPetId) as T
                }
            }
    }
}

private fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    fold(onSuccess = { Result.success(transform(it)) }, onFailure = { Result.failure(it) })
