package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AdminDashboardSummary
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.data.repository.PlatformAdministrationRepository
import com.comunidapp.app.domain.authorization.AdminAccessPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminHubUiState(
    val accessChecked: Boolean = false,
    val accessAllowed: Boolean = false,
    val roleLabel: String? = null,
    val canSeeUsers: Boolean = false,
    val canSeeModeration: Boolean = false,
    val canSeeStaff: Boolean = false,
    val canSeeCatalogs: Boolean = false,
    val canSeeSupport: Boolean = false,
    val summary: AdminDashboardSummary? = null
)

class AdminHubViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val adminRepository: PlatformAdministrationRepository =
        DataProvider.platformAdministrationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminHubUiState())
    val uiState: StateFlow<AdminHubUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.value = AdminHubUiState(accessChecked = true, accessAllowed = false)
                return@launch
            }
            val ctx = try {
                permissionRepository.refresh(user.id)
            } catch (_: Exception) {
                _uiState.value = AdminHubUiState(accessChecked = true, accessAllowed = false)
                return@launch
            }
            val allowed = AdminAccessPolicy.canEnterAdministration(ctx)
            if (!allowed) {
                _uiState.value = AdminHubUiState(accessChecked = true, accessAllowed = false)
                return@launch
            }
            val canSeeUsers = AdminAccessPolicy.canSeeUsers(ctx)
            val canSeeModeration = AdminAccessPolicy.canSeeModeration(ctx)
            val canSeeStaff = AdminAccessPolicy.canSeeStaff(ctx)
            val canSeeCatalogs = AdminAccessPolicy.canSeeCatalogs(ctx)
            val summary = if (canSeeUsers || canSeeModeration || canSeeStaff || canSeeCatalogs) {
                adminRepository.dashboardSummary().getOrNull()
            } else {
                null
            }
            _uiState.update {
                AdminHubUiState(
                    accessChecked = true,
                    accessAllowed = true,
                    roleLabel = AdminAccessPolicy.displayRoleLabel(ctx.roles),
                    canSeeUsers = canSeeUsers,
                    canSeeModeration = canSeeModeration,
                    canSeeStaff = canSeeStaff,
                    canSeeCatalogs = canSeeCatalogs,
                    canSeeSupport = AdminAccessPolicy.canSeeSupport(ctx),
                    summary = summary
                )
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdminHubViewModel() as T
                }
            }
    }
}
