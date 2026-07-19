package com.comunidapp.app.viewmodel.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.ModerationRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.moderation.ModerationActionRules
import com.comunidapp.app.domain.moderation.ModerationActionType
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import com.comunidapp.app.domain.moderation.ModerationTargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModerationActionUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val actionType: ModerationActionType = ModerationActionType.WARNING,
    val targetType: ModerationTargetType = ModerationTargetType.USER_PROFILE,
    val targetId: String = "",
    val reasonCode: String = "policy_violation",
    val reasonDetail: String = "",
    val expiresAtEpochMs: Long? = null,
    val confirmed: Boolean = false,
    val validationError: String? = null,
    val message: String? = null,
    val requiresStrongWarning: Boolean = false
)

class ModerationActionViewModel(
    private val caseId: String,
    private val moderationRepository: ModerationRepository = DataProvider.moderationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModerationActionUiState())
    val uiState: StateFlow<ModerationActionUiState> = _uiState.asStateFlow()
    private var lock = false

    init {
        viewModelScope.launch {
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.MODERATION_APPLY_ACTIONS
            )
            _uiState.update {
                it.copy(
                    phase = if (gate.allowed) {
                        AdministrativeScreenPhase.Content
                    } else {
                        AdministrativeScreenPhase.AccessDenied
                    }
                )
            }
        }
    }

    fun setActionType(type: ModerationActionType) {
        _uiState.update {
            it.copy(
                actionType = type,
                requiresStrongWarning = type == ModerationActionType.ACCOUNT_BANNED ||
                    type == ModerationActionType.ACCOUNT_SUSPENDED ||
                    type == ModerationActionType.VERIFICATION_REVOKED,
                expiresAtEpochMs = if (ModerationActionRules.isPermanent(type)) null else it.expiresAtEpochMs,
                validationError = null
            )
        }
    }

    fun setTarget(type: ModerationTargetType, id: String) {
        _uiState.update { it.copy(targetType = type, targetId = id, validationError = null) }
    }

    fun setReason(code: String, detail: String) {
        _uiState.update { it.copy(reasonCode = code, reasonDetail = detail, validationError = null) }
    }

    fun setExpiry(epochMs: Long?) {
        _uiState.update { it.copy(expiresAtEpochMs = epochMs, validationError = null) }
    }

    fun setConfirmed(confirmed: Boolean) {
        _uiState.update { it.copy(confirmed = confirmed) }
    }

    fun validateLocal(): Boolean {
        val s = _uiState.value
        val target = ModerationTargetRef(s.targetType, s.targetId.trim())
        val result = ModerationActionRules.validateNew(
            caseId,
            target,
            s.actionType,
            s.reasonCode,
            s.reasonDetail.ifBlank { null },
            "actor",
            clock(),
            s.expiresAtEpochMs
        )
        return if (result.isFailure) {
            _uiState.update { it.copy(validationError = result.exceptionOrNull()?.message) }
            false
        } else {
            _uiState.update { it.copy(validationError = null) }
            true
        }
    }

    fun submit() {
        if (lock) return
        if (!_uiState.value.confirmed) {
            _uiState.update { it.copy(message = "Confirmá la medida antes de aplicarla.") }
            return
        }
        if (!validateLocal()) return
        lock = true
        viewModelScope.launch {
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Submitting) }
            val s = _uiState.value
            val actor = authRepository.getCurrentUser()?.id
            if (actor == null) {
                lock = false
                _uiState.update {
                    it.copy(
                        phase = AdministrativeScreenPhase.AccessDenied,
                        message = "Sesión requerida."
                    )
                }
                return@launch
            }
            val target = ModerationTargetRef(s.targetType, s.targetId.trim())
            when (
                val result = moderationRepository.recordAction(
                    caseId,
                    target,
                    s.actionType,
                    s.reasonCode,
                    s.reasonDetail.ifBlank { null },
                    actor,
                    clock(),
                    s.expiresAtEpochMs
                )
            ) {
                is AppResult.Success -> {
                    lock = false
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            message = "Medida aplicada",
                            confirmed = false
                        )
                    }
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
        fun factory(caseId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ModerationActionViewModel(caseId) as T
            }
    }
}
