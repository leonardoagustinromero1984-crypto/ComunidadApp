package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.CreateM14CredentialInput
import com.comunidapp.app.data.model.CreateM14PassportInput
import com.comunidapp.app.data.model.M14Credential
import com.comunidapp.app.data.model.M14CredentialType
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14PetPassport
import com.comunidapp.app.data.model.M14PublicPassportProjection
import com.comunidapp.app.data.model.M14VerificationRequest
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.UpdateM14PassportInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m14.M14ErrorMapper
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.M14CredentialRepository
import com.comunidapp.app.data.repository.M14PassportRepository
import com.comunidapp.app.data.repository.M14VerificationRepository
import com.comunidapp.app.data.repository.PetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class M14PassportListUiState {
    data object Loading : M14PassportListUiState()
    data object Empty : M14PassportListUiState()
    data class Content(val items: List<M14PetPassport>) : M14PassportListUiState()
    data class Error(val message: String) : M14PassportListUiState()
}

class M14PassportListViewModel(
    private val repository: M14PassportRepository = DataProvider.m14PassportRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M14PassportListUiState>(M14PassportListUiState.Loading)
    val uiState: StateFlow<M14PassportListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeMyPassports()
                .catch { e ->
                    _uiState.value = M14PassportListUiState.Error(
                        M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e))
                    )
                }
                .collect { list ->
                    _uiState.value = if (list.isEmpty()) {
                        M14PassportListUiState.Empty
                    } else {
                        M14PassportListUiState.Content(list)
                    }
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M14PassportListViewModel() as T
        }
    }
}

class M14PetPassportViewModel(
    private val petId: String,
    private val passportRepository: M14PassportRepository = DataProvider.m14PassportRepository,
    private val petRepository: PetRepository = DataProvider.petRepository
) : ViewModel() {
    private val _passport = MutableStateFlow<M14PetPassport?>(null)
    val passport: StateFlow<M14PetPassport?> = _passport.asStateFlow()
    private val _pet = MutableStateFlow<Pet?>(null)
    val pet: StateFlow<Pet?> = _pet.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            petRepository.observePet(petId).collect { _pet.value = it }
        }
        viewModelScope.launch {
            passportRepository.observePassportForPet(petId).collect { _passport.value = it }
        }
    }

    fun createFromPet() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val pet = _pet.value ?: petRepository.observePet(petId).first()
            if (pet == null) {
                _message.value = M14ErrorMapper.userMessage("PET_NOT_FOUND")
                _busy.value = false
                return@launch
            }
            passportRepository.createPassport(
                CreateM14PassportInput(
                    petId = pet.id,
                    displayName = pet.name,
                    species = pet.species,
                    breedText = pet.breed,
                    sex = pet.sex,
                    primaryColor = pet.color,
                    microchipNumber = pet.microchipId ?: pet.microchipNormalized,
                    visibility = M14Visibility.PRIVATE
                )
            ).onFailure { e ->
                _message.value = M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e))
            }
            _busy.value = false
        }
    }

    fun activate() = runAction { passportRepository.activatePassport(requirePassportId()) }

    fun setPublicRedacted() = runAction {
        passportRepository.updatePassport(
            requirePassportId(),
            UpdateM14PassportInput(visibility = M14Visibility.PUBLIC_REDACTED)
        )
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun requirePassportId(): String =
        _passport.value?.id ?: error("PASSPORT_NOT_FOUND")

    private fun runAction(block: suspend () -> Result<*>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            block().onFailure { e ->
                _message.value = M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e))
            }
            _busy.value = false
        }
    }

    companion object {
        fun factory(petId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    M14PetPassportViewModel(petId) as T
            }
    }
}

class M14PassportEditViewModel(
    private val petId: String,
    private val passportRepository: M14PassportRepository = DataProvider.m14PassportRepository
) : ViewModel() {
    private val _passport = MutableStateFlow<M14PetPassport?>(null)
    val passport: StateFlow<M14PetPassport?> = _passport.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            passportRepository.observePassportForPet(petId).collect { _passport.value = it }
        }
    }

    fun save(
        displayName: String,
        breedText: String?,
        primaryColor: String?,
        distinctiveMarks: String?,
        microchip: String?,
        sex: PetSex?
    ) {
        if (_busy.value) return
        val id = _passport.value?.id ?: run {
            _message.value = M14ErrorMapper.userMessage("PASSPORT_NOT_FOUND")
            return
        }
        viewModelScope.launch {
            _busy.value = true
            passportRepository.updatePassport(
                id,
                UpdateM14PassportInput(
                    displayName = displayName,
                    breedText = breedText,
                    primaryColor = primaryColor,
                    distinctiveMarks = distinctiveMarks,
                    microchipNumber = microchip,
                    sex = sex
                )
            ).onSuccess {
                _saved.value = true
            }.onFailure { e ->
                _message.value = M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e))
            }
            _busy.value = false
        }
    }

    companion object {
        fun factory(petId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    M14PassportEditViewModel(petId) as T
            }
    }
}

class M14CredentialsViewModel(
    private val passportId: String,
    private val credentialRepository: M14CredentialRepository = DataProvider.m14CredentialRepository
) : ViewModel() {
    private val _items = MutableStateFlow<List<M14Credential>>(emptyList())
    val items: StateFlow<List<M14Credential>> = _items.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            credentialRepository.observeCredentials(passportId).collect { _items.value = it }
        }
    }

    companion object {
        fun factory(passportId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    M14CredentialsViewModel(passportId) as T
            }
    }
}

class M14CredentialCreateViewModel(
    private val passportId: String,
    private val credentialRepository: M14CredentialRepository = DataProvider.m14CredentialRepository
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _createdId = MutableStateFlow<String?>(null)
    val createdId: StateFlow<String?> = _createdId.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun create(
        type: M14CredentialType,
        title: String,
        mediaRef: String?,
        visibility: M14Visibility = M14Visibility.PRIVATE
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            credentialRepository.createCredential(
                CreateM14CredentialInput(
                    passportId = passportId,
                    type = type,
                    title = title,
                    visibility = visibility,
                    mediaRefs = mediaRef?.trim()?.takeIf { it.isNotEmpty() }?.let { listOf(it) }
                        .orEmpty()
                )
            ).onSuccess {
                _createdId.value = it.id
            }.onFailure { e ->
                _message.value = M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e))
            }
            _busy.value = false
        }
    }

    companion object {
        fun factory(passportId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    M14CredentialCreateViewModel(passportId) as T
            }
    }
}

class M14CredentialDetailViewModel(
    private val credentialId: String,
    private val credentialRepository: M14CredentialRepository = DataProvider.m14CredentialRepository
) : ViewModel() {
    private val _credential = MutableStateFlow<M14Credential?>(null)
    val credential: StateFlow<M14Credential?> = _credential.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            credentialRepository.observeCredential(credentialId).collect { _credential.value = it }
        }
    }

    fun requestVerification() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            credentialRepository.requestVerification(credentialId)
                .onSuccess {
                    _message.value = "Solicitud enviada: Pendiente de verificación"
                }
                .onFailure { e ->
                    _message.value = M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e))
                }
            _busy.value = false
        }
    }

    companion object {
        fun factory(credentialId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    M14CredentialDetailViewModel(credentialId) as T
            }
    }
}

class M14VerificationPrepViewModel(
    private val passportId: String,
    private val verificationRepository: M14VerificationRepository =
        DataProvider.m14VerificationRepository
) : ViewModel() {
    private val _requests = MutableStateFlow<List<M14VerificationRequest>>(emptyList())
    val requests: StateFlow<List<M14VerificationRequest>> = _requests.asStateFlow()

    init {
        viewModelScope.launch {
            verificationRepository.observeRequests(passportId).collect { _requests.value = it }
        }
    }

    companion object {
        fun factory(passportId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    M14VerificationPrepViewModel(passportId) as T
            }
    }
}

class M14PublicPassportViewModel(
    private val publicCode: String,
    private val passportRepository: M14PassportRepository = DataProvider.m14PassportRepository
) : ViewModel() {
    sealed class UiState {
        data object Loading : UiState()
        data class Content(val projection: M14PublicPassportProjection) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            passportRepository.getPublicProjection(publicCode)
                .onSuccess { _uiState.value = UiState.Content(it) }
                .onFailure { e ->
                    _uiState.value = UiState.Error(
                        M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e))
                    )
                }
        }
    }

    companion object {
        fun factory(publicCode: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    M14PublicPassportViewModel(publicCode) as T
            }
    }
}
