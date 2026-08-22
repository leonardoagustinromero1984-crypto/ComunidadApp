package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.organization.CreateOrganizationInvitationCommand
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.OrganizationInvitation
import com.comunidapp.app.domain.organization.OrganizationInvitationStatus
import com.comunidapp.app.domain.organization.OrganizationInvitationToken
import com.comunidapp.app.domain.organization.OrgInvitePolicy
import com.comunidapp.app.domain.organization.PersonSearchHit
import com.comunidapp.app.domain.organization.authorization.OrganizationMembership
import com.comunidapp.app.domain.organization.authorization.OrganizationMembershipStatus
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
private data class CanonicalPersonSearchRow(
    @SerialName("user_id") val userId: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_asset_id") val avatarAssetId: String? = null
)

@Serializable
private data class CanonicalOrgInvitationRow(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("organization_name") val organizationName: String? = null,
    @SerialName("organization_capability") val organizationCapability: String? = null,
    @SerialName("invitee_user_id") val inviteeUserId: String? = null,
    @SerialName("invitee_name") val inviteeName: String? = null,
    @SerialName("invitee_username") val inviteeUsername: String? = null,
    @SerialName("role_code") val roleCode: String? = null,
    val status: String = "PENDING",
    @SerialName("permission_codes") val permissionCodes: List<String> = emptyList(),
    @SerialName("invited_by") val invitedBy: String? = null,
    @SerialName("inviter_name") val inviterName: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

class CanonicalOrganizationInvitationRepository : OrganizationInvitationRepository {

    suspend fun searchPersons(query: String): List<PersonSearchHit> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_SEARCH_PERSONS,
            parameters = buildJsonObject { put("p_query", query) }
        ).decodeAs()
        M08RpcDecoding.decodeRows<CanonicalPersonSearchRow>(element).map {
            PersonSearchHit(
                userId = it.userId,
                displayName = it.displayName.orEmpty(),
                username = it.username.orEmpty(),
                avatarAssetId = it.avatarAssetId
            )
        }
    }.getOrDefault(emptyList())

    override suspend fun create(
        command: CreateOrganizationInvitationCommand
    ): Result<OrganizationInvitation> = runCatching {
        val invitee = command.targetUserId?.takeIf { it.isNotBlank() }
            ?: error("INVITE_TARGET_REQUIRED")
        val role = if (command.invitedRole == OrganizationRoleCode.ADMIN) "ADMIN" else "MEMBER"
        val perms = if (role == "ADMIN") emptyList() else OrgInvitePolicy.normalizeMemberPermissions(
            command.permissionCodes.toSet()
        )
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_INVITE_ORG_MEMBER,
            parameters = buildJsonObject {
                put("p_organization_id", command.organizationId.value)
                put("p_invitee_user_id", invitee)
                put("p_role_code", role)
                put(
                    "p_permission_codes",
                    kotlinx.serialization.json.JsonArray(perms.map { JsonPrimitive(it) })
                )
            }
        ).decodeAs()
        val id = (element as? JsonPrimitive)?.content ?: element.toString().trim('"')
        OrganizationInvitation(
            id = id,
            organizationId = command.organizationId,
            invitedRole = command.invitedRole,
            targetUserId = invitee,
            invitedByUserId = command.invitedByUserId,
            permissionCodes = perms,
            expiresAtEpochMs = command.expiresAtEpochMs
        )
    }

    override suspend fun getById(id: String): OrganizationInvitation? =
        listMyPending().getOrNull()?.firstOrNull { it.id == id }

    override suspend fun accept(
        invitationId: String,
        actorUserId: String,
        token: OrganizationInvitationToken?,
        nowEpochMs: Long
    ): Result<OrganizationInvitation> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_ACCEPT_ORG_INVITATION,
            parameters = buildJsonObject { put("p_invitation_id", invitationId) }
        )
        getById(invitationId)?.copy(status = OrganizationInvitationStatus.ACCEPTED)
            ?: OrganizationInvitation(
                id = invitationId,
                organizationId = OrganizationId(""),
                invitedRole = OrganizationRoleCode.MEMBER,
                invitedByUserId = actorUserId,
                status = OrganizationInvitationStatus.ACCEPTED,
                expiresAtEpochMs = nowEpochMs
            )
    }

    override suspend fun revoke(
        invitationId: String,
        actorUserId: String,
        nowEpochMs: Long
    ): Result<OrganizationInvitation> =
        Result.failure(IllegalStateException("USE_CANONICAL_REJECT_OR_ADMIN_RPC"))

    override suspend fun listByOrganization(
        organizationId: OrganizationId
    ): Result<List<OrganizationInvitation>> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_ORG_INVITATIONS,
            parameters = buildJsonObject { put("p_organization_id", organizationId.value) }
        ).decodeAs()
        M08RpcDecoding.decodeRows<CanonicalOrgInvitationRow>(element).map { it.toDomain() }
    }

    override suspend fun listMyPending(): Result<List<OrganizationInvitation>> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_MY_ORG_INVITATIONS
        ).decodeAs()
        M08RpcDecoding.decodeRows<CanonicalOrgInvitationRow>(element).map { it.toDomain() }
    }

    override suspend fun acceptByToken(
        token: OrganizationInvitationToken
    ): Result<OrganizationMembership> =
        Result.failure(IllegalStateException("CANONICAL_ACCEPT_BY_ID"))

    override suspend fun declineByToken(token: OrganizationInvitationToken): Result<Unit> =
        Result.failure(IllegalStateException("CANONICAL_REJECT_BY_ID"))

    override suspend fun rejectMine(invitationId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_REJECT_ORG_INVITATION,
            parameters = buildJsonObject { put("p_invitation_id", invitationId) }
        )
        Unit
    }

    private fun CanonicalOrgInvitationRow.toDomain(): OrganizationInvitation {
        val role = runCatching {
            OrganizationRoleCode.valueOf(roleCode?.trim()?.uppercase() ?: "MEMBER")
        }.getOrDefault(OrganizationRoleCode.MEMBER)
        val status = runCatching {
            OrganizationInvitationStatus.valueOf(status.trim().uppercase())
        }.getOrDefault(OrganizationInvitationStatus.PENDING)
        return OrganizationInvitation(
            id = id,
            organizationId = OrganizationId(organizationId),
            invitedRole = role,
            status = status,
            invitedByUserId = invitedBy.orEmpty(),
            targetUserId = inviteeUserId,
            permissionCodes = permissionCodes,
            organizationName = organizationName,
            organizationCapability = organizationCapability,
            inviterName = inviterName,
            expiresAtEpochMs = expiresAt?.let {
                runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
            } ?: 0L
        )
    }
}

fun OrganizationInvitation.toSyntheticMembership(userId: String): OrganizationMembership =
    OrganizationMembership(
        id = "canon-inv-$id",
        organizationId = organizationId,
        userId = userId,
        role = invitedRole,
        status = OrganizationMembershipStatus.ACTIVE
    )
