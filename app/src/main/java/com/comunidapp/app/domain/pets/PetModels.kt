package com.comunidapp.app.domain.pets

/**
 * Agregado de dominio M08 (capa nueva). Independiente de [com.comunidapp.app.data.model.Pet] legacy.
 * [legacyOwnerUserId] es proyección del principal persona durante la transición (Etapa 3 podrá hacerlo nullable).
 */
data class PetAggregate(
    val id: PetId,
    val displayName: String,
    val status: PetLifecycleStatus,
    val principal: PetPrincipalHolder,
    val microchipNormalized: String? = null,
    val media: PetMediaBundle = PetMediaBundle(),
    val legacyOwnerUserId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deceasedAtEpochMs: Long? = null,
    val archivedAtEpochMs: Long? = null
)

data class PetResponsibility(
    val id: PetResponsibilityId,
    val petId: PetId,
    val role: PetResponsibilityRole,
    val status: PetLinkStatus,
    val holder: PetPrincipalHolder,
    val validFromEpochMs: Long,
    val validToEpochMs: Long? = null,
    val grantedByUserId: String,
    val acceptedAtEpochMs: Long? = null,
    val revokedAtEpochMs: Long? = null,
    val revokeReason: String? = null,
    val createdAtEpochMs: Long
)

data class PetAuthorization(
    val id: PetAuthorizationId,
    val petId: PetId,
    val granteeUserId: String,
    val capabilities: Set<PetCapability>,
    val status: PetLinkStatus,
    val validFromEpochMs: Long,
    val validToEpochMs: Long? = null,
    val grantedByUserId: String,
    val acceptedAtEpochMs: Long? = null,
    val revokedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long
) {
    init {
        require(granteeUserId.isNotBlank()) { "PET_AUTH_GRANTEE_BLANK" }
        require(capabilities.isNotEmpty()) { "PET_AUTH_CAPABILITIES_EMPTY" }
        require(PetCapability.INITIATE_TRANSFER !in capabilities) { "PET_AUTH_CANNOT_GRANT_TRANSFER" }
        require(PetCapability.MARK_DECEASED !in capabilities) { "PET_AUTH_CANNOT_GRANT_DECEASED" }
        require(PetCapability.ARCHIVE !in capabilities) { "PET_AUTH_CANNOT_GRANT_ARCHIVE" }
        require(PetCapability.MANAGE_RESPONSIBILITIES !in capabilities) {
            "PET_AUTH_CANNOT_GRANT_MANAGE_RESPONSIBILITIES"
        }
    }
}

data class PetTransfer(
    val id: PetTransferId,
    val petId: PetId,
    val fromPrincipal: PetPrincipalHolder,
    val toPrincipal: PetPrincipalHolder,
    val status: PetTransferStatus,
    val requestedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val requestedByUserId: String,
    val resolvedAtEpochMs: Long? = null,
    val correlationId: String? = null
)

data class PetStatusHistoryEntry(
    val petId: PetId,
    val fromStatus: PetLifecycleStatus?,
    val toStatus: PetLifecycleStatus,
    val reasonCode: String?,
    val actorUserId: String,
    val createdAtEpochMs: Long,
    val correlationId: String? = null
)

/** Snapshot conceptual para reglas: agregado + vínculos activos relevantes. */
data class PetResponsibilityGraph(
    val pet: PetAggregate,
    val responsibilities: List<PetResponsibility>,
    val authorizations: List<PetAuthorization> = emptyList(),
    val pendingTransfer: PetTransfer? = null
)
