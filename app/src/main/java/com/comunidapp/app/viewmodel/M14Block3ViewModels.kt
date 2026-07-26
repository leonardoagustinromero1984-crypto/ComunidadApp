package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.IssueVerifiedM14CredentialInput
import com.comunidapp.app.data.model.M14CredentialType
import com.comunidapp.app.data.model.M14PassportHistory
import com.comunidapp.app.data.model.M14PetPassport
import com.comunidapp.app.data.model.M14VerificationDecision
import com.comunidapp.app.data.model.M14VerificationRequest
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m14.M14ErrorMapper
import com.comunidapp.app.data.repository.M14CredentialRepository
import com.comunidapp.app.data.repository.M14PassportRepository
import com.comunidapp.app.data.repository.M14PublicQrPayloadService
import com.comunidapp.app.data.repository.M14VerificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class M14ManagedVerificationsViewModel(
    private val repository: M14VerificationRepository = DataProvider.m14VerificationRepository
) : ViewModel() {
    sealed class UiState {
        data object Loading : UiState()
        data object Empty : UiState()
        data class Content(val items: List<M14VerificationRequest>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.listManaged()
                .onSuccess { list ->
                    _uiState.value = if (list.isEmpty()) UiState.Empty else UiState.Content(list)
                }
                .onFailure { e ->
                    _uiState.value = UiState.Error(M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e)))
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M14ManagedVerificationsViewModel() as T
        }
    }
}

class M14VerificationDetailViewModel(
    private val requestId: String,
    private val repository: M14VerificationRepository = DataProvider.m14VerificationRepository
) : ViewModel() {
    private val _request = MutableStateFlow<M14VerificationRequest?>(null)
    val request: StateFlow<M14VerificationRequest?> = _request.asStateFlow()
    private val _decision = MutableStateFlow<M14VerificationDecision?>(null)
    val decision: StateFlow<M14VerificationDecision?> = _decision.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            repository.getRequest(requestId)
                .onSuccess { _request.value = it }
                .onFailure { e -> _message.value = M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e)) }
            repository.getDecision(requestId).onSuccess { _decision.value = it }
        }
    }

    fun openReview() = act { repository.openReview(requestId) }
    fun approve(reason: String, notePrivate: String? = null) =
        act { repository.approve(requestId, reason, notePrivate) }
    fun reject(reason: String, notePrivate: String? = null) =
        act { repository.reject(requestId, reason, notePrivate) }
    fun expire() = act { repository.expire(requestId) }

    private fun act(block: suspend () -> Result<M14VerificationRequest>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            block()
                .onSuccess {
                    _request.value = it
                    _message.value = "Actualizado."
                    repository.getDecision(requestId).onSuccess { d -> _decision.value = d }
                }
                .onFailure { e -> _message.value = M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e)) }
            _busy.value = false
        }
    }

    companion object {
        fun factory(requestId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M14VerificationDetailViewModel(requestId) as T
        }
    }
}

class M14IssueCredentialViewModel(
    private val passportId: String,
    private val repository: M14CredentialRepository = DataProvider.m14CredentialRepository
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _createdId = MutableStateFlow<String?>(null)
    val createdId: StateFlow<String?> = _createdId.asStateFlow()
    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun issue(
        title: String,
        type: M14CredentialType,
        issuerOrganizationId: String?,
        issuerProfessionalId: String?
    ) {
        viewModelScope.launch {
            repository.issueVerified(
                IssueVerifiedM14CredentialInput(
                    passportId = passportId,
                    type = type,
                    title = title,
                    issuerOrganizationId = issuerOrganizationId?.ifBlank { null },
                    issuerProfessionalId = issuerProfessionalId?.ifBlank { null },
                    visibility = M14Visibility.PRIVATE
                )
            ).onSuccess {
                _createdId.value = it.id
                _done.value = true
                _message.value = "Credencial emitida como verificada."
            }.onFailure { e ->
                _message.value = M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e))
            }
        }
    }

    companion object {
        fun factory(passportId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M14IssueCredentialViewModel(passportId) as T
        }
    }
}

class M14RevokeCredentialViewModel(
    private val credentialId: String,
    private val repository: M14CredentialRepository = DataProvider.m14CredentialRepository
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun revoke(reason: String) {
        viewModelScope.launch {
            repository.revokeVerified(credentialId, reason.ifBlank { "REVOKED" }, null)
                .onSuccess {
                    _done.value = true
                    _message.value = "Credencial revocada."
                }
                .onFailure { e ->
                    _message.value = M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e))
                }
        }
    }

    companion object {
        fun factory(credentialId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M14RevokeCredentialViewModel(credentialId) as T
        }
    }
}

class M14SharePassportViewModel(
    private val passportId: String,
    private val repository: M14PassportRepository = DataProvider.m14PassportRepository
) : ViewModel() {
    private val _passport = MutableStateFlow<M14PetPassport?>(null)
    val passport: StateFlow<M14PetPassport?> = _passport.asStateFlow()
    private val _payload = MutableStateFlow<String?>(null)
    val payload: StateFlow<String?> = _payload.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observePassport(passportId).collect { p ->
                _passport.value = p
                val code = p?.publicCode
                _payload.value = if (code.isNullOrBlank()) null
                else M14PublicQrPayloadService.buildPayload(code).getOrNull()
            }
        }
    }

    fun rotate() {
        viewModelScope.launch {
            repository.rotatePublicCode(passportId)
                .onSuccess { p ->
                    _passport.value = p
                    _payload.value = p.publicCode?.let {
                        M14PublicQrPayloadService.buildPayload(it).getOrNull()
                    }
                    _message.value = "Código público rotado. El anterior ya no es válido."
                }
                .onFailure { e ->
                    _message.value = M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e))
                }
        }
    }

    companion object {
        fun factory(passportId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M14SharePassportViewModel(passportId) as T
        }
    }
}

class M14PassportHistoryViewModel(
    private val passportId: String,
    private val repository: M14PassportRepository = DataProvider.m14PassportRepository
) : ViewModel() {
    private val _items = MutableStateFlow<List<M14PassportHistory>>(emptyList())
    val items: StateFlow<List<M14PassportHistory>> = _items.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.observeHistory(passportId).first() }
                .onSuccess { _items.value = it }
                .onFailure { e ->
                    _message.value = M14ErrorMapper.userMessage(M14ErrorMapper.codeOf(e))
                }
        }
    }

    companion object {
        fun factory(passportId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M14PassportHistoryViewModel(passportId) as T
        }
    }
}
