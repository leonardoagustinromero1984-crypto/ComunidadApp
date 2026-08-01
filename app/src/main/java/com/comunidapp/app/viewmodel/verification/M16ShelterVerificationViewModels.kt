package com.comunidapp.app.viewmodel.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.model.M16ShelterVerificationDecision
import com.comunidapp.app.data.model.M16ShelterVerificationRequest
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.M16ShelterVerificationRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.viewmodel.moderation.AdministrativeAccessGate
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class M16ShelterVerificationReviewUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val request: M16ShelterVerificationRequest? = null,
    val canDecide: Boolean = false,
    val notes: String = "",
    val message: String? = null,
    val errorMessage: String? = null
)

class M16ShelterVerificationReviewViewModel(
    private val requestId: String,
    private val repository: M16ShelterVerificationRepository = DataProvider.m16ShelterVerificationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(M16ShelterVerificationReviewUiState())
    val uiState: StateFlow<M16ShelterVerificationReviewUiState> = _uiState.asStateFlow()
    private var lock = false

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { M16ShelterVerificationReviewUiState(phase = AdministrativeScreenPhase.Loading) }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.ORGANIZATIONS_REVIEW_VERIFICATION,
                extra = setOf(PermissionCode.MODERATION_MANAGE_REPORTS)
            )
            if (!gate.allowed) {
                _uiState.update {
                    M16ShelterVerificationReviewUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            when (val result = repository.getRequest(requestId)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            request = result.data,
                            canDecide = gate.allowed
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Error,
                            errorMessage = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun decide(decision: M16ShelterVerificationDecision) {
        if (lock || !_uiState.value.canDecide) return
        viewModelScope.launch {
            lock = true
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Submitting) }
            when (
                val result = repository.decide(requestId, decision, _uiState.value.notes.ifBlank { null })
            ) {
                is AppResult.Success -> {
                    lock = false
                    _uiState.update { it.copy(message = "Decisión registrada", phase = AdministrativeScreenPhase.Content) }
                    refresh()
                }
                is AppResult.Failure -> {
                    lock = false
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            message = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(requestId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M16ShelterVerificationReviewViewModel(requestId) as T
        }
    }
}
