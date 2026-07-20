package com.comunidapp.app.domain.pets

/**
 * Capacidades por defecto según rol de responsabilidad.
 * Las autorizaciones explícitas NUNCA heredan ownership ni superan este catálogo
 * (restricciones adicionales en [PetAuthorization] init).
 */
object PetRoleCapabilityMatrix {

    private val PRINCIPAL: Set<PetCapability> = setOf(
        PetCapability.READ,
        PetCapability.CREATE,
        PetCapability.UPDATE,
        PetCapability.MANAGE_RESPONSIBILITIES,
        PetCapability.MANAGE_AUTHORIZATIONS,
        PetCapability.INITIATE_TRANSFER,
        PetCapability.CANCEL_TRANSFER,
        PetCapability.MARK_DECEASED,
        PetCapability.ARCHIVE,
        PetCapability.RESTORE,
        PetCapability.MANAGE_MEDIA,
        PetCapability.VIEW_HISTORY,
        PetCapability.MANAGE_HEALTH
    )

    private val CO_RESPONSIBLE: Set<PetCapability> = setOf(
        PetCapability.READ,
        PetCapability.UPDATE,
        PetCapability.MANAGE_MEDIA,
        PetCapability.MANAGE_HEALTH,
        PetCapability.VIEW_HISTORY
    )

    private val TEMPORARY_CUSTODIAN: Set<PetCapability> = setOf(
        PetCapability.READ,
        PetCapability.UPDATE,
        PetCapability.MANAGE_HEALTH,
        PetCapability.VIEW_HISTORY
    )

    fun defaultsFor(role: PetResponsibilityRole): Set<PetCapability> = when (role) {
        PetResponsibilityRole.PRINCIPAL -> PRINCIPAL
        PetResponsibilityRole.CO_RESPONSIBLE -> CO_RESPONSIBLE
        PetResponsibilityRole.TEMPORARY_CUSTODIAN -> TEMPORARY_CUSTODIAN
    }
}

/**
 * Capacidades efectivas: roles activos ∪ autorizaciones vigentes ∪ staff M02 (PermissionCode pet.*).
 * Deny-by-default. AUTHORIZED solo obtiene lo explícito; no hereda ownership.
 */
object PetEffectiveCapabilities {

    fun forActor(
        graph: PetResponsibilityGraph,
        actorUserId: String,
        nowEpochMs: Long,
        staffPetCapabilities: Set<PetCapability> = emptySet()
    ): Set<PetCapability> {
        if (actorUserId.isBlank()) return emptySet()
        val fromRoles = graph.responsibilities
            .filter { PetResponsibilityRules.isEffectivelyActive(it, nowEpochMs) }
            .filter { matchesPerson(it.holder, actorUserId) }
            .flatMap { PetRoleCapabilityMatrix.defaultsFor(it.role) }
            .toMutableSet()

        val fromAuth = graph.authorizations
            .filter { PetAuthorizationRules.isEffectivelyActive(it, nowEpochMs) }
            .filter { it.granteeUserId == actorUserId }
            .flatMap { it.capabilities }

        fromRoles.addAll(fromAuth)
        fromRoles.addAll(staffPetCapabilities)
        return fromRoles.toSet()
    }

    fun has(
        graph: PetResponsibilityGraph,
        actorUserId: String,
        capability: PetCapability,
        nowEpochMs: Long,
        staffPetCapabilities: Set<PetCapability> = emptySet()
    ): Boolean = capability in forActor(graph, actorUserId, nowEpochMs, staffPetCapabilities)

    private fun matchesPerson(holder: PetPrincipalHolder, userId: String): Boolean =
        holder is PetPrincipalHolder.Person && holder.userId == userId
}
