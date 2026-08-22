package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M28GrantProfessionalAccessInput
import com.comunidapp.app.data.model.M28GrantPurpose
import com.comunidapp.app.data.model.M28GrantStatus
import com.comunidapp.app.data.model.M28PassportUpdateProposal
import com.comunidapp.app.data.model.M28ProfessionalAccessGrant
import com.comunidapp.app.data.model.M28ProposalDecision
import com.comunidapp.app.data.model.M28ProposalStatus
import com.comunidapp.app.data.model.M28ProposalType
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.vitacora.VitaCoraGrantScope
import com.comunidapp.app.domain.vitacora.VitaCoraHolderKind
import com.comunidapp.app.domain.vitacora.VitaCoraProposalStatus
import com.comunidapp.app.domain.vitacora.VitaCoraRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
private data class CanonicalVitacoraGrantRow(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("grantee_kind") val granteeKind: String,
    @SerialName("grantee_person_id") val granteePersonId: String? = null,
    @SerialName("grantee_organization_id") val granteeOrganizationId: String? = null,
    val purpose: String,
    val scope: String,
    @SerialName("granted_at") val grantedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null
)

@Serializable
private data class CanonicalVitacoraProposalRow(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("origin_kind") val originKind: String,
    val payload: JsonElement,
    val status: String,
    @SerialName("actor_user_id") val actorUserId: String,
    @SerialName("created_at") val createdAt: String? = null
)

class CanonicalVitacoraAccessRepository(
    private val vitaCoraRepository: VitaCoraRepository = CanonicalVitaCoraRepository()
) {
    suspend fun listGrants(petId: String): Result<List<M28ProfessionalAccessGrant>> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_VITACORA_GRANTS,
            parameters = buildJsonObject { put("p_pet_id", petId) }
        ).decodeAs()
        M08RpcDecoding.decodeRows<CanonicalVitacoraGrantRow>(element).map { it.toDomain() }
    }

    suspend fun listProposals(petId: String): Result<List<M28PassportUpdateProposal>> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_VITACORA_PROPOSALS,
            parameters = buildJsonObject { put("p_pet_id", petId) }
        ).decodeAs()
        M08RpcDecoding.decodeRows<CanonicalVitacoraProposalRow>(element).map { it.toDomain() }
    }

    suspend fun grantAccess(input: M28GrantProfessionalAccessInput, actorUserId: String): Result<Unit> =
        runCatching {
            val scope = when {
                input.purposes.contains(M28GrantPurpose.PASSPORT_PROPOSAL) &&
                    input.purposes.contains(M28GrantPurpose.HISTORICAL_READ) ->
                    VitaCoraGrantScope.ESSENTIAL_AND_HEALTH
                input.purposes.contains(M28GrantPurpose.HISTORICAL_READ) ->
                    VitaCoraGrantScope.HEALTH
                else -> VitaCoraGrantScope.ESSENTIAL
            }
            vitaCoraRepository.grantAccess(
                petId = input.petId,
                granteeKind = if (input.clinicId != null) {
                    VitaCoraHolderKind.ORGANIZATION
                } else {
                    VitaCoraHolderKind.PERSON
                },
                granteePersonId = input.professionalId,
                granteeOrganizationId = input.clinicId,
                purpose = input.purposes.joinToString(",") { it.name },
                scope = scope,
                expiresAt = input.validUntilEpochMs?.let { Instant.ofEpochMilli(it) }
            ).getOrThrow()
            Unit
        }

    suspend fun revokeAccess(grantId: String): Result<Unit> =
        vitaCoraRepository.revokeAccess(grantId)

    suspend fun decideProposal(
        proposalId: String,
        decision: M28ProposalDecision
    ): Result<Unit> = runCatching {
        val status = when (decision) {
            M28ProposalDecision.ACCEPT -> VitaCoraProposalStatus.ACCEPTED
            M28ProposalDecision.REJECT -> VitaCoraProposalStatus.REJECTED
            M28ProposalDecision.CORRECTION_REQUESTED -> VitaCoraProposalStatus.CORRECTION_REQUESTED
        }
        vitaCoraRepository.decideProposal(proposalId, status).getOrThrow()
    }

    private fun CanonicalVitacoraGrantRow.toDomain(): M28ProfessionalAccessGrant {
        val revoked = revokedAt != null
        val expired = expiresAt?.let {
            runCatching { Instant.parse(it).isBefore(Instant.now()) }.getOrDefault(false)
        } ?: false
        val status = when {
            revoked -> M28GrantStatus.REVOKED
            expired -> M28GrantStatus.EXPIRED
            else -> M28GrantStatus.ACTIVE
        }
        return M28ProfessionalAccessGrant(
            id = id,
            petId = petId,
            grantedByUserId = "",
            clinicId = granteeOrganizationId,
            professionalId = granteePersonId,
            purposes = scopeToPurposes(scope),
            status = status,
            validFromEpochMs = grantedAt?.let {
                runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
            } ?: 0L,
            validUntilEpochMs = expiresAt?.let {
                runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            revokedAtEpochMs = revokedAt?.let {
                runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
            }
        )
    }

    private fun scopeToPurposes(scope: String): List<M28GrantPurpose> = when (scope.uppercase()) {
        "FULL_SHAREABLE" -> listOf(
            M28GrantPurpose.CURRENT_CARE,
            M28GrantPurpose.HISTORICAL_READ,
            M28GrantPurpose.DOCUMENTS,
            M28GrantPurpose.PASSPORT_PROPOSAL
        )
        "ESSENTIAL_AND_HEALTH", "HEALTH" -> listOf(
            M28GrantPurpose.HISTORICAL_READ,
            M28GrantPurpose.PASSPORT_PROPOSAL
        )
        else -> listOf(M28GrantPurpose.HISTORICAL_READ)
    }

    private fun CanonicalVitacoraProposalRow.toDomain(): M28PassportUpdateProposal =
        M28PassportUpdateProposal(
            id = id,
            petId = petId,
            passportId = petId,
            sourceCareId = null,
            clinicId = "",
            proposedByProfessionalId = actorUserId,
            proposalType = when (originKind.uppercase()) {
                "VET" -> M28ProposalType.HEALTH_DOCUMENT
                "WALKER", "TRAINER" -> M28ProposalType.CONTROL_EVENT
                else -> M28ProposalType.OTHER
            },
            previousValueJson = null,
            proposedValueJson = payload.toString(),
            status = when (status.uppercase()) {
                "ACCEPTED" -> M28ProposalStatus.ACCEPTED
                "REJECTED" -> M28ProposalStatus.REJECTED
                "CANCELLED" -> M28ProposalStatus.CANCELLED
                else -> M28ProposalStatus.PENDING
            },
            decisionNote = null,
            decidedBy = null,
            decidedAtEpochMs = null,
            createdAtEpochMs = createdAt?.let {
                runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
            } ?: 0L,
            professionalName = originKind.replace('_', ' ')
        )
}
