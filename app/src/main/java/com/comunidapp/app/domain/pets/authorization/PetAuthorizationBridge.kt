package com.comunidapp.app.domain.pets.authorization

import com.comunidapp.app.domain.authorization.AuthorizationContext
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.pets.PetCapability
import com.comunidapp.app.domain.pets.PetEffectiveCapabilities
import com.comunidapp.app.domain.pets.PetResponsibilityGraph

/**
 * Puente M02 ↔ capacidades M08. Deny-by-default.
 * No consulta AccountType / active_modules.
 */
object PetAuthorizationBridge {

    private val STAFF_MAP: Map<PermissionCode, PetCapability> = mapOf(
        PermissionCode.PET_READ to PetCapability.READ,
        PermissionCode.PET_CREATE to PetCapability.CREATE,
        PermissionCode.PET_UPDATE to PetCapability.UPDATE,
        PermissionCode.PET_MANAGE_RESPONSIBILITIES to PetCapability.MANAGE_RESPONSIBILITIES,
        PermissionCode.PET_MANAGE_AUTHORIZATIONS to PetCapability.MANAGE_AUTHORIZATIONS,
        PermissionCode.PET_INITIATE_TRANSFER to PetCapability.INITIATE_TRANSFER,
        PermissionCode.PET_ACCEPT_TRANSFER to PetCapability.ACCEPT_TRANSFER,
        PermissionCode.PET_CANCEL_TRANSFER to PetCapability.CANCEL_TRANSFER,
        PermissionCode.PET_MARK_DECEASED to PetCapability.MARK_DECEASED,
        PermissionCode.PET_ARCHIVE to PetCapability.ARCHIVE,
        PermissionCode.PET_RESTORE to PetCapability.RESTORE,
        PermissionCode.PET_MANAGE_MEDIA to PetCapability.MANAGE_MEDIA,
        PermissionCode.PET_VIEW_HISTORY to PetCapability.VIEW_HISTORY,
        PermissionCode.PET_MANAGE_HEALTH to PetCapability.MANAGE_HEALTH
    )

    fun staffCapabilities(ctx: AuthorizationContext): Set<PetCapability> =
        ctx.permissions.mapNotNull { STAFF_MAP[it] }.toSet()

    fun decide(
        graph: PetResponsibilityGraph,
        actorUserId: String,
        required: PetCapability,
        nowEpochMs: Long,
        platform: AuthorizationContext? = null
    ): Boolean {
        val staff = platform?.let { staffCapabilities(it) }.orEmpty()
        return PetEffectiveCapabilities.has(graph, actorUserId, required, nowEpochMs, staff)
    }
}
