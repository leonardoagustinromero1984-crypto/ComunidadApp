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
import com.comunidapp.app.domain.moderation.ModerationAppeal
import com.comunidapp.app.domain.moderation.ModerationAppealStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModerationAppealQueueUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val appeals: List<ModerationAppeal> = emptyList(),
    val selected: ModerationAppeal? = null,
    val message: String? = null,
    val errorMessage: String? = null
)

class ModerationAppealQueueViewModel(
    private val moderationRepository: ModerationRepository = DataProvider.moderationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModerationAppealQueueUiState())
    val uiState: StateFlow<ModerationAppealQueueUiState> = _uiState.asStateFlow()
    private var lock = false

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(phase = AdministrativeScreenPhase.Loading, appeals = emptyList())
            }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.MODERATION_REVIEW_APPEALS
            )
            if (!gate.allowed) {
                _uiState.update {
                    ModerationAppealQueueUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            when (val result = moderationRepository.listAppeals()) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            phase = if (result.data.isEmpty()) {
                                AdministrativeScreenPhase.Empty
                            } else {
                                AdministrativeScreenPhase.Content
                            },
                            appeals = result.data
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

    fun select(appeal: ModerationAppeal?) {
        _uiState.update { it.copy(selected = appeal) }
    }

    fun review(appealId: String, decision: ModerationAppealStatus, reason: String) {
        if (lock) return
        if (_uiState.value.phase != AdministrativeScreenPhase.Content &&
            _uiState.value.phase != AdministrativeScreenPhase.Empty
        ) {
            return
        }
        viewModelScope.launch {
            if (lock) return@launch
            val actor = authRepository.getCurrentUser()?.id ?: return@launch
            lock = true
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Submitting) }
            when (
                val result = moderationRepository.reviewAppeal(
                    appealId, decision, reason, actor, clock()
                )
            ) {
                is AppResult.Success -> {
                    lock = false
                    _uiState.update { it.copy(message = "Apelación revisada", selected = null) }
                    refresh()
                }
                is AppResult.Failure -> {
                    lock = false
                    val msg = if (result.error.kind == com.comunidapp.app.core.result.AppErrorKind.CONFLICT ||
                        result.error.code == "CONFLICT" ||
                        result.error.technicalMessage.contains("CONFLICT", ignoreCase = true)
                    ) {
                        "No podés revisar esta apelación (conflicto)."
                    } else {
                        result.error.userMessage
                    }
                    _uiState.update {
                        it.copy(phase = AdministrativeScreenPhase.Content, message = msg)
                    }
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ModerationAppealQueueViewModel() as T
        }
    }
}

data class MyModerationAppealsUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val appeals: List<ModerationAppeal> = emptyList(),
    val submitActionId: String = "",
    val statement: String = "",
    val message: String? = null,
    val errorMessage: String? = null
)

class MyModerationAppealsViewModel(
    private val moderationRepository: ModerationRepository = DataProvider.moderationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyModerationAppealsUiState())
    val uiState: StateFlow<MyModerationAppealsUiState> = _uiState.asStateFlow()
    private var lock = false

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.update {
                    MyModerationAppealsUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Loading) }
            // list_moderation_appeals es staff-only en servidor; en mock filtramos client-side.
            when (val result = moderationRepository.listAppeals()) {
                is AppResult.Success -> {
                    val mine = result.data.filter { it.submittedByUserId == user.id }
                    _uiState.update {
                        it.copy(
                            phase = if (mine.isEmpty()) {
                                AdministrativeScreenPhase.Empty
                            } else {
                                AdministrativeScreenPhase.Content
                            },
                            appeals = mine,
                            message = if (result.data.isNotEmpty() && mine.isEmpty()) {
                                "Solo ves tus propias apelaciones cuando el servidor lo permite."
                            } else null
                        )
                    }
                }
                is AppResult.Failure -> {
                    // Staff-only RPC → pantalla informativa / stub de envío
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Empty,
                            appeals = emptyList(),
                            message = "Podés presentar una apelación si tenés el id de la medida."
                        )
                    }
                }
            }
        }
    }

    fun onActionIdChange(v: String) = _uiState.update { it.copy(submitActionId = v) }
    fun onStatementChange(v: String) = _uiState.update { it.copy(statement = v) }

    fun submitAppeal() {
        if (lock) return
        viewModelScope.launch {
            if (lock) return@launch
            val user = authRepository.getCurrentUser() ?: return@launch
            val actionId = _uiState.value.submitActionId.trim()
            val statement = _uiState.value.statement
            if (actionId.isBlank()) {
                _uiState.update { it.copy(message = "Indicá el id de la medida.") }
                return@launch
            }
            lock = true
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Submitting) }
            when (
                val result = moderationRepository.submitAppeal(
                    actionId, user.id, statement, clock()
                )
            ) {
                is AppResult.Success -> {
                    lock = false
                    _uiState.update {
                        it.copy(message = "Apelación enviada", submitActionId = "", statement = "")
                    }
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
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MyModerationAppealsViewModel() as T
        }
    }
}
