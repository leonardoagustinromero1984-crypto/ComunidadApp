package com.comunidapp.app.domain.organization

import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class OrganizationContextMode {
    PERSONAL,
    ORGANIZATION
}

data class OrganizationContextState(
    val mode: OrganizationContextMode = OrganizationContextMode.PERSONAL,
    val organizationId: OrganizationId? = null,
    val organizationName: String? = null,
    val organizationSlug: String? = null,
    val role: OrganizationRoleCode? = null,
    val permissions: Set<OrganizationPermissionCode> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isOrganizationMode: Boolean
        get() = mode == OrganizationContextMode.ORGANIZATION && organizationId != null

    fun hasPermission(permission: OrganizationPermissionCode): Boolean =
        permission in permissions
}

/**
 * Contexto de sesión organizacional. No reemplaza [com.comunidapp.app.domain.auth.AuthState].
 * Los permisos son caché de UI; toda acción privilegiada debe validarse en repositorio/RPC.
 */
object OrganizationContextProvider {

    private val _state = MutableStateFlow(OrganizationContextState())
    val state: StateFlow<OrganizationContextState> = _state.asStateFlow()

    suspend fun selectOrganization(
        organizationId: OrganizationId,
        organizationRepository: com.comunidapp.app.data.repository.OrganizationRepository =
            DataProvider.organizationRepository,
        permissionRepository: com.comunidapp.app.data.repository.OrganizationPermissionRepository =
            DataProvider.organizationPermissionRepository,
        membershipRepository: com.comunidapp.app.data.repository.OrganizationMembershipRepository =
            DataProvider.organizationMembershipRepository,
        userRepository: UserRepository = DataProvider.userRepository
    ) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        val authUser = AuthProvider.repository.getCurrentUser()
        if (authUser == null) {
            clear()
            return
        }
        val accountStatus = parseAccountStatus(
            userRepository.getUser(authUser.id)?.accountStatus ?: authUser.accountStatus
        )
        val org = organizationRepository.getById(organizationId)
        val membership = membershipRepository.getActiveMembership(organizationId, authUser.id)
        if (org == null || membership == null) {
            selectPersonal()
            _state.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Ya no tenés acceso a esta organización"
                )
            }
            return
        }
        val ctx = permissionRepository.getAuthorizationContext(
            organizationId = organizationId,
            userId = authUser.id,
            accountStatus = accountStatus
        )
        _state.update {
            OrganizationContextState(
                mode = OrganizationContextMode.ORGANIZATION,
                organizationId = organizationId,
                organizationName = org.publicName,
                organizationSlug = org.slug.value,
                role = membership.role,
                permissions = ctx.permissions,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    suspend fun refreshPermissions(
        permissionRepository: com.comunidapp.app.data.repository.OrganizationPermissionRepository =
            DataProvider.organizationPermissionRepository,
        membershipRepository: com.comunidapp.app.data.repository.OrganizationMembershipRepository =
            DataProvider.organizationMembershipRepository,
        userRepository: UserRepository = DataProvider.userRepository
    ) {
        val current = _state.value
        val orgId = current.organizationId ?: return
        val authUser = AuthProvider.repository.getCurrentUser() ?: run {
            clear()
            return
        }
        val membership = membershipRepository.getActiveMembership(orgId, authUser.id)
        if (membership == null) {
            selectPersonal()
            return
        }
        val accountStatus = parseAccountStatus(
            userRepository.getUser(authUser.id)?.accountStatus ?: authUser.accountStatus
        )
        val ctx = permissionRepository.getAuthorizationContext(
            organizationId = orgId,
            userId = authUser.id,
            accountStatus = accountStatus
        )
        _state.update {
            it.copy(
                role = membership.role,
                permissions = ctx.permissions,
                errorMessage = null
            )
        }
    }

    fun selectPersonal() {
        _state.value = OrganizationContextState()
    }

    fun clear() {
        _state.value = OrganizationContextState()
    }

    private fun parseAccountStatus(raw: String): AccountStatus =
        runCatching { AccountStatus.valueOf(raw.trim().uppercase()) }
            .getOrDefault(AccountStatus.ACTIVE)
}
