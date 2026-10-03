package com.comunidapp.app.data.repository

import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetTransfer
import com.comunidapp.app.domain.pets.PetTransferId
import com.comunidapp.app.domain.pets.PetTransferRepository
import com.comunidapp.app.domain.pets.PetTransferStatus
import com.comunidapp.app.domain.pets.PetTransferTargetHit
import com.comunidapp.app.domain.pets.petFailure
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
data class CanonicalCareTransferRow(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("pet_name") val petName: String? = null,
    val status: String = "PENDING",
    @SerialName("source_kind") val sourceKind: String? = null,
    @SerialName("source_person_id") val sourcePersonId: String? = null,
    @SerialName("source_organization_id") val sourceOrganizationId: String? = null,
    @SerialName("source_display_name") val sourceDisplayName: String? = null,
    @SerialName("target_kind") val targetKind: String? = null,
    @SerialName("target_person_id") val targetPersonId: String? = null,
    @SerialName("target_organization_id") val targetOrganizationId: String? = null,
    @SerialName("target_display_name") val targetDisplayName: String? = null,
    @SerialName("initiated_by_user_id") val initiatedByUserId: String? = null,
    @SerialName("decided_by_user_id") val decidedByUserId: String? = null,
    @SerialName("share_personal_media") val sharePersonalMedia: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null
)

@Serializable
data class CanonicalCareContextRow(
    @SerialName("pet_id") val petId: String,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("relation_code") val relationCode: String = "HOLDER",
    @SerialName("original_creator_person_id") val originalCreatorPersonId: String? = null,
    @SerialName("original_creator_display_name") val originalCreatorDisplayName: String? = null,
    @SerialName("principal_person_id") val principalPersonId: String? = null,
    @SerialName("principal_organization_id") val principalOrganizationId: String? = null,
    @SerialName("principal_display_name") val principalDisplayName: String? = null,
    @SerialName("current_custodian_kind") val currentCustodianKind: String? = null,
    @SerialName("can_read") val canRead: Boolean = false,
    @SerialName("can_update") val canUpdate: Boolean = false,
    @SerialName("can_manage_health") val canManageHealth: Boolean = false,
    @SerialName("can_manage_media") val canManageMedia: Boolean = false,
    @SerialName("can_manage_responsibilities") val canManageResponsibilities: Boolean = false,
    @SerialName("can_manage_authorizations") val canManageAuthorizations: Boolean = false,
    @SerialName("can_initiate_transfer") val canInitiateTransfer: Boolean = false,
    @SerialName("can_accept_transfer") val canAcceptTransfer: Boolean = false,
    @SerialName("can_cancel_transfer") val canCancelTransfer: Boolean = false,
    @SerialName("can_archive") val canArchive: Boolean = false,
    @SerialName("can_restore") val canRestore: Boolean = false,
    @SerialName("can_mark_deceased") val canMarkDeceased: Boolean = false,
    @SerialName("can_view_history") val canViewHistory: Boolean = false
)

@Serializable
private data class CanonicalCareTargetRow(
    val kind: String,
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val subtitle: String? = null
)

class CanonicalCareTransferRepository : PetTransferRepository {

    override suspend fun create(transfer: PetTransfer): Result<PetTransferId> {
        return try {
            val (kind, personId, orgId) = when (val t = transfer.toPrincipal) {
                is PetPrincipalHolder.Person -> Triple("PERSON", t.userId, null)
                is PetPrincipalHolder.Organization ->
                    Triple("ORGANIZATION", null, t.organizationId.value)
            }
            val row = rpcOne<CanonicalCareTransferRow>(
                CanonicalBackend.RPC_INITIATE_CARE_TRANSFER,
                buildJsonObject {
                    put("p_pet_id", transfer.petId.value)
                    put("p_target_kind", kind)
                    if (personId != null) put("p_target_person_id", personId) else put("p_target_person_id", JsonNull)
                    if (orgId != null) put("p_target_organization_id", orgId) else put("p_target_organization_id", JsonNull)
                    put("p_share_personal_media", transfer.sharePersonalMedia)
                }
            )
            Result.success(PetTransferId(row.id))
        } catch (e: Exception) {
            M08PetErrorMapper.failureCode(
                canonicalCareTransferErrorCode(e, missingCode = "PET_NOT_FOUND"),
                e
            )
        }
    }

    override suspend fun getPending(petId: PetId): PetTransfer? =
        listHistory(petId).firstOrNull { it.status == PetTransferStatus.PENDING }

    override suspend fun accept(transferId: PetTransferId, atEpochMs: Long): Result<Unit> {
        val result = mutate(CanonicalBackend.RPC_ACCEPT_CARE_TRANSFER, transferId)
        if (result.isSuccess) {
            val adoptions = runCatching { DataProvider.adoptionRepository }.getOrNull()
            if (adoptions is CanonicalAdoptionRepository) {
                runCatching { adoptions.refresh() }
            }
        }
        return result
    }

    override suspend fun reject(transferId: PetTransferId, atEpochMs: Long): Result<Unit> =
        mutate(CanonicalBackend.RPC_REJECT_CARE_TRANSFER, transferId)

    override suspend fun cancel(
        transferId: PetTransferId,
        atEpochMs: Long,
        reason: String?
    ): Result<Unit> = mutate(CanonicalBackend.RPC_CANCEL_CARE_TRANSFER, transferId)

    override suspend fun expire(transferId: PetTransferId, atEpochMs: Long): Result<Unit> =
        petFailure("PET_TRANSFER_EXPIRE_CLIENT_FORBIDDEN")

    override suspend fun listHistory(petId: PetId): List<PetTransfer> {
        val element: JsonElement = supabase.postgrest.rpc(
            CanonicalBackend.RPC_LIST_CARE_TRANSFERS,
            buildJsonObject { put("p_pet_id", petId.value) }
        ).decodeAs()
        return M08RpcDecoding.decodeRows<CanonicalCareTransferRow>(element).map { it.toDomain() }
    }

    override suspend fun listIncoming(): List<PetTransfer> {
        val element: JsonElement = supabase.postgrest.rpc(
            CanonicalBackend.RPC_LIST_INCOMING_CARE_TRANSFERS
        ).decodeAs()
        return M08RpcDecoding.decodeRows<CanonicalCareTransferRow>(element)
            .filter { it.status.equals("PENDING", ignoreCase = true) }
            .map { it.toDomain() }
    }

    override suspend fun searchTargets(query: String): List<PetTransferTargetHit> {
        if (query.trim().length < 2) return emptyList()
        val element: JsonElement = supabase.postgrest.rpc(
            CanonicalBackend.RPC_SEARCH_CARE_TRANSFER_TARGETS,
            buildJsonObject { put("p_query", query.trim()) }
        ).decodeAs()
        return M08RpcDecoding.decodeRows<CanonicalCareTargetRow>(element).map {
            PetTransferTargetHit(
                kind = it.kind,
                id = it.id,
                displayName = it.displayName?.trim().orEmpty().ifBlank { "Sin nombre" },
                subtitle = it.subtitle
            )
        }
    }

    private suspend fun mutate(function: String, transferId: PetTransferId): Result<Unit> {
        return try {
            rpcOne<CanonicalCareTransferRow>(
                function,
                buildJsonObject { put("p_transfer_id", transferId.value) }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            M08PetErrorMapper.failureCode(
                canonicalCareTransferErrorCode(e, missingCode = "PET_TRANSFER_NOT_FOUND"),
                e
            )
        }
    }

    private suspend inline fun <reified T : Any> rpcOne(
        function: String,
        params: kotlinx.serialization.json.JsonObject
    ): T {
        val element: JsonElement = supabase.postgrest.rpc(function, params).decodeAs()
        return M08RpcDecoding.decodeRow(element)
    }
}

/**
 * Canonical transfer RPCs use the shared `NOT_FOUND` backend code for two
 * different resources. Keep that backend contract and add operation context
 * only at the Android boundary so the UI can show the correct recovery copy.
 */
internal fun canonicalCareTransferErrorCode(
    error: Throwable,
    missingCode: String
): String {
    val mapped = M08PetErrorMapper.codeOf(error)
    if (mapped != "UNKNOWN") return mapped
    val backendText = sequenceOf(error.message, error.cause?.message)
        .filterNotNull()
        .joinToString(" ")
        .uppercase()
    return if (Regex("""(^|\W)NOT_FOUND($|\W)""").containsMatchIn(backendText)) {
        missingCode
    } else {
        mapped
    }
}

fun CanonicalCareTransferRow.toDomain(): PetTransfer {
    val from = principalOf(sourceKind, sourcePersonId, sourceOrganizationId)
    val to = principalOf(targetKind, targetPersonId, targetOrganizationId)
    val created = createdAt.toEpochMillis() ?: 0L
    return PetTransfer(
        id = PetTransferId(id),
        petId = PetId(petId),
        fromPrincipal = from,
        toPrincipal = to,
        status = runCatching { PetTransferStatus.valueOf(status) }.getOrDefault(PetTransferStatus.PENDING),
        requestedAtEpochMs = created,
        expiresAtEpochMs = created,
        requestedByUserId = initiatedByUserId.orEmpty(),
        resolvedAtEpochMs = decidedAt.toEpochMillis(),
        sharePersonalMedia = sharePersonalMedia,
        sourceDisplayName = sourceDisplayName,
        targetDisplayName = targetDisplayName,
        petDisplayName = petName
    )
}

private fun principalOf(
    kind: String?,
    personId: String?,
    organizationId: String?
): PetPrincipalHolder {
    return when {
        kind.equals("ORGANIZATION", ignoreCase = true) && !organizationId.isNullOrBlank() ->
            PetPrincipalHolder.Organization(OrganizationId(organizationId))
        !personId.isNullOrBlank() -> PetPrincipalHolder.Person(personId)
        !organizationId.isNullOrBlank() ->
            PetPrincipalHolder.Organization(OrganizationId(organizationId))
        else -> PetPrincipalHolder.Person("")
    }
}

private fun String?.toEpochMillis(): Long? =
    this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
