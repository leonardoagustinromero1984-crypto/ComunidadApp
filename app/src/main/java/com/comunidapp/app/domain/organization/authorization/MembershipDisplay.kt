package com.comunidapp.app.domain.organization.authorization

/**
 * UI terminology. OWNER remains the internal role; never show "Owner".
 */
object MembershipDisplay {
    const val ADMINISTRATOR_VISIBLE = "Administrador"
    const val MEMBER_VISIBLE = "Miembro"

    fun visibleRole(role: OrganizationRoleCode): String = when (role) {
        OrganizationRoleCode.OWNER, OrganizationRoleCode.ADMIN -> ADMINISTRATOR_VISIBLE
        OrganizationRoleCode.MEMBER, OrganizationRoleCode.MANAGER, OrganizationRoleCode.VIEWER ->
            MEMBER_VISIBLE
    }

    fun isAdministrator(role: OrganizationRoleCode): Boolean =
        role == OrganizationRoleCode.OWNER || role == OrganizationRoleCode.ADMIN

    const val PENDING_HAS_ACCESS = false
    const val VET_MEMBERSHIP_AUTO_HEALTH_ACCESS = false
    const val HUMAN_ACTOR_ALWAYS_RECORDED = true
}
