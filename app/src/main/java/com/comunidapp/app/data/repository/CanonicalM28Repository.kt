package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M28GrantProfessionalAccessInput
import com.comunidapp.app.data.model.M28PassportUpdateProposal
import com.comunidapp.app.data.model.M28ProfessionalAccessGrant
import com.comunidapp.app.data.model.VitacoraAccessTarget
import com.comunidapp.app.data.model.M28ProposalDecision
import com.comunidapp.app.data.model.M28ProposalStatus
import com.comunidapp.app.data.model.M28ProposalType

/**
 * Canonical STAGING owner-facing M28 paths backed by VitaCora RPCs.
 * Clinic care flows still delegate to the in-memory mock.
 */
class CanonicalM28Repository(
    private val access: CanonicalVitacoraAccessRepository = CanonicalVitacoraAccessRepository(),
    private val delegate: M28Repository
) : M28Repository by delegate {

    override suspend fun grantAccess(input: M28GrantProfessionalAccessInput): Result<M28ProfessionalAccessGrant> {
        val actor = com.comunidapp.app.data.repository.AuthProvider.repository.getCurrentUser()?.id
            ?: return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
        return access.grantAccess(input, actor).map {
            M28ProfessionalAccessGrant(
                id = "pending",
                petId = input.petId,
                grantedByUserId = actor,
                clinicId = input.clinicId,
                professionalId = input.professionalId,
                purposes = input.purposes,
                status = com.comunidapp.app.data.model.M28GrantStatus.ACTIVE,
                validFromEpochMs = System.currentTimeMillis(),
                validUntilEpochMs = input.validUntilEpochMs,
                revokedAtEpochMs = null
            )
        }
    }

    override suspend fun revokeAccess(grantId: String): Result<M28ProfessionalAccessGrant> =
        access.revokeAccess(grantId).map {
            M28ProfessionalAccessGrant(
                id = grantId,
                petId = "",
                grantedByUserId = "",
                clinicId = null,
                professionalId = null,
                purposes = emptyList(),
                status = com.comunidapp.app.data.model.M28GrantStatus.REVOKED,
                validFromEpochMs = 0L,
                validUntilEpochMs = null,
                revokedAtEpochMs = System.currentTimeMillis()
            )
        }

    override suspend fun listGrantsForResponsible(petId: String): Result<List<M28ProfessionalAccessGrant>> =
        access.listGrants(petId)

    override suspend fun listProposalsForResponsible(
        petId: String,
        actorUserId: String
    ): Result<List<M28PassportUpdateProposal>> = access.listProposals(petId)

    override suspend fun decideProposal(
        proposalId: String,
        decision: M28ProposalDecision,
        note: String?,
        actorUserId: String
    ): Result<M28PassportUpdateProposal> =
        access.decideProposal(proposalId, decision).map {
            M28PassportUpdateProposal(
                id = proposalId,
                petId = "",
                passportId = "",
                sourceCareId = null,
                clinicId = "",
                proposedByProfessionalId = null,
                proposalType = M28ProposalType.OTHER,
                previousValueJson = null,
                proposedValueJson = note.orEmpty(),
                status = when (decision) {
                    M28ProposalDecision.ACCEPT -> M28ProposalStatus.ACCEPTED
                    M28ProposalDecision.REJECT -> M28ProposalStatus.REJECTED
                    M28ProposalDecision.CORRECTION_REQUESTED -> M28ProposalStatus.PENDING
                },
                decisionNote = note,
                decidedBy = actorUserId,
                decidedAtEpochMs = System.currentTimeMillis(),
                createdAtEpochMs = 0L
            )
        }

    override suspend fun searchAccessTargets(query: String) = access.searchAccessTargets(query)
}
