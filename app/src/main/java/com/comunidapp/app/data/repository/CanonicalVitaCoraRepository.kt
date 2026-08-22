package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.vitacora.VitaCoraGrantScope
import com.comunidapp.app.domain.vitacora.VitaCoraHolderKind
import com.comunidapp.app.domain.vitacora.VitaCoraProposalStatus
import com.comunidapp.app.domain.vitacora.VitaCoraRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class CanonicalVitaCoraRepository : VitaCoraRepository {

    override suspend fun createMoment(
        petId: String,
        kind: String,
        title: String,
        body: String
    ): Result<String> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CREATE_MOMENT,
            parameters = buildJsonObject {
                put("p_pet_id", petId)
                put("p_kind", kind)
                put("p_title", title)
                put("p_body", body)
            }
        ).decodeAs<String>()
    }

    override suspend fun grantAccess(
        petId: String,
        granteeKind: VitaCoraHolderKind,
        granteePersonId: String?,
        granteeOrganizationId: String?,
        purpose: String,
        scope: VitaCoraGrantScope,
        expiresAt: Instant?
    ): Result<String> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_GRANT_VITACORA,
            parameters = buildJsonObject {
                put("p_pet_id", petId)
                put("p_kind", granteeKind.name)
                if (granteePersonId != null) put("p_person", granteePersonId) else put("p_person", JsonNull)
                if (granteeOrganizationId != null) put("p_org", granteeOrganizationId) else put("p_org", JsonNull)
                put("p_purpose", purpose)
                put("p_scope", scope.name)
                if (expiresAt != null) put("p_expires", expiresAt.toString()) else put("p_expires", JsonNull)
            }
        ).decodeAs<String>()
    }

    override suspend fun revokeAccess(grantId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_REVOKE_VITACORA,
            parameters = buildJsonObject { put("p_grant_id", grantId) }
        )
        Unit
    }

    override suspend fun createProposal(
        petId: String,
        originKind: String,
        payloadJson: String
    ): Result<String> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CREATE_PROPOSAL,
            parameters = buildJsonObject {
                put("p_pet_id", petId)
                put("p_origin", originKind)
                put("p_payload", payloadJson)
            }
        ).decodeAs<String>()
    }

    override suspend fun decideProposal(
        proposalId: String,
        status: VitaCoraProposalStatus
    ): Result<Unit> {
        if (status == VitaCoraProposalStatus.PENDING) {
            return Result.failure(IllegalArgumentException("INVALID_STATUS"))
        }
        return runCatching {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_DECIDE_PROPOSAL,
                parameters = buildJsonObject {
                    put("p_proposal_id", proposalId)
                    put("p_status", status.name)
                }
            )
            Unit
        }
    }
}
