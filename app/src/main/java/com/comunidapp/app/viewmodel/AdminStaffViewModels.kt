package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AdminStaffAuditEntry
import com.comunidapp.app.data.repository.AdminStaffCreateInput
import com.comunidapp.app.data.repository.AdminStaffCredentials
import com.comunidapp.app.data.repository.AdminStaffRepository
import com.comunidapp.app.data.repository.AdminStaffSummary
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.AdminAccessPolicy
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminStaffListUiState(
    val accessChecked: Boolean = false,
    val accessAllowed: Boolean = false,
    val canManage: Boolean = false,
    val query: String = "",
    val items: List<AdminStaffSummary> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null
)

class AdminStaffListViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val staffRepository: AdminStaffRepository = DataProvider.adminStaffRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminStaffListUiState())
    val uiState: StateFlow<AdminStaffListUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun onQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.value = AdminStaffListUiState(accessChecked = true)
                return@launch
            }
            val ctx = permissionRepository.refresh(user.id)
            val allowed = AdminAccessPolicy.canSeeStaff(ctx)
            _uiState.update {
                it.copy(
                    accessChecked = true,
                    accessAllowed = allowed,
                    canManage = AdminAccessPolicy.canManageStaff(ctx),
                    loading = allowed
                )
            }
            if (!allowed) return@launch
            val result = staffRepository.list(_uiState.value.query)
            _uiState.update {
                it.copy(
                    loading = false,
                    items = result.getOrDefault(emptyList()),
                    message = result.exceptionOrNull()?.message
                )
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AdminStaffListViewModel() as T
        }
    }
}

data class AdminStaffCreateUiState(
    val accessChecked: Boolean = false,
    val accessAllowed: Boolean = false,
    val displayName: String = "",
    val username: String = "",
    val role: PlatformRoleCode = PlatformRoleCode.MODERATOR,
    val active: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val credentials: AdminStaffCredentials? = null
)

class AdminStaffCreateViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val staffRepository: AdminStaffRepository = DataProvider.adminStaffRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminStaffCreateUiState())
    val uiState: StateFlow<AdminStaffCreateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.update { it.copy(accessChecked = true) }
                return@launch
            }
            val ctx = permissionRepository.refresh(user.id)
            _uiState.update {
                it.copy(
                    accessChecked = true,
                    accessAllowed = AdminAccessPolicy.canManageStaff(ctx)
                )
            }
        }
    }

    fun onDisplayName(value: String) = _uiState.update { it.copy(displayName = value, error = null) }
    fun onUsername(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun onRole(value: PlatformRoleCode) = _uiState.update { it.copy(role = value, error = null) }
    fun onActive(value: Boolean) = _uiState.update { it.copy(active = value) }

    fun submit() {
        val state = _uiState.value
        if (state.saving) return
        if (state.displayName.trim().length < 2) {
            _uiState.update { it.copy(error = "El nombre es obligatorio") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            val result = staffRepository.create(
                AdminStaffCreateInput(
                    displayName = state.displayName,
                    username = state.username,
                    role = state.role,
                    active = state.active
                )
            )
            result.fold(
                onSuccess = { creds ->
                    _uiState.update { it.copy(saving = false, credentials = creds) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(saving = false, error = err.message ?: "No se pudo crear la cuenta.")
                    }
                }
            )
        }
    }

    fun consumeCredentials() {
        _uiState.update { it.copy(credentials = null) }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AdminStaffCreateViewModel() as T
        }
    }
}

data class AdminStaffDetailUiState(
    val accessChecked: Boolean = false,
    val accessAllowed: Boolean = false,
    val canManage: Boolean = false,
    val item: AdminStaffSummary? = null,
    val audit: List<AdminStaffAuditEntry> = emptyList(),
    val pendingRole: PlatformRoleCode? = null,
    val confirmDisable: Boolean = false,
    val confirmEnable: Boolean = false,
    val confirmReset: Boolean = false,
    val confirmForceChange: Boolean = false,
    val credentials: AdminStaffCredentials? = null,
    val message: String? = null,
    val loading: Boolean = false
)

class AdminStaffDetailViewModel(
    private val userId: String,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val staffRepository: AdminStaffRepository = DataProvider.adminStaffRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminStaffDetailUiState())
    val uiState: StateFlow<AdminStaffDetailUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.value = AdminStaffDetailUiState(accessChecked = true)
                return@launch
            }
            val ctx = permissionRepository.refresh(user.id)
            val allowed = AdminAccessPolicy.canSeeStaff(ctx)
            _uiState.update {
                it.copy(
                    accessChecked = true,
                    accessAllowed = allowed,
                    canManage = AdminAccessPolicy.canManageStaff(ctx),
                    loading = allowed
                )
            }
            if (!allowed) return@launch
            val item = staffRepository.get(userId).getOrNull()
            val audit = staffRepository.listAudit(userId).getOrDefault(emptyList())
            _uiState.update { it.copy(loading = false, item = item, audit = audit) }
        }
    }

    fun requestRole(role: PlatformRoleCode) = _uiState.update { it.copy(pendingRole = role) }
    fun requestDisable() = _uiState.update { it.copy(confirmDisable = true) }
    fun requestEnable() = _uiState.update { it.copy(confirmEnable = true) }
    fun requestReset() = _uiState.update { it.copy(confirmReset = true) }
    fun requestForceChange() = _uiState.update { it.copy(confirmForceChange = true) }
    fun cancelConfirm() = _uiState.update {
        it.copy(
            pendingRole = null,
            confirmDisable = false,
            confirmEnable = false,
            confirmReset = false,
            confirmForceChange = false
        )
    }

    fun confirmRole() {
        val role = _uiState.value.pendingRole ?: return
        viewModelScope.launch {
            staffRepository.setRole(userId, role).fold(
                onSuccess = {
                    _uiState.update { it.copy(pendingRole = null, message = "Rol actualizado") }
                    refresh()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(pendingRole = null, message = err.message) }
                }
            )
        }
    }

    fun confirmDisable() {
        viewModelScope.launch {
            staffRepository.setDisabled(userId, true).fold(
                onSuccess = {
                    _uiState.update { it.copy(confirmDisable = false, message = "Cuenta desactivada") }
                    refresh()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(confirmDisable = false, message = err.message) }
                }
            )
        }
    }

    fun confirmEnable() {
        viewModelScope.launch {
            staffRepository.setDisabled(userId, false).fold(
                onSuccess = {
                    _uiState.update { it.copy(confirmEnable = false, message = "Cuenta reactivada") }
                    refresh()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(confirmEnable = false, message = err.message) }
                }
            )
        }
    }

    fun confirmReset() {
        viewModelScope.launch {
            staffRepository.resetPassword(userId).fold(
                onSuccess = { creds ->
                    _uiState.update { it.copy(confirmReset = false, credentials = creds) }
                    refresh()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(confirmReset = false, message = err.message) }
                }
            )
        }
    }

    fun confirmForceChange() {
        viewModelScope.launch {
            staffRepository.forcePasswordChange(userId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(confirmForceChange = false, message = "Deberá cambiar la contraseña")
                    }
                    refresh()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(confirmForceChange = false, message = err.message) }
                }
            )
        }
    }

    fun consumeCredentials() = _uiState.update { it.copy(credentials = null) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(userId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AdminStaffDetailViewModel(userId) as T
        }
    }
}
