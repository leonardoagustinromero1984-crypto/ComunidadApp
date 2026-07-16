package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.organization.CreateOrganizationInvitationCommand
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.OrganizationInvitation
import com.comunidapp.app.domain.organization.OrganizationInvitationRules
import com.comunidapp.app.domain.organization.OrganizationInvitationStatus
import com.comunidapp.app.domain.organization.OrganizationInvitationToken
import com.comunidapp.app.domain.organization.authorization.OrganizationMembership
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
data class OrganizationInvitationRow(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("invited_email") val invitedEmail: String? = null,
    @SerialName("invited_user_id") val invitedUserId: String? = null,
    @SerialName("role_code") val roleCode: String,
    val status: String = "PENDING",
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("accepted_by") val acceptedBy: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("revoked_by") val revokedBy: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

object OrganizationInvitationRowMapper {

    fun toDomain(row: OrganizationInvitationRow, token: OrganizationInvitationToken? = null): OrganizationInvitation =
        OrganizationInvitation(
            id = row.id,
            organizationId = OrganizationId(row.organizationId),
            invitedRole = runCatching {
                OrganizationRoleCode.valueOf(row.roleCode.trim().uppercase())
            }.getOrDefault(OrganizationRoleCode.MEMBER),
            status = runCatching {
                OrganizationInvitationStatus.valueOf(row.status.trim().uppercase())
            }.getOrDefault(OrganizationInvitationStatus.PENDING),
            invitedByUserId = row.createdBy,
            targetUserId = row.invitedUserId,
            targetEmailHint = row.invitedEmail,
            expiresAtEpochMs = row.expiresAt.toEpochMillis() ?: 0L,
            acceptedAtEpochMs = row.acceptedAt.toEpochMillis(),
            revokedAtEpochMs = row.revokedAt.toEpochMillis(),
            token = token
        )

    private fun String?.toEpochMillis(): Long? =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}

class SupabaseOrganizationInvitationRepository(
    private val membershipRepository: OrganizationMembershipRepository =
        SupabaseOrganizationMembershipRepository()
) : OrganizationInvitationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(
        command: CreateOrganizationInvitationCommand
    ): Result<OrganizationInvitation> {
        return try {
            val element = supabase.postgrest.rpc(
                function = "invite_organization_member",
                parameters = buildJsonObject {
                    put("p_organization_id", command.organizationId.value)
                    command.targetEmailHint?.let { put("p_invited_email", it) }
                    command.targetUserId?.let { put("p_invited_user_id", it) }
                    put("p_role_code", command.invitedRole.name)
                    put(
                        "p_expires_at",
                        Instant.ofEpochMilli(command.expiresAtEpochMs).toString()
                    )
                }
            ).decodeAs<JsonElement>()

            val obj = element as? JsonObject
                ?: return Result.failure(IllegalStateException("INVITE_EMPTY"))
            val invitationJson = obj["invitation"]
                ?: return Result.failure(IllegalStateException("INVITE_MISSING_BODY"))
            val row = json.decodeFromJsonElement(OrganizationInvitationRow.serializer(), invitationJson)
            val tokenRaw = obj["token"]?.jsonPrimitive?.content
            val token = tokenRaw?.let { OrganizationInvitationToken.fromSecureRandom(it) }
            Result.success(OrganizationInvitationRowMapper.toDomain(row, token))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getById(id: String): OrganizationInvitation? {
        return try {
            supabase.from("organization_invitations")
                .select {
                    filter { eq("id", id) }
                }
                .decodeList<OrganizationInvitationRow>()
                .firstOrNull()
                ?.let { OrganizationInvitationRowMapper.toDomain(it) }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun accept(
        invitationId: String,
        actorUserId: String,
        token: OrganizationInvitationToken?,
        nowEpochMs: Long
    ): Result<OrganizationInvitation> {
        val rawToken = token?.rawToken
            ?: return Result.failure(IllegalStateException("TOKEN_REQUIRED"))
        return try {
            acceptByToken(token)
            val row = getById(invitationId)
                ?: return Result.failure(IllegalStateException("NOT_FOUND"))
            Result.success(row)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun revoke(
        invitationId: String,
        actorUserId: String,
        nowEpochMs: Long
    ): Result<OrganizationInvitation> {
        return try {
            val row = supabase.postgrest.rpc(
                function = "revoke_organization_invitation",
                parameters = buildJsonObject {
                    put("p_invitation_id", invitationId)
                }
            ).decodeList<OrganizationInvitationRow>().firstOrNull()
                ?: return Result.failure(IllegalStateException("REVOKE_EMPTY"))
            Result.success(OrganizationInvitationRowMapper.toDomain(row))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listByOrganization(
        organizationId: OrganizationId
    ): Result<List<OrganizationInvitation>> {
        return try {
            val rows = supabase.postgrest.rpc(
                function = "list_organization_invitations",
                parameters = buildJsonObject {
                    put("p_organization_id", organizationId.value)
                }
            ).decodeList<OrganizationInvitationRow>()
            Result.success(rows.map { OrganizationInvitationRowMapper.toDomain(it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listMyPending(): Result<List<OrganizationInvitation>> {
        return try {
            val rows = supabase.postgrest.rpc(function = "list_my_pending_invitations")
                .decodeList<OrganizationInvitationRow>()
            Result.success(rows.map { OrganizationInvitationRowMapper.toDomain(it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptByToken(
        token: OrganizationInvitationToken
    ): Result<OrganizationMembership> {
        return try {
            val row = supabase.postgrest.rpc(
                function = "accept_organization_invitation",
                parameters = buildJsonObject {
                    put("p_token", token.rawToken)
                }
            ).decodeList<OrganizationMembershipRow>().firstOrNull()
                ?: return Result.failure(IllegalStateException("ACCEPT_EMPTY"))
            Result.success(OrganizationRowMapper.toMembership(row))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun declineByToken(token: OrganizationInvitationToken): Result<Unit> {
        return try {
            supabase.postgrest.rpc(
                function = "decline_organization_invitation",
                parameters = buildJsonObject {
                    put("p_token", token.rawToken)
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
