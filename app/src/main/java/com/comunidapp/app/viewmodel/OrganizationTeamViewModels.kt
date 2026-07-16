package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.OrganizationInvitationRepository
import com.comunidapp.app.data.repository.OrganizationMembershipRepository
import com.comunidapp.app.data.repository.OrganizationPermissionRepository
import com.comunidapp.app.data.repository.OrganizationRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.organization.CreateOrganizationBranchCommand
import com.comunidapp.app.domain.organization.CreateOrganizationInvitationCommand
import com.comunidapp.app.domain.organization.Organization
import com.comunidapp.app.domain.organization.OrganizationBranch
import com.comunidapp.app.domain.organization.OrganizationBranchStatus
import com.comunidapp.app.domain.organization.OrganizationContextProvider
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.OrganizationInvitation
import com.comunidapp.app.domain.organization.OrganizationInvitationRules
import com.comunidapp.app.domain.organization.OrganizationInvitationToken
import com.comunidapp.app.domain.organization.UpdateOrganizationBranchCommand
import com.comunidapp.app.domain.organization.authorization.OrganizationAuthorizationService
import com.comunidapp.app.domain.organization.authorization.OrganizationMembership
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class OrganizationManageUiState(
    val isLoading: Boolean = true,
    val organization: Organization? = null,
    val canManageMembers: Boolean = false,
    val canInvite: Boolean = false,
    val canManageBranches: Boolean = false,
    val canClose: Boolean = false,
    val canLeave: Boolean = false,
    val isOwner: Boolean = false,
    val errorMessage: String? = null
)

class OrganizationManageViewModel(
    private val organizationId: String,
    private val organizationRepository: OrganizationRepository = DataProvider.organizationRepository,
    private val permissionRepository: OrganizationPermissionRepository =
        DataProvider.organizationPermissionRepository,
    private val membershipRepository: OrganizationMembershipRepository =
        DataProvider.organizationMembershipRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizationManageUiState())
    val uiState: StateFlow<OrganizationManageUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "No hay sesión activa")
                }
                return@launch
            }
            val orgId = OrganizationId(organizationId)
            val accountStatus = parseAccountStatus(
                userRepository.getUser(authUser.id)?.accountStatus ?: authUser.accountStatus
            )
            val org = organizationRepository.getById(orgId)
            if (org == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Organización no encontrada")
                }
                return@launch
            }
            val membership = membershipRepository.getActiveMembership(orgId, authUser.id)
            if (membership == null) {
                OrganizationContextProvider.selectPersonal()
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "No tenés acceso a esta organización")
                }
                return@launch
            }
            OrganizationContextProvider.selectOrganization(orgId)
            val ctx = permissionRepository.getAuthorizationContext(orgId, authUser.id, accountStatus)
            _uiState.update {
                OrganizationManageUiState(
                    isLoading = false,
                    organization = org,
                    canManageMembers = OrganizationAuthorizationService.hasPermission(
                        ctx,
                        OrganizationPermissionCode.ORGANIZATION_MANAGE_MEMBERS
                    ),
                    canInvite = OrganizationAuthorizationService.hasPermission(
                        ctx,
                        OrganizationPermissionCode.ORGANIZATION_INVITE_MEMBERS
                    ),
                    canManageBranches = OrganizationAuthorizationService.hasPermission(
                        ctx,
                        OrganizationPermissionCode.ORGANIZATION_MANAGE_BRANCHES
                    ),
                    canClose = OrganizationAuthorizationService.hasPermission(
                        ctx,
                        OrganizationPermissionCode.ORGANIZATION_CLOSE
                    ),
                    canLeave = membership.role != OrganizationRoleCode.OWNER ||
                        membershipRepository.countActiveOwners(orgId) > 1,
                    isOwner = membership.role == OrganizationRoleCode.OWNER
                )
            }
        }
    }

    fun activateContext() {
        viewModelScope.launch {
            OrganizationContextProvider.selectOrganization(OrganizationId(organizationId))
        }
    }

    fun usePersonalContext() {
        OrganizationContextProvider.selectPersonal()
    }

    private fun parseAccountStatus(raw: String): AccountStatus =
        runCatching { AccountStatus.valueOf(raw.trim().uppercase()) }
            .getOrDefault(AccountStatus.ACTIVE)

    companion object {
        fun factory(organizationId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OrganizationManageViewModel(organizationId) as T
                }
            }
    }
}

data class OrganizationTeamUiState(
    val isLoading: Boolean = true,
    val members: List<OrganizationMembership> = emptyList(),
    val invitations: List<OrganizationInvitation> = emptyList(),
    val canManageMembers: Boolean = false,
    val canInvite: Boolean = false,
    val canManageRoles: Boolean = false,
    val canRemove: Boolean = false,
    val canTransferOwnership: Boolean = false,
    val ownerCount: Int = 0,
    val inviteEmail: String = "",
    val inviteRole: OrganizationRoleCode = OrganizationRoleCode.MEMBER,
    val isInviting: Boolean = false,
    val lastInviteTokenHint: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class OrganizationTeamViewModel(
    private val organizationId: String,
    private val organizationRepository: OrganizationRepository = DataProvider.organizationRepository,
    private val membershipRepository: OrganizationMembershipRepository =
        DataProvider.organizationMembershipRepository,
    private val invitationRepository: OrganizationInvitationRepository =
        DataProvider.organizationInvitationRepository,
    private val permissionRepository: OrganizationPermissionRepository =
        DataProvider.organizationPermissionRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizationTeamUiState())
    val uiState: StateFlow<OrganizationTeamUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            val authUser = authRepository.getCurrentUser() ?: run {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No hay sesión") }
                return@launch
            }
            val orgId = OrganizationId(organizationId)
            val accountStatus = parseAccountStatus(
                userRepository.getUser(authUser.id)?.accountStatus ?: authUser.accountStatus
            )
            val ctx = permissionRepository.getAuthorizationContext(orgId, authUser.id, accountStatus)
            val canManage = OrganizationAuthorizationService.hasPermission(
                ctx,
                OrganizationPermissionCode.ORGANIZATION_MANAGE_MEMBERS
            )
            val members = if (canManage) {
                membershipRepository.listMembers(orgId).getOrElse { emptyList() }
            } else {
                emptyList()
            }
            val invitations = if (canManage) {
                invitationRepository.listByOrganization(orgId).getOrElse { emptyList() }
            } else {
                emptyList()
            }
            val owners = membershipRepository.countActiveOwners(orgId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    members = members,
                    invitations = invitations,
                    canManageMembers = canManage,
                    canInvite = OrganizationAuthorizationService.hasPermission(
                        ctx,
                        OrganizationPermissionCode.ORGANIZATION_INVITE_MEMBERS
                    ),
                    canManageRoles = OrganizationAuthorizationService.hasPermission(
                        ctx,
                        OrganizationPermissionCode.ORGANIZATION_MANAGE_ROLES
                    ),
                    canRemove = OrganizationAuthorizationService.hasPermission(
                        ctx,
                        OrganizationPermissionCode.ORGANIZATION_REMOVE_MEMBERS
                    ),
                    canTransferOwnership = ctx.membership?.role == OrganizationRoleCode.OWNER,
                    ownerCount = owners
                )
            }
            OrganizationContextProvider.refreshPermissions()
        }
    }

    fun onInviteEmailChange(value: String) =
        _uiState.update { it.copy(inviteEmail = value, errorMessage = null) }

    fun onInviteRoleChange(value: OrganizationRoleCode) =
        _uiState.update { it.copy(inviteRole = value, errorMessage = null) }

    fun inviteMember() {
        val state = _uiState.value
        if (!state.canInvite || state.isInviting) return
        val email = state.inviteEmail.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingresá un email") }
            return
        }
        if (!OrganizationInvitationRules.canInviteRole(state.inviteRole)) {
            _uiState.update { it.copy(errorMessage = "No se puede invitar a ese rol") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isInviting = true, errorMessage = null) }
            val authUser = authRepository.getCurrentUser() ?: return@launch
            val token = OrganizationInvitationToken.fromSecureRandom(
                UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString()
            )
            invitationRepository.create(
                CreateOrganizationInvitationCommand(
                    organizationId = OrganizationId(organizationId),
                    invitedRole = state.inviteRole,
                    invitedByUserId = authUser.id,
                    targetEmailHint = email,
                    expiresAtEpochMs = System.currentTimeMillis() + 7L * 86_400_000L,
                    token = token
                )
            ).onSuccess { invitation ->
                _uiState.update {
                    it.copy(
                        isInviting = false,
                        inviteEmail = "",
                        successMessage = "Invitación enviada",
                        lastInviteTokenHint = invitation.token?.fingerprint()
                    )
                }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isInviting = false,
                        errorMessage = error.message ?: "No se pudo invitar"
                    )
                }
            }
        }
    }

    fun revokeInvitation(invitationId: String) {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser() ?: return@launch
            invitationRepository.revoke(invitationId, authUser.id, System.currentTimeMillis())
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "No se pudo revocar")
                    }
                }
        }
    }

    fun changeRole(targetUserId: String, newRole: OrganizationRoleCode) {
        viewModelScope.launch {
            membershipRepository.changeMemberRole(
                OrganizationId(organizationId),
                targetUserId,
                newRole
            ).onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "No se pudo cambiar el rol")
                    }
                }
        }
    }

    fun suspendMember(targetUserId: String) {
        viewModelScope.launch {
            membershipRepository.suspendMember(OrganizationId(organizationId), targetUserId)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "No se pudo suspender")
                    }
                }
        }
    }

    fun removeMember(targetUserId: String) {
        viewModelScope.launch {
            membershipRepository.removeMember(OrganizationId(organizationId), targetUserId)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "No se pudo remover")
                    }
                }
        }
    }

    fun transferOwnership(targetUserId: String) {
        viewModelScope.launch {
            membershipRepository.transferOwnership(OrganizationId(organizationId), targetUserId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Ownership transferido") }
                    refresh()
                    OrganizationContextProvider.refreshPermissions()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "No se pudo transferir")
                    }
                }
        }
    }

    fun leaveOrganization(onLeft: () -> Unit) {
        viewModelScope.launch {
            membershipRepository.leaveOrganization(OrganizationId(organizationId))
                .onSuccess {
                    OrganizationContextProvider.selectPersonal()
                    onLeft()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "No se pudo salir")
                    }
                }
        }
    }

    fun closeOrganization(onClosed: () -> Unit) {
        viewModelScope.launch {
            organizationRepository.closeOrganization(OrganizationId(organizationId))
                .onSuccess {
                    OrganizationContextProvider.selectPersonal()
                    onClosed()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "No se pudo cerrar")
                    }
                }
        }
    }

    private fun parseAccountStatus(raw: String): AccountStatus =
        runCatching { AccountStatus.valueOf(raw.trim().uppercase()) }
            .getOrDefault(AccountStatus.ACTIVE)

    companion object {
        fun factory(organizationId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OrganizationTeamViewModel(organizationId) as T
                }
            }
    }
}

data class OrganizationBranchesUiState(
    val isLoading: Boolean = true,
    val branches: List<OrganizationBranch> = emptyList(),
    val canManage: Boolean = false,
    val name: String = "",
    val addressLine: String = "",
    val city: String = "",
    val phone: String = "",
    val phonePublic: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class OrganizationBranchesViewModel(
    private val organizationId: String,
    private val organizationRepository: OrganizationRepository = DataProvider.organizationRepository,
    private val permissionRepository: OrganizationPermissionRepository =
        DataProvider.organizationPermissionRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizationBranchesUiState())
    val uiState: StateFlow<OrganizationBranchesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val authUser = authRepository.getCurrentUser() ?: run {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No hay sesión") }
                return@launch
            }
            val orgId = OrganizationId(organizationId)
            val accountStatus = parseAccountStatus(
                userRepository.getUser(authUser.id)?.accountStatus ?: authUser.accountStatus
            )
            val canManage = permissionRepository.hasPermission(
                orgId,
                authUser.id,
                accountStatus,
                OrganizationPermissionCode.ORGANIZATION_MANAGE_BRANCHES
            )
            organizationRepository.listBranches(orgId, includePrivate = canManage)
                .onSuccess { branches ->
                    _uiState.update {
                        it.copy(isLoading = false, branches = branches, canManage = canManage)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            canManage = false,
                            errorMessage = error.message ?: "No se pudieron cargar sucursales"
                        )
                    }
                }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }
    fun onAddressChange(value: String) = _uiState.update { it.copy(addressLine = value) }
    fun onCityChange(value: String) = _uiState.update { it.copy(city = value) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value) }
    fun onPhonePublicChange(value: Boolean) = _uiState.update { it.copy(phonePublic = value) }

    fun createBranch() {
        val state = _uiState.value
        if (!state.canManage || state.isSaving) return
        if (state.name.trim().length < 2) {
            _uiState.update { it.copy(errorMessage = "Nombre demasiado corto") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            organizationRepository.createBranch(
                CreateOrganizationBranchCommand(
                    organizationId = OrganizationId(organizationId),
                    name = state.name,
                    addressLine = state.addressLine.ifBlank { null },
                    city = state.city.ifBlank { null },
                    phone = state.phone.ifBlank { null },
                    phonePublic = state.phonePublic
                )
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        name = "",
                        addressLine = "",
                        city = "",
                        phone = "",
                        phonePublic = false,
                        successMessage = "Sucursal creada"
                    )
                }
                refresh()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = error.message ?: "Error al crear")
                }
            }
        }
    }

    fun closeBranch(branchId: String) {
        viewModelScope.launch {
            organizationRepository.setBranchStatus(branchId, OrganizationBranchStatus.CLOSED)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "No se pudo cerrar la sucursal")
                    }
                }
        }
    }

    private fun parseAccountStatus(raw: String): AccountStatus =
        runCatching { AccountStatus.valueOf(raw.trim().uppercase()) }
            .getOrDefault(AccountStatus.ACTIVE)

    companion object {
        fun factory(organizationId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OrganizationBranchesViewModel(organizationId) as T
                }
            }
    }
}
