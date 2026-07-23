package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AdoptionApplication
import com.comunidapp.app.data.model.AdoptionApplicationStatus
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.SubmitApplicationParams
import com.comunidapp.app.data.repository.AdoptionApplicationRepository
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Apply form ---

data class AdoptionApplyFormState(
    val message: String = "",
    val housingType: String = "",
    val hasOtherPets: Boolean? = null,
    val previousExperience: String = "",
    val contactPhone: String = "",
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val fieldError: String? = null,
    val submitError: String? = null
)

sealed class AdoptionApplyUiState {
    data object Loading : AdoptionApplyUiState()
    data object NotFound : AdoptionApplyUiState()
    data class Error(val message: String) : AdoptionApplyUiState()
    data class Ready(val post: AdoptionPost, val form: AdoptionApplyFormState) : AdoptionApplyUiState()
}

class AdoptionApplyViewModel(
    savedStateHandle: SavedStateHandle,
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository,
    private val applicationRepository: AdoptionApplicationRepository =
        DataProvider.adoptionApplicationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val adoptionId: String = savedStateHandle["adoptionId"] ?: ""

    private val _post = MutableStateFlow<AdoptionPost?>(null)
    private val _loadError = MutableStateFlow<String?>(null)
    private val _loading = MutableStateFlow(true)
    private val _form = MutableStateFlow(AdoptionApplyFormState())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val uiState: StateFlow<AdoptionApplyUiState> = combine(
        _post, _loading, _loadError, _form
    ) { post, loading, error, form ->
        when {
            loading && post == null && error == null -> AdoptionApplyUiState.Loading
            error == "NOT_FOUND" || (adoptionId.isBlank()) -> AdoptionApplyUiState.NotFound
            error != null && post == null -> AdoptionApplyUiState.Error(error)
            post != null -> AdoptionApplyUiState.Ready(post, form)
            else -> AdoptionApplyUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdoptionApplyUiState.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (adoptionId.isBlank()) {
                _loading.value = false
                _loadError.value = "NOT_FOUND"
                return@launch
            }
            _loading.value = true
            _loadError.value = null
            adoptionRepository.getAdoptionById(adoptionId)
                .onSuccess { post ->
                    _post.value = post
                    _loading.value = false
                    val userId = authRepository.getCurrentUser()?.id
                    if (userId != null &&
                        (post.publisherId == userId || post.shelterId == userId)
                    ) {
                        _form.update {
                            it.copy(submitError = M09AdoptionErrorMapper.userMessage("CANNOT_APPLY_TO_OWN_ADOPTION"))
                        }
                    } else if (post.status != AdoptionStatus.PUBLISHED) {
                        _form.update {
                            it.copy(
                                submitError = M09AdoptionErrorMapper.userMessage(
                                    "ADOPTION_NOT_ACCEPTING_APPLICATIONS"
                                )
                            )
                        }
                    }
                }
                .onFailure { e ->
                    val code = M09AdoptionErrorMapper.codeOf(e)
                    _post.value = null
                    _loading.value = false
                    _loadError.value = if (code == "ADOPTION_NOT_FOUND") {
                        "NOT_FOUND"
                    } else {
                        M09AdoptionErrorMapper.userMessage(code)
                    }
                }
        }
    }

    fun onMessageChange(value: String) {
        _form.update { it.copy(message = value, fieldError = null, submitError = null) }
    }

    fun onHousingTypeChange(value: String) {
        _form.update { it.copy(housingType = value) }
    }

    fun onHasOtherPetsChange(value: Boolean?) {
        _form.update { it.copy(hasOtherPets = value) }
    }

    fun onPreviousExperienceChange(value: String) {
        _form.update { it.copy(previousExperience = value) }
    }

    fun onContactPhoneChange(value: String) {
        _form.update { it.copy(contactPhone = value) }
    }

    fun submit() {
        val current = _form.value
        if (current.submitting || current.submitted) return
        val message = current.message.trim()
        if (message.isEmpty()) {
            _form.update {
                it.copy(fieldError = M09AdoptionErrorMapper.userMessage("APPLICATION_MESSAGE_REQUIRED"))
            }
            return
        }
        if (message.length > 2000) {
            _form.update { it.copy(fieldError = "El mensaje es demasiado largo.") }
            return
        }
        val post = _post.value ?: return
        if (post.status != AdoptionStatus.PUBLISHED) {
            _form.update {
                it.copy(
                    submitError = M09AdoptionErrorMapper.userMessage("ADOPTION_NOT_ACCEPTING_APPLICATIONS")
                )
            }
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(submitting = true, submitError = null, fieldError = null) }
            applicationRepository.submitApplication(
                SubmitApplicationParams(
                    adoptionId = adoptionId,
                    message = message,
                    housingType = current.housingType.ifBlank { null },
                    hasOtherPets = current.hasOtherPets,
                    previousExperience = current.previousExperience.ifBlank { null },
                    contactPhone = current.contactPhone.ifBlank { null }
                )
            ).onSuccess {
                _form.update { it.copy(submitting = false, submitted = true) }
                _events.tryEmit("Postulación enviada")
            }.onFailure { e ->
                val msg = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
                _form.update { it.copy(submitting = false, submitError = msg) }
            }
        }
    }
}

// --- My applications ---

sealed class MyApplicationsUiState {
    data object Loading : MyApplicationsUiState()
    data object Empty : MyApplicationsUiState()
    data class Content(val items: List<AdoptionApplication>) : MyApplicationsUiState()
    data class Error(val message: String) : MyApplicationsUiState()
}

class MyAdoptionApplicationsViewModel(
    private val applicationRepository: AdoptionApplicationRepository =
        DataProvider.adoptionApplicationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _loadError = MutableStateFlow<String?>(null)
    private val _loading = MutableStateFlow(true)
    private val items = MutableStateFlow<List<AdoptionApplication>>(emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var withdrawing = false

    val uiState: StateFlow<MyApplicationsUiState> = combine(items, _loading, _loadError) { list, loading, error ->
        when {
            loading && list.isEmpty() && error == null -> MyApplicationsUiState.Loading
            error != null && list.isEmpty() -> MyApplicationsUiState.Error(error)
            list.isEmpty() -> MyApplicationsUiState.Empty
            else -> MyApplicationsUiState.Content(list)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MyApplicationsUiState.Loading)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _loadError.value = null
            val uid = authRepository.getCurrentUser()?.id.orEmpty()
            applicationRepository.observeMyApplications(uid)
                .catch { e ->
                    _loadError.value = M09AdoptionErrorMapper.userMessage(
                        M09AdoptionErrorMapper.codeOf(e)
                    )
                    _loading.value = false
                }
                .collect { list ->
                    items.value = list
                    _loading.value = false
                    _loadError.value = null
                }
        }
    }

    fun withdraw(applicationId: String) {
        if (withdrawing) return
        withdrawing = true
        viewModelScope.launch {
            applicationRepository.withdrawApplication(applicationId)
                .onSuccess { updated ->
                    items.update { list ->
                        list.map { app -> if (app.id == applicationId) updated else app }
                    }
                    _message.value = "Postulación retirada"
                }
                .onFailure { e ->
                    _message.value = M09AdoptionErrorMapper.userMessage(
                        M09AdoptionErrorMapper.codeOf(e)
                    )
                }
            withdrawing = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

// --- Received applications ---

sealed class ReceivedApplicationsUiState {
    data object Loading : ReceivedApplicationsUiState()
    data object Empty : ReceivedApplicationsUiState()
    data class Content(val items: List<AdoptionApplication>) : ReceivedApplicationsUiState()
    data class Error(val message: String) : ReceivedApplicationsUiState()
}

class ReceivedAdoptionApplicationsViewModel(
    private val applicationRepository: AdoptionApplicationRepository =
        DataProvider.adoptionApplicationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _statusFilter = MutableStateFlow<AdoptionApplicationStatus?>(null)
    val statusFilter: StateFlow<AdoptionApplicationStatus?> = _statusFilter.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    private val _loading = MutableStateFlow(true)
    private val items = MutableStateFlow<List<AdoptionApplication>>(emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var actionInFlight = false

    val uiState: StateFlow<ReceivedApplicationsUiState> =
        combine(items, _loading, _loadError) { list, loading, error ->
            when {
                loading && list.isEmpty() && error == null -> ReceivedApplicationsUiState.Loading
                error != null && list.isEmpty() -> ReceivedApplicationsUiState.Error(error)
                list.isEmpty() -> ReceivedApplicationsUiState.Empty
                else -> ReceivedApplicationsUiState.Content(list)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ReceivedApplicationsUiState.Loading)

    init {
        refresh()
    }

    fun onStatusFilterChange(status: AdoptionApplicationStatus?) {
        _statusFilter.value = status
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _loadError.value = null
            val uid = authRepository.getCurrentUser()?.id.orEmpty()
            applicationRepository.observeReceivedApplications(uid, _statusFilter.value)
                .catch { e ->
                    _loadError.value = M09AdoptionErrorMapper.userMessage(
                        M09AdoptionErrorMapper.codeOf(e)
                    )
                    _loading.value = false
                }
                .collect { list ->
                    items.value = list
                    _loading.value = false
                    _loadError.value = null
                }
        }
    }

    fun markUnderReview(id: String) = runAction { applicationRepository.markUnderReview(id) }
    fun accept(id: String) = runAction { applicationRepository.acceptApplication(id) }
    fun reject(id: String, reason: String?) =
        runAction { applicationRepository.rejectApplication(id, reason) }

    private fun runAction(block: suspend () -> Result<AdoptionApplication>) {
        if (actionInFlight) return
        actionInFlight = true
        viewModelScope.launch {
            block()
                .onSuccess {
                    val uid = authRepository.getCurrentUser()?.id.orEmpty()
                    items.value = applicationRepository
                        .observeReceivedApplications(uid, _statusFilter.value)
                        .first()
                    _message.value = "Listo"
                }
                .onFailure { e ->
                    _message.value = M09AdoptionErrorMapper.userMessage(
                        M09AdoptionErrorMapper.codeOf(e)
                    )
                }
            actionInFlight = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

// --- Application detail ---

sealed class ApplicationDetailUiState {
    data object Loading : ApplicationDetailUiState()
    data object NotFound : ApplicationDetailUiState()
    data class Error(val message: String) : ApplicationDetailUiState()
    data class Content(
        val application: AdoptionApplication,
        val isApplicant: Boolean,
        val isManager: Boolean,
        val visiblePhone: String?,
        val actionInFlight: Boolean = false,
        val actionMessage: String? = null
    ) : ApplicationDetailUiState()
}

class AdoptionApplicationDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val applicationRepository: AdoptionApplicationRepository =
        DataProvider.adoptionApplicationRepository,
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val applicationId: String = savedStateHandle["applicationId"] ?: ""

    private val _uiState = MutableStateFlow<ApplicationDetailUiState>(ApplicationDetailUiState.Loading)
    val uiState: StateFlow<ApplicationDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var submitting = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (applicationId.isBlank()) {
                _uiState.value = ApplicationDetailUiState.NotFound
                return@launch
            }
            _uiState.value = ApplicationDetailUiState.Loading
            applicationRepository.getApplicationById(applicationId)
                .onSuccess { app ->
                    val userId = authRepository.getCurrentUser()?.id
                    val isApplicant = userId != null && userId == app.applicantUserId
                    val adoption = adoptionRepository.getAdoptionPostById(app.adoptionId)
                    val isManager = userId != null && adoption != null &&
                        (adoption.publisherId == userId || adoption.shelterId == userId)
                    _uiState.value = ApplicationDetailUiState.Content(
                        application = app,
                        isApplicant = isApplicant,
                        isManager = isManager,
                        visiblePhone = app.visibleContactPhone(userId, isManager)
                    )
                }
                .onFailure { e ->
                    val code = M09AdoptionErrorMapper.codeOf(e)
                    _uiState.value = when (code) {
                        "APPLICATION_NOT_FOUND" -> ApplicationDetailUiState.NotFound
                        else -> ApplicationDetailUiState.Error(
                            M09AdoptionErrorMapper.userMessage(code)
                        )
                    }
                }
        }
    }

    fun withdraw() = runApplicantAction { applicationRepository.withdrawApplication(applicationId) }
    fun markUnderReview() = runManagerAction { applicationRepository.markUnderReview(applicationId) }
    fun accept() = runManagerAction { applicationRepository.acceptApplication(applicationId) }
    fun reject(reason: String?) =
        runManagerAction { applicationRepository.rejectApplication(applicationId, reason) }

    private fun runApplicantAction(block: suspend () -> Result<AdoptionApplication>) {
        val current = _uiState.value as? ApplicationDetailUiState.Content ?: return
        if (!current.isApplicant || submitting) return
        runMutation(current, block)
    }

    private fun runManagerAction(block: suspend () -> Result<AdoptionApplication>) {
        val current = _uiState.value as? ApplicationDetailUiState.Content ?: return
        if (!current.isManager || submitting) return
        runMutation(current, block)
    }

    private fun runMutation(
        current: ApplicationDetailUiState.Content,
        block: suspend () -> Result<AdoptionApplication>
    ) {
        submitting = true
        viewModelScope.launch {
            _uiState.value = current.copy(actionInFlight = true, actionMessage = null)
            block()
                .onSuccess { app ->
                    _uiState.value = current.copy(
                        application = app,
                        visiblePhone = app.visibleContactPhone(
                            authRepository.getCurrentUser()?.id,
                            current.isManager
                        ),
                        actionInFlight = false
                    )
                    submitting = false
                    _events.tryEmit("Actualizado")
                }
                .onFailure { e ->
                    val msg = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
                    _uiState.value = current.copy(actionInFlight = false, actionMessage = msg)
                    submitting = false
                }
        }
    }
}
