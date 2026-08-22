package com.comunidapp.app.domain.organization

import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode

data class PersonSearchHit(
    val userId: String,
    val displayName: String,
    val username: String,
    val avatarAssetId: String? = null
)

data class OrgInvitePermissionOption(
    val catalogCode: String,
    val visibleLabel: String,
    val adminOnly: Boolean = false
)

object OrgInvitePolicy {
    val MEMBER_PERMISSION_OPTIONS: List<OrgInvitePermissionOption> = listOf(
        OrgInvitePermissionOption("org.edit", "Administrar perfil"),
        OrgInvitePermissionOption("org.publish", "Publicar"),
        OrgInvitePermissionOption("org.schedule.manage", "Gestionar agenda"),
        OrgInvitePermissionOption("org.pets.manage", "Gestionar mascotas de la organización"),
        OrgInvitePermissionOption("org.pets.import", "Importar mascotas"),
        OrgInvitePermissionOption("org.reservations.manage", "Gestionar reservas"),
        OrgInvitePermissionOption("org.guests.manage", "Gestionar huéspedes")
    )

    val ADMIN_ONLY_CODES: Set<String> = setOf(
        "org.members.manage"
    )

    fun selectableFor(role: OrganizationRoleCode): List<OrgInvitePermissionOption> =
        if (role == OrganizationRoleCode.ADMIN || role == OrganizationRoleCode.OWNER) {
            emptyList()
        } else {
            MEMBER_PERMISSION_OPTIONS
        }

    fun normalizeMemberPermissions(selected: Set<String>): List<String> {
        val allowed = MEMBER_PERMISSION_OPTIONS.map { it.catalogCode }.toSet()
        return (selected.intersect(allowed) + "org.view").distinct()
    }

    fun canInviteRole(role: OrganizationRoleCode): Boolean =
        role == OrganizationRoleCode.ADMIN || role == OrganizationRoleCode.MEMBER

    const val PENDING_HAS_ACCESS = false
}

fun OrganizationPermissionCode.toCatalogCode(): String = when (this) {
    OrganizationPermissionCode.ORGANIZATION_VIEW -> "org.view"
    OrganizationPermissionCode.ORGANIZATION_UPDATE -> "org.edit"
    OrganizationPermissionCode.ORGANIZATION_MANAGE_MEMBERS,
    OrganizationPermissionCode.ORGANIZATION_INVITE_MEMBERS,
    OrganizationPermissionCode.ORGANIZATION_REMOVE_MEMBERS,
    OrganizationPermissionCode.ORGANIZATION_MANAGE_ROLES -> "org.members.manage"
    OrganizationPermissionCode.ORGANIZATION_PUBLISH,
    OrganizationPermissionCode.SOCIAL_MANAGE -> "org.publish"
    else -> code
}
