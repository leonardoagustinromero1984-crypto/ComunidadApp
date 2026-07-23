package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m09.CreateAdoptionParams
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.UpdateAdoptionParams
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdoptionFormState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val adoptionId: String? = null,
    val selectedPetId: String? = null,
    val selectablePets: List<Pet> = emptyList(),
    val title: String = "",
    val description: String = "",
    val requirements: String = "",
    val location: String = "",
    val status: AdoptionStatus? = null,
    val errorMessage: String? = null,
    val fieldError: String? = null,
    val saved: Boolean = false,
    val editable: Boolean = true
)

class AdoptionFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val editingId: String? = savedStateHandle.get<String>("adoptionId")
        ?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(AdoptionFormState())
    val state: StateFlow<AdoptionFormState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var submitting = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            val userId = authRepository.getCurrentUser()?.id
            if (userId.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        loading = false,
                        errorMessage = M09AdoptionErrorMapper.userMessage("NOT_AUTHENTICATED")
                    )
                }
                return@launch
            }
            val pets = petRepository.getPetsByOwner(userId)
                .filter { it.status.equals("ACTIVE", ignoreCase = true) }
            if (editingId == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        selectablePets = pets,
                        selectedPetId = pets.firstOrNull()?.id,
                        editable = true
                    )
                }
                return@launch
            }
            adoptionRepository.getAdoptionById(editingId)
                .onSuccess { post ->
                    val editable = post.status != AdoptionStatus.CLOSED &&
                        post.status != AdoptionStatus.ADOPTED
                    _state.update {
                        it.copy(
                            loading = false,
                            adoptionId = post.id,
                            selectedPetId = post.petId,
                            selectablePets = pets,
                            title = post.title.ifBlank { post.name },
                            description = post.description,
                            requirements = post.requirements,
                            location = post.location,
                            status = post.status,
                            editable = editable,
                            errorMessage = if (!editable) {
                                M09AdoptionErrorMapper.userMessage("ADOPTION_NOT_EDITABLE")
                            } else {
                                null
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            errorMessage = M09AdoptionErrorMapper.userMessage(
                                M09AdoptionErrorMapper.codeOf(error)
                            )
                        )
                    }
                }
        }
    }

    fun onPetSelected(petId: String) {
        if (editingId != null) return
        _state.update { it.copy(selectedPetId = petId, fieldError = null) }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value, fieldError = null) }
    fun onDescriptionChange(value: String) =
        _state.update { it.copy(description = value, fieldError = null) }
    fun onRequirementsChange(value: String) = _state.update { it.copy(requirements = value) }
    fun onLocationChange(value: String) = _state.update { it.copy(location = value) }

    fun saveDraft() = save(publish = false)
    fun publish() = save(publish = true)

    private fun save(publish: Boolean) {
        if (submitting) return
        val current = _state.value
        if (!current.editable) {
            _events.tryEmit(M09AdoptionErrorMapper.userMessage("ADOPTION_NOT_EDITABLE"))
            return
        }
        if (current.title.isBlank()) {
            _state.update {
                it.copy(fieldError = M09AdoptionErrorMapper.userMessage("ADOPTION_TITLE_REQUIRED"))
            }
            return
        }
        if (current.description.isBlank()) {
            _state.update {
                it.copy(
                    fieldError = M09AdoptionErrorMapper.userMessage("ADOPTION_DESCRIPTION_REQUIRED")
                )
            }
            return
        }
        submitting = true
        viewModelScope.launch {
            _state.update { it.copy(saving = true, errorMessage = null, fieldError = null) }
            val result: Result<AdoptionPost> = if (current.adoptionId.isNullOrBlank()) {
                val petId = current.selectedPetId
                if (petId.isNullOrBlank()) {
                    submitting = false
                    _state.update {
                        it.copy(
                            saving = false,
                            fieldError = "Seleccioná una mascota"
                        )
                    }
                    return@launch
                }
                adoptionRepository.createAdoption(
                    CreateAdoptionParams(
                        petId = petId,
                        title = current.title.trim(),
                        description = current.description.trim(),
                        requirements = current.requirements.trim(),
                        locationText = current.location.trim(),
                        publish = publish
                    )
                )
            } else {
                val update = adoptionRepository.updateAdoption(
                    UpdateAdoptionParams(
                        adoptionId = current.adoptionId,
                        title = current.title.trim(),
                        description = current.description.trim(),
                        requirements = current.requirements.trim(),
                        locationText = current.location.trim()
                    )
                )
                if (publish && update.isSuccess) {
                    val post = update.getOrNull()!!
                    when (post.status) {
                        AdoptionStatus.DRAFT, AdoptionStatus.PAUSED ->
                            adoptionRepository.resumeAdoption(post.id)
                        else -> update
                    }
                } else {
                    update
                }
            }
            result
                .onSuccess { post ->
                    _state.update {
                        it.copy(
                            saving = false,
                            saved = true,
                            adoptionId = post.id,
                            status = post.status
                        )
                    }
                    submitting = false
                }
                .onFailure { error ->
                    val msg = M09AdoptionErrorMapper.userMessage(
                        M09AdoptionErrorMapper.codeOf(error)
                    )
                    _state.update { it.copy(saving = false, errorMessage = msg) }
                    _events.tryEmit(msg)
                    submitting = false
                }
        }
    }
}
