package com.comunidapp.app.domain.organization.authorization

import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.OrganizationStatus
import com.comunidapp.app.domain.user.AccountStatus

/**
 * Roles internos de organización (M03). Independientes de [com.comunidapp.app.domain.authorization.PlatformRoleCode].
 */
enum class OrganizationRoleCode {
    OWNER,
    ADMIN,
    MANAGER,
    MEMBER,
    VIEWER
}

enum class OrganizationPermissionCode(val code: String) {
    ORGANIZATION_VIEW("organization.view"),
    ORGANIZATION_UPDATE("organization.update"),
    ORGANIZATION_VIEW_PRIVATE("organization.view_private"),
    ORGANIZATION_MANAGE_MEMBERS("organization.manage_members"),
    ORGANIZATION_INVITE_MEMBERS("organization.invite_members"),
    ORGANIZATION_REMOVE_MEMBERS("organization.remove_members"),
    ORGANIZATION_MANAGE_ROLES("organization.manage_roles"),
    ORGANIZATION_MANAGE_BRANCHES("organization.manage_branches"),
    ORGANIZATION_PUBLISH("organization.publish"),
    ORGANIZATION_REQUEST_VERIFICATION("organization.request_verification"),
    ORGANIZATION_CLOSE("organization.close");

    companion object {
        fun fromCode(raw: String): OrganizationPermissionCode? =
            entries.firstOrNull { it.code.equals(raw.trim(), ignoreCase = true) }
    }
}

enum class OrganizationMembershipStatus {
    ACTIVE,
    INVITED,
    SUSPENDED,
    LEFT,
    REMOVED
}

data class OrganizationMembership(
    val id: String,
    val organizationId: OrganizationId,
    val userId: String,
    val role: OrganizationRoleCode,
    val status: OrganizationMembershipStatus = OrganizationMembershipStatus.ACTIVE,
    val invitedByUserId: String? = null,
    val joinedAtEpochMs: Long? = null
)

data class OrganizationAuthorizationContext(
    val userId: String,
    val organizationId: OrganizationId,
    /** Estado de cuenta plataforma (M02). */
    val accountStatus: AccountStatus,
    val organizationStatus: OrganizationStatus,
    val membership: OrganizationMembership?,
    val permissions: Set<OrganizationPermissionCode> = emptySet()
) {
    companion object {
        fun empty(userId: String, organizationId: OrganizationId) =
            OrganizationAuthorizationContext(
                userId = userId,
                organizationId = organizationId,
                accountStatus = AccountStatus.ACTIVE,
                organizationStatus = OrganizationStatus.DRAFT,
                membership = null,
                permissions = emptySet()
            )
    }
}

sealed interface OrganizationAuthorizationDecision {
    data object Allowed : OrganizationAuthorizationDecision
    data class Denied(val reason: String = "denied_by_default") : OrganizationAuthorizationDecision

    val isAllowed: Boolean get() = this is Allowed
}

/**
 * Matriz rol interno → permisos. No consulta AccountType ni active_modules ni roles M02.
 */
object OrganizationRolePermissionMatrix {

    private val VIEWER: Set<OrganizationPermissionCode> = setOf(
        OrganizationPermissionCode.ORGANIZATION_VIEW
    )

    private val MEMBER: Set<OrganizationPermissionCode> = VIEWER + setOf(
        OrganizationPermissionCode.ORGANIZATION_VIEW_PRIVATE
    )

    private val MANAGER: Set<OrganizationPermissionCode> = MEMBER + setOf(
        OrganizationPermissionCode.ORGANIZATION_UPDATE,
        OrganizationPermissionCode.ORGANIZATION_MANAGE_BRANCHES,
        OrganizationPermissionCode.ORGANIZATION_PUBLISH,
        OrganizationPermissionCode.ORGANIZATION_REQUEST_VERIFICATION
    )

    private val ADMIN: Set<OrganizationPermissionCode> = MANAGER + setOf(
        OrganizationPermissionCode.ORGANIZATION_MANAGE_MEMBERS,
        OrganizationPermissionCode.ORGANIZATION_INVITE_MEMBERS,
        OrganizationPermissionCode.ORGANIZATION_REMOVE_MEMBERS,
        OrganizationPermissionCode.ORGANIZATION_MANAGE_ROLES
    )

    private val OWNER: Set<OrganizationPermissionCode> = ADMIN + setOf(
        OrganizationPermissionCode.ORGANIZATION_CLOSE
    )

    fun permissionsFor(role: OrganizationRoleCode): Set<OrganizationPermissionCode> = when (role) {
        OrganizationRoleCode.VIEWER -> VIEWER
        OrganizationRoleCode.MEMBER -> MEMBER
        OrganizationRoleCode.MANAGER -> MANAGER
        OrganizationRoleCode.ADMIN -> ADMIN
        OrganizationRoleCode.OWNER -> OWNER
    }
}

object OrganizationAuthorizationService {

    fun decide(
        context: OrganizationAuthorizationContext,
        permission: OrganizationPermissionCode
    ): OrganizationAuthorizationDecision {
        if (context.userId.isBlank()) {
            return OrganizationAuthorizationDecision.Denied("no_user")
        }
        if (context.accountStatus == AccountStatus.SUSPENDED ||
            context.accountStatus == AccountStatus.BANNED
        ) {
            return OrganizationAuthorizationDecision.Denied("platform_account_blocked")
        }
        if (context.organizationStatus == OrganizationStatus.SUSPENDED ||
            context.organizationStatus == OrganizationStatus.CLOSED ||
            context.organizationStatus == OrganizationStatus.REJECTED
        ) {
            if (permission == OrganizationPermissionCode.ORGANIZATION_PUBLISH) {
                return OrganizationAuthorizationDecision.Denied("organization_not_publishable")
            }
            // Lectura básica podría permitirse a miembros; deny-by-default para administrar.
            if (permission != OrganizationPermissionCode.ORGANIZATION_VIEW) {
                return OrganizationAuthorizationDecision.Denied("organization_blocked")
            }
        }
        val membership = context.membership
            ?: return OrganizationAuthorizationDecision.Denied("no_membership")
        if (membership.status != OrganizationMembershipStatus.ACTIVE) {
            return OrganizationAuthorizationDecision.Denied("membership_not_active")
        }
        if (membership.userId != context.userId) {
            return OrganizationAuthorizationDecision.Denied("membership_mismatch")
        }
        val effective = context.permissions.ifEmpty {
            OrganizationRolePermissionMatrix.permissionsFor(membership.role)
        }
        return if (permission in effective) {
            OrganizationAuthorizationDecision.Allowed
        } else {
            OrganizationAuthorizationDecision.Denied("missing:${permission.code}")
        }
    }

    fun hasPermission(
        context: OrganizationAuthorizationContext,
        permission: OrganizationPermissionCode
    ): Boolean = decide(context, permission).isAllowed

    /**
     * Contención: AccountType / active_modules / roles plataforma no conceden membresía.
     */
    fun grantsFromAccountTypeOrModulesOrPlatformRoles(): Boolean = false

    fun canRemoveMember(
        actor: OrganizationAuthorizationContext,
        target: OrganizationMembership,
        ownerCount: Int
    ): OrganizationAuthorizationDecision {
        if (!hasPermission(actor, OrganizationPermissionCode.ORGANIZATION_REMOVE_MEMBERS) &&
            !hasPermission(actor, OrganizationPermissionCode.ORGANIZATION_MANAGE_ROLES)
        ) {
            return OrganizationAuthorizationDecision.Denied("missing:remove")
        }
        if (target.role == OrganizationRoleCode.OWNER && ownerCount <= 1) {
            return OrganizationAuthorizationDecision.Denied("last_owner_protected")
        }
        // ADMIN no puede remover OWNER
        if (actor.membership?.role == OrganizationRoleCode.ADMIN &&
            target.role == OrganizationRoleCode.OWNER
        ) {
            return OrganizationAuthorizationDecision.Denied("admin_cannot_remove_owner")
        }
        return OrganizationAuthorizationDecision.Allowed
    }

    fun canAssignRole(
        actor: OrganizationAuthorizationContext,
        newRole: OrganizationRoleCode,
        targetCurrentRole: OrganizationRoleCode?,
        ownerCount: Int
    ): OrganizationAuthorizationDecision {
        if (!hasPermission(actor, OrganizationPermissionCode.ORGANIZATION_MANAGE_ROLES)) {
            return OrganizationAuthorizationDecision.Denied("missing:manage_roles")
        }
        val actorRole = actor.membership?.role
            ?: return OrganizationAuthorizationDecision.Denied("no_membership")
        // Nadie se autoeleva a OWNER salvo transferencia explícita (comando aparte).
        if (newRole == OrganizationRoleCode.OWNER && actor.userId == actor.membership?.userId &&
            actorRole != OrganizationRoleCode.OWNER
        ) {
            return OrganizationAuthorizationDecision.Denied("cannot_self_elevate_to_owner")
        }
        if (actorRole == OrganizationRoleCode.ADMIN) {
            if (newRole == OrganizationRoleCode.OWNER) {
                return OrganizationAuthorizationDecision.Denied("admin_cannot_assign_owner")
            }
            if (targetCurrentRole == OrganizationRoleCode.OWNER) {
                return OrganizationAuthorizationDecision.Denied("admin_cannot_change_owner")
            }
        }
        if (targetCurrentRole == OrganizationRoleCode.OWNER &&
            newRole != OrganizationRoleCode.OWNER &&
            ownerCount <= 1
        ) {
            return OrganizationAuthorizationDecision.Denied("last_owner_protected")
        }
        return OrganizationAuthorizationDecision.Allowed
    }

    fun canPublish(context: OrganizationAuthorizationContext): Boolean {
        if (context.organizationStatus != OrganizationStatus.ACTIVE &&
            context.organizationStatus != OrganizationStatus.RESTRICTED
        ) {
            return false
        }
        return hasPermission(context, OrganizationPermissionCode.ORGANIZATION_PUBLISH)
    }
}
