package com.comunidapp.app.viewmodel.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.OrganizationVerificationRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.organization.OrganizationVerificationStatus
import com.comunidapp.app.domain.verification.OrganizationVerificationDecision
import com.comunidapp.app.domain.verification.OrganizationVerificationReview
import com.comunidapp.app.viewmodel.moderation.AdministrativeAccessGate
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrganizationVerificationQueueUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val reviews: List<OrganizationVerificationReview> = emptyList(),
    val canReview: Boolean = false,
    val canRevoke: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class OrganizationVerificationQueueViewModel(
    private val repository: OrganizationVerificationRepository =
        DataProvider.organizationVerificationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizationVerificationQueueUiState())
    val uiState: StateFlow<OrganizationVerificationQueueUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(phase = AdministrativeScreenPhase.Loading, reviews = emptyList())
            }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.ORGANIZATIONS_REVIEW_VERIFICATION,
                extra = setOf(PermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION)
            )
            if (!gate.allowed) {
                _uiState.update {
                    OrganizationVerificationQueueUiState(
                        phase = AdministrativeScreenPhase.AccessDenied
                    )
                }
                return@launch
            }
            when (val result = repository.listPendingVerificationRequests()) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            phase = if (result.data.isEmpty()) {
                                AdministrativeScreenPhase.Empty
                            } else {
                                AdministrativeScreenPhase.Content
                            },
                            reviews = result.data,
                            canReview = true,
                            canRevoke = AdministrativeAccessGate.hasExtra(
                                gate,
                                PermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION
                            )
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

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OrganizationVerificationQueueViewModel() as T
        }
    }
}

data class OrganizationVerificationReviewUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val review: OrganizationVerificationReview? = null,
    val canRevoke: Boolean = false,
    val orgStatus: OrganizationVerificationStatus = OrganizationVerificationStatus.PENDING,
    val actorIsOrgMember: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class OrganizationVerificationReviewViewModel(
    private val reviewId: String,
    private val repository: OrganizationVerificationRepository =
        DataProvider.organizationVerificationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val orgStatusProvider: () -> OrganizationVerificationStatus = {
        OrganizationVerificationStatus.PENDING
    },
    private val isOrgMemberProvider: () -> Boolean = { false }
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizationVerificationReviewUiState())
    val uiState: StateFlow<OrganizationVerificationReviewUiState> = _uiState.asStateFlow()
    private var lock = false

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                OrganizationVerificationReviewUiState(phase = AdministrativeScreenPhase.Loading)
            }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.ORGANIZATIONS_REVIEW_VERIFICATION,
                extra = setOf(PermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION)
            )
            if (!gate.allowed) {
                _uiState.update {
                    OrganizationVerificationReviewUiState(
                        phase = AdministrativeScreenPhase.AccessDenied
                    )
                }
                return@launch
            }
            when (val result = repository.getVerificationReview(reviewId)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            review = result.data,
                            canRevoke = AdministrativeAccessGate.hasExtra(
                                gate,
                                PermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION
                            ),
                            orgStatus = orgStatusProvider(),
                            actorIsOrgMember = isOrgMemberProvider()
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

    fun assignToMe() {
        mutate {
            val actor = authRepository.getCurrentUser()?.id ?: return@mutate failUnauthorized()
            repository.assignVerificationReview(reviewId, actor, clock())
        }
    }

    fun decide(decision: OrganizationVerificationDecision, note: String?) {
        if (decision == OrganizationVerificationDecision.REVOKE && !_uiState.value.canRevoke) {
            _uiState.update { it.copy(message = "No tenés permiso para revocar.") }
            return
        }
        mutate {
            val actor = authRepository.getCurrentUser()?.id ?: return@mutate failUnauthorized()
            repository.recordVerificationDecision(
                reviewId = reviewId,
                currentOrgStatus = _uiState.value.orgStatus,
                decision = decision,
                reviewNote = note,
                actorUserId = actor,
                actorIsOrgMember = _uiState.value.actorIsOrgMember,
                nowEpochMs = clock()
            )
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun failUnauthorized(): AppResult.Failure =
        AppResult.Failure(
            com.comunidapp.app.core.result.AppError(
                kind = com.comunidapp.app.core.result.AppErrorKind.UNAUTHORIZED,
                userMessage = "Tenés que iniciar sesión.",
                technicalMessage = "NO_SESSION",
                code = "UNAUTHORIZED"
            )
        )

    private fun mutate(block: suspend () -> AppResult<*>) {
        if (lock) return
        viewModelScope.launch {
            if (lock) return@launch
            lock = true
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Submitting) }
            when (val result = block()) {
                is AppResult.Success -> {
                    lock = false
                    _uiState.update { it.copy(message = "Decisión registrada") }
                    refresh()
                }
                is AppResult.Failure -> {
                    lock = false
                    val conflict = result.error.technicalMessage.contains("CONFLICT", true) ||
                        result.error.code == "CONFLICT_ORG_MEMBER" ||
                        result.error.technicalMessage.contains("CONFLICT_ORG_MEMBER", true)
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            message = if (conflict) {
                                "Conflicto: no podés revisar tu propia organización."
                            } else {
                                result.error.userMessage
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun factory(reviewId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OrganizationVerificationReviewViewModel(reviewId) as T
            }
    }
}
