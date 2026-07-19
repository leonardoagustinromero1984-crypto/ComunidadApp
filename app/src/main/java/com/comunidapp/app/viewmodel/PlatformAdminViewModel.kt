package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AdminRoleAssignment
import com.comunidapp.app.data.repository.AdminStatusHistoryEntry
import com.comunidapp.app.data.repository.AdminUserSummary
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.data.repository.PlatformAdministrationRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlatformAdminUiState(
    val accessChecked: Boolean = false,
    val accessAllowed: Boolean = false,
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<AdminUserSummary> = emptyList(),
    val selectedUserId: String? = null,
    val selectedUser: AdminUserSummary? = null,
    val roles: List<AdminRoleAssignment> = emptyList(),
    val history: List<AdminStatusHistoryEntry> = emptyList(),
    val canViewPrivate: Boolean = false,
    val canChangeStatus: Boolean = false,
    val canAssignRoles: Boolean = false,
    val canRevokeRoles: Boolean = false,
    val canViewAudit: Boolean = false,
    val pendingStatus: AccountStatus? = null,
    val pendingRole: PlatformRoleCode? = null,
    val reasonCode: String = "manual_admin",
    val confirmAction: String? = null,
    val message: String? = null,
    val isWorking: Boolean = false
)

class PlatformAdminViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val adminRepository: PlatformAdministrationRepository =
        DataProvider.platformAdministrationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlatformAdminUiState())
    val uiState: StateFlow<PlatformAdminUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.update {
                    it.copy(accessChecked = true, accessAllowed = false)
                }
                return@launch
            }
            permissionRepository.refresh(user.id)
            val allowed = permissionRepository.hasPermission(user.id, PermissionCode.ROLES_VIEW) ||
                permissionRepository.hasPermission(user.id, PermissionCode.USERS_CHANGE_STATUS) ||
                permissionRepository.hasPermission(user.id, PermissionCode.MODERATION_VIEW)
            _uiState.update {
                it.copy(
                    accessChecked = true,
                    accessAllowed = allowed,
                    canViewPrivate = permissionRepository.hasPermission(
                        user.id,
                        PermissionCode.USERS_VIEW_PRIVATE
                    ),
                    canChangeStatus = permissionRepository.hasPermission(
                        user.id,
                        PermissionCode.USERS_CHANGE_STATUS
                    ),
                    canAssignRoles = permissionRepository.hasPermission(
                        user.id,
                        PermissionCode.ROLES_ASSIGN
                    ),
                    canRevokeRoles = permissionRepository.hasPermission(
                        user.id,
                        PermissionCode.ROLES_REVOKE
                    ),
                    canViewAudit = permissionRepository.hasPermission(
                        user.id,
                        PermissionCode.AUDIT_VIEW
                    )
                )
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, message = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(300)
            adminRepository.searchUsers(query)
                .onSuccess { list ->
                    _uiState.update { it.copy(isSearching = false, results = list) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            results = emptyList(),
                            message = err.message ?: "No se pudo buscar"
                        )
                    }
                }
        }
    }

    fun selectUser(userId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedUserId = userId,
                    selectedUser = it.results.find { u -> u.id == userId },
                    isWorking = true,
                    message = null
                )
            }
            val roles = adminRepository.getUserRoles(userId).getOrElse { emptyList() }
            val history = if (_uiState.value.canViewAudit) {
                adminRepository.getStatusHistory(userId).getOrElse { emptyList() }
            } else {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    roles = roles,
                    history = history,
                    isWorking = false,
                    selectedUser = it.selectedUser ?: it.results.find { u -> u.id == userId }
                )
            }
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedUserId = null,
                selectedUser = null,
                roles = emptyList(),
                history = emptyList(),
                confirmAction = null
            )
        }
    }

    fun onReasonCodeChange(value: String) {
        _uiState.update { it.copy(reasonCode = value) }
    }

    fun requestStatusChange(status: AccountStatus) {
        if (!_uiState.value.canChangeStatus) return
        _uiState.update {
            it.copy(pendingStatus = status, confirmAction = "status", message = null)
        }
    }

    fun requestAssignRole(role: PlatformRoleCode) {
        if (!_uiState.value.canAssignRoles) return
        _uiState.update {
            it.copy(pendingRole = role, confirmAction = "assign", message = null)
        }
    }

    fun requestRevokeRole(role: PlatformRoleCode) {
        if (!_uiState.value.canRevokeRoles) return
        _uiState.update {
            it.copy(pendingRole = role, confirmAction = "revoke", message = null)
        }
    }

    fun cancelConfirm() {
        _uiState.update {
            it.copy(confirmAction = null, pendingRole = null, pendingStatus = null)
        }
    }

    fun confirmPendingAction() {
        val state = _uiState.value
        val target = state.selectedUserId ?: return
        when (state.confirmAction) {
            "status" -> {
                val status = state.pendingStatus ?: return
                viewModelScope.launch {
                    _uiState.update { it.copy(isWorking = true, confirmAction = null) }
                    adminRepository.changeAccountStatus(
                        target,
                        status,
                        state.reasonCode.ifBlank { "manual_admin" }
                    ).onSuccess {
                        selectUser(target)
                        _uiState.update {
                            it.copy(isWorking = false, message = "Estado actualizado")
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(isWorking = false, message = err.message ?: "Error")
                        }
                    }
                }
            }
            "assign" -> {
                val role = state.pendingRole ?: return
                viewModelScope.launch {
                    _uiState.update { it.copy(isWorking = true, confirmAction = null) }
                    adminRepository.assignRole(
                        target,
                        role,
                        state.reasonCode.ifBlank { "manual_admin" }
                    ).onSuccess {
                        selectUser(target)
                        _uiState.update {
                            it.copy(isWorking = false, message = "Rol asignado")
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(isWorking = false, message = err.message ?: "Error")
                        }
                    }
                }
            }
            "revoke" -> {
                val role = state.pendingRole ?: return
                viewModelScope.launch {
                    _uiState.update { it.copy(isWorking = true, confirmAction = null) }
                    adminRepository.revokeRole(
                        target,
                        role,
                        state.reasonCode.ifBlank { "manual_admin" }
                    ).onSuccess {
                        selectUser(target)
                        _uiState.update {
                            it.copy(isWorking = false, message = "Rol revocado")
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(isWorking = false, message = err.message ?: "Error")
                        }
                    }
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlatformAdminViewModel() as T
                }
            }
    }
}
