package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.ContentReport
import com.comunidapp.app.data.model.ReportStatus
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.data.repository.PlatformRepository
import com.comunidapp.app.domain.authorization.AuthorizationService
import com.comunidapp.app.domain.authorization.PermissionCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminModerationUiState(
    val accessAllowed: Boolean = false,
    val accessChecked: Boolean = false,
    val reports: List<ContentReport> = emptyList(),
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AdminModerationViewModel(
    private val platformRepository: PlatformRepository = DataProvider.platformRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AdminModerationUiState> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(AdminModerationUiState(accessAllowed = false, accessChecked = true))
            } else {
                combine(
                    flow {
                        emit(permissionRepository.refresh(user.id))
                        permissionRepository.observeAuthorizationContext(user.id).collect {
                            emit(it)
                        }
                    },
                    platformRepository.observeOpenReports(),
                    _message
                ) { authz, reports, message ->
                    val allowed = AuthorizationService.hasPermission(
                        authz,
                        PermissionCode.MODERATION_VIEW
                    )
                    AdminModerationUiState(
                        accessAllowed = allowed,
                        accessChecked = true,
                        reports = if (allowed) reports else emptyList(),
                        message = message
                    )
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AdminModerationUiState()
        )

    fun dismissReport(id: String) = updateStatus(id, ReportStatus.DISMISSED, "Reporte desestimado")

    fun actionReport(id: String) = updateStatus(id, ReportStatus.ACTIONED, "Acción aplicada al reporte")

    fun clearMessage() = _message.update { null }

    private fun updateStatus(id: String, status: ReportStatus, successMessage: String) {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser() ?: return@launch
            val allowed = permissionRepository.hasPermission(
                user.id,
                PermissionCode.MODERATION_MANAGE_REPORTS
            )
            if (!allowed) {
                _message.value = "No tenés permiso para moderar."
                return@launch
            }
            platformRepository.updateReportStatus(id, status, user.id)
                .onSuccess { _message.value = successMessage }
                .onFailure { error ->
                    _message.value = error.message ?: "No se pudo actualizar el reporte"
                }
        }
    }
}
