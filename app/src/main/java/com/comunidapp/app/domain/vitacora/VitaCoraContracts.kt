package com.comunidapp.app.domain.vitacora

import java.time.Instant

/**
 * VitaCora is the active M14 domain. Passport is historical only.
 * Grants compose Health / Services / Moments — they do not snapshot a mega-record.
 */
enum class VitaCoraGrantScope {
    ESSENTIAL,
    HEALTH,
    ESSENTIAL_AND_HEALTH,
    FULL_SHAREABLE
}

enum class VitaCoraProposalStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CORRECTION_REQUESTED
}

enum class VitaCoraHolderKind {
    PERSON,
    ORGANIZATION
}

data class VitaCoraGrant(
    val id: String,
    val petId: String,
    val granteeKind: VitaCoraHolderKind,
    val granteePersonId: String?,
    val granteeOrganizationId: String?,
    val purpose: String,
    val scope: VitaCoraGrantScope,
    val expiresAt: Instant?,
    val revokedAt: Instant?,
    val grantedByActorUserId: String
) {
    val isIndefinite: Boolean get() = expiresAt == null
    val isActive: Boolean get() = revokedAt == null && (expiresAt == null || expiresAt.isAfter(Instant.now()))
}

data class VitaCoraMoment(
    val id: String,
    val petId: String,
    val kind: String,
    val title: String?,
    val body: String?,
    val createdByUserId: String
)

data class VitaCoraProposal(
    val id: String,
    val petId: String,
    val originKind: String,
    val status: VitaCoraProposalStatus,
    val payloadJson: String
)

interface VitaCoraRepository {
    suspend fun createMoment(petId: String, kind: String, title: String, body: String): Result<String>
    suspend fun grantAccess(
        petId: String,
        granteeKind: VitaCoraHolderKind,
        granteePersonId: String?,
        granteeOrganizationId: String?,
        purpose: String,
        scope: VitaCoraGrantScope,
        expiresAt: Instant?
    ): Result<String>
    suspend fun revokeAccess(grantId: String): Result<Unit>
    suspend fun createProposal(petId: String, originKind: String, payloadJson: String): Result<String>
    suspend fun decideProposal(proposalId: String, status: VitaCoraProposalStatus): Result<Unit>
}
