package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.organization.Organization
import com.comunidapp.app.domain.organization.OrganizationContactVisibility
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.OrganizationResourceType
import com.comunidapp.app.domain.organization.OrganizationSlug
import com.comunidapp.app.domain.organization.OrganizationStatus
import com.comunidapp.app.domain.organization.OrganizationType
import com.comunidapp.app.domain.organization.OrganizationVerificationStatus
import com.comunidapp.app.domain.organization.PublicOrganization
import com.comunidapp.app.domain.organization.UpdateOrganizationCommand
import com.comunidapp.app.domain.organization.ValidatedOrganizationDraft
import com.comunidapp.app.domain.organization.authorization.OrganizationAuthorizationContext
import com.comunidapp.app.domain.organization.authorization.OrganizationMembership
import com.comunidapp.app.domain.organization.authorization.OrganizationMembershipStatus
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import com.comunidapp.app.domain.user.AccountStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
data class OrganizationRow(
    val id: String,
    val slug: String,
    @SerialName("legal_name") val legalName: String? = null,
    @SerialName("display_name") val displayName: String,
    val type: String,
    @SerialName("other_type_description") val otherTypeDescription: String? = null,
    val description: String? = null,
    val status: String = "DRAFT",
    @SerialName("verification_status") val verificationStatus: String = "NOT_REQUESTED",
    @SerialName("country_code") val countryCode: String? = null,
    val province: String? = null,
    val city: String? = null,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("contact_email_public") val contactEmailPublic: Boolean = false,
    @SerialName("contact_phone_public") val contactPhonePublic: Boolean = false,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("cover_path") val coverPath: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PublicOrganizationRpcRow(
    val id: String,
    val slug: String,
    @SerialName("display_name") val displayName: String,
    val type: String,
    val description: String? = null,
    val city: String? = null,
    val province: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val status: String,
    @SerialName("verification_status") val verificationStatus: String,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("cover_path") val coverPath: String? = null,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null
)

@Serializable
data class OrganizationMembershipRow(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("role_code") val roleCode: String,
    val status: String = "ACTIVE",
    @SerialName("invited_by") val invitedBy: String? = null,
    @SerialName("joined_at") val joinedAt: String? = null
)

object OrganizationRowMapper {

    fun toDomain(row: OrganizationRow): Organization = Organization(
        id = OrganizationId(row.id),
        legalName = row.legalName?.takeIf { it.isNotBlank() } ?: row.displayName,
        publicName = row.displayName,
        slug = OrganizationSlug.ofNormalized(row.slug.lowercase()),
        type = parseType(row.type),
        typeDescription = row.otherTypeDescription,
        description = row.description,
        status = parseStatus(row.status),
        verificationStatus = parseVerification(row.verificationStatus),
        institutionalEmail = row.contactEmail,
        institutionalPhone = row.contactPhone,
        contactVisibility = OrganizationContactVisibility(
            showEmail = row.contactEmailPublic,
            showPhone = row.contactPhonePublic
        ),
        city = row.city,
        province = row.province,
        countryCode = row.countryCode,
        logoPath = row.logoPath,
        coverPath = row.coverPath,
        createdByUserId = row.createdBy,
        createdAtEpochMs = row.createdAt.toEpochMillis(),
        updatedAtEpochMs = row.updatedAt.toEpochMillis()
    )

    fun toPublic(row: PublicOrganizationRpcRow): PublicOrganization = PublicOrganization(
        id = row.id,
        publicName = row.displayName,
        slug = row.slug,
        type = parseType(row.type),
        description = row.description,
        city = row.city,
        province = row.province,
        countryCode = row.countryCode,
        status = parseStatus(row.status),
        verificationStatus = parseVerification(row.verificationStatus),
        logoPath = row.logoPath,
        coverPath = row.coverPath,
        publicEmail = row.contactEmail,
        publicPhone = row.contactPhone
    )

    fun toMembership(row: OrganizationMembershipRow): OrganizationMembership =
        OrganizationMembership(
            id = row.id,
            organizationId = OrganizationId(row.organizationId),
            userId = row.userId,
            role = parseRole(row.roleCode),
            status = parseMembershipStatus(row.status),
            invitedByUserId = row.invitedBy,
            joinedAtEpochMs = row.joinedAt.toEpochMillis()
        )

    fun parseType(raw: String): OrganizationType =
        runCatching { OrganizationType.valueOf(raw.trim().uppercase()) }
            .getOrDefault(OrganizationType.OTHER)

    fun parseStatus(raw: String): OrganizationStatus =
        runCatching { OrganizationStatus.valueOf(raw.trim().uppercase()) }
            .getOrDefault(OrganizationStatus.DRAFT)

    fun parseVerification(raw: String): OrganizationVerificationStatus =
        runCatching { OrganizationVerificationStatus.valueOf(raw.trim().uppercase()) }
            .getOrDefault(OrganizationVerificationStatus.NOT_REQUESTED)

    fun parseRole(raw: String): OrganizationRoleCode =
        runCatching { OrganizationRoleCode.valueOf(raw.trim().uppercase()) }
            .getOrDefault(OrganizationRoleCode.VIEWER)

    fun parseMembershipStatus(raw: String): OrganizationMembershipStatus =
        runCatching { OrganizationMembershipStatus.valueOf(raw.trim().uppercase()) }
            .getOrDefault(OrganizationMembershipStatus.ACTIVE)

    fun String?.toEpochMillis(): Long? =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    fun resourceTypeDb(type: OrganizationResourceType): String = when (type) {
        OrganizationResourceType.SHELTER_LISTING -> "SHELTER_LISTING"
        OrganizationResourceType.SERVICE_PROFILE -> "SERVICE_PROFILE"
    }
}

/**
 * Persistencia vía RPCs security definer (auth.uid() server-side).
 */
class SupabaseOrganizationRepository : OrganizationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getById(id: OrganizationId): Organization? {
        return try {
            supabase.from("organizations")
                .select {
                    filter { eq("id", id.value) }
                }
                .decodeSingleOrNull<OrganizationRow>()
                ?.let(OrganizationRowMapper::toDomain)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun createDraft(
        draft: ValidatedOrganizationDraft,
        createdByUserId: String
    ): Result<Organization> {
        // Remoto: auth.uid() en RPC; createdByUserId se ignora.
        return createOrganization(draft)
    }

    override suspend fun createOrganization(draft: ValidatedOrganizationDraft): Result<Organization> {
        return try {
            val row = supabase.postgrest.rpc(
                function = "create_organization",
                parameters = buildJsonObject {
                    put("p_display_name", draft.publicName)
                    put("p_slug", draft.slug.value)
                    put("p_type", draft.type.name)
                    draft.typeDescription?.let { put("p_other_type_description", it) }
                    put("p_legal_name", draft.legalName)
                    draft.description?.let { put("p_description", it) }
                    draft.countryCode?.let { put("p_country_code", it) }
                    draft.province?.let { put("p_province", it) }
                    draft.city?.let { put("p_city", it) }
                }
            ).decodeList<OrganizationRow>().firstOrNull()
                ?: return Result.failure(IllegalStateException("CREATE_ORGANIZATION_EMPTY"))
            Result.success(OrganizationRowMapper.toDomain(row))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMyOrganizations(): List<Organization> {
        return try {
            supabase.postgrest.rpc(function = "get_my_organizations")
                .decodeList<OrganizationRow>()
                .map(OrganizationRowMapper::toDomain)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun updateMyOrganization(
        command: UpdateOrganizationCommand
    ): Result<Organization> {
        return try {
            val row = supabase.postgrest.rpc(
                function = "update_my_organization",
                parameters = buildJsonObject {
                    put("p_organization_id", command.organizationId.value)
                    command.displayName?.let { put("p_display_name", it) }
                    command.legalName?.let { put("p_legal_name", it) }
                    command.description?.let { put("p_description", it) }
                    command.countryCode?.let { put("p_country_code", it) }
                    command.province?.let { put("p_province", it) }
                    command.city?.let { put("p_city", it) }
                    command.contactEmail?.let { put("p_contact_email", it) }
                    command.contactPhone?.let { put("p_contact_phone", it) }
                    command.contactEmailPublic?.let { put("p_contact_email_public", it) }
                    command.contactPhonePublic?.let { put("p_contact_phone_public", it) }
                    command.logoPath?.let { put("p_logo_path", it) }
                    command.coverPath?.let { put("p_cover_path", it) }
                }
            ).decodeList<OrganizationRow>().firstOrNull()
                ?: return Result.failure(IllegalStateException("UPDATE_ORGANIZATION_EMPTY"))
            Result.success(OrganizationRowMapper.toDomain(row))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPublicBySlug(slug: String): Result<PublicOrganization?> {
        return try {
            val element = supabase.postgrest.rpc(
                function = "get_public_organization_by_slug",
                parameters = buildJsonObject { put("p_slug", slug) }
            ).decodeAs<JsonElement>()
            if (element is JsonNull) return Result.success(null)
            val row = json.decodeFromJsonElement(PublicOrganizationRpcRow.serializer(), element)
            Result.success(OrganizationRowMapper.toPublic(row))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchPublic(
        query: String,
        type: OrganizationType?,
        city: String?,
        limit: Int
    ): Result<List<PublicOrganization>> {
        return try {
            val element = supabase.postgrest.rpc(
                function = "search_public_organizations",
                parameters = buildJsonObject {
                    put("p_query", query)
                    type?.let { put("p_type", it.name) }
                    city?.let { put("p_city", it) }
                    put("p_limit", limit)
                }
            ).decodeAs<JsonElement>()
            val array = element as? JsonArray ?: return Result.success(emptyList())
            val list = array.mapNotNull { item ->
                runCatching {
                    json.decodeFromJsonElement(PublicOrganizationRpcRow.serializer(), item)
                }.getOrNull()?.let(OrganizationRowMapper::toPublic)
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun requestVerification(
        organizationId: OrganizationId
    ): Result<Organization> {
        return try {
            val row = supabase.postgrest.rpc(
                function = "request_organization_verification",
                parameters = buildJsonObject {
                    put("p_organization_id", organizationId.value)
                }
            ).decodeList<OrganizationRow>().firstOrNull()
                ?: return Result.failure(IllegalStateException("REQUEST_VERIFICATION_EMPTY"))
            Result.success(OrganizationRowMapper.toDomain(row))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun linkResource(
        organizationId: OrganizationId,
        resourceType: OrganizationResourceType,
        resourceId: String
    ): Result<Unit> {
        return try {
            supabase.postgrest.rpc(
                function = "link_organization_resource",
                parameters = buildJsonObject {
                    put("p_organization_id", organizationId.value)
                    put("p_resource_type", OrganizationRowMapper.resourceTypeDb(resourceType))
                    put("p_resource_id", resourceId)
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unlinkResource(
        organizationId: OrganizationId,
        resourceType: OrganizationResourceType,
        resourceId: String
    ): Result<Unit> {
        return try {
            supabase.postgrest.rpc(
                function = "unlink_organization_resource",
                parameters = buildJsonObject {
                    put("p_organization_id", organizationId.value)
                    put("p_resource_type", OrganizationRowMapper.resourceTypeDb(resourceType))
                    put("p_resource_id", resourceId)
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class SupabaseOrganizationMembershipRepository : OrganizationMembershipRepository {

    override suspend fun getActiveMembership(
        organizationId: OrganizationId,
        userId: String
    ): OrganizationMembership? {
        return try {
            supabase.from("organization_memberships")
                .select {
                    filter {
                        eq("organization_id", organizationId.value)
                        eq("user_id", userId)
                        eq("status", "ACTIVE")
                    }
                }
                .decodeList<OrganizationMembershipRow>()
                .firstOrNull()
                ?.let(OrganizationRowMapper::toMembership)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun listActiveByOrganization(
        organizationId: OrganizationId
    ): List<OrganizationMembership> {
        return try {
            supabase.from("organization_memberships")
                .select {
                    filter {
                        eq("organization_id", organizationId.value)
                        eq("status", "ACTIVE")
                    }
                }
                .decodeList<OrganizationMembershipRow>()
                .map(OrganizationRowMapper::toMembership)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun listMyMemberships(userId: String): List<OrganizationMembership> {
        return try {
            val uid = userId.ifBlank {
                supabase.auth.currentUserOrNull()?.id.orEmpty()
            }
            if (uid.isBlank()) return emptyList()
            supabase.from("organization_memberships")
                .select {
                    filter {
                        eq("user_id", uid)
                        eq("status", "ACTIVE")
                    }
                }
                .decodeList<OrganizationMembershipRow>()
                .map(OrganizationRowMapper::toMembership)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun countActiveOwners(organizationId: OrganizationId): Int =
        listActiveByOrganization(organizationId).count { it.role == OrganizationRoleCode.OWNER }

    override suspend fun addMembership(membership: OrganizationMembership): Result<Unit> =
        Result.failure(UnsupportedOperationException("membership writes only via RPC"))
}

/**
 * RPCs has_org_permission / get_my_org_permissions. Deny-by-default ante error.
 */
class SupabaseOrganizationPermissionRepository(
    private val organizationRepository: OrganizationRepository = SupabaseOrganizationRepository(),
    private val membershipRepository: OrganizationMembershipRepository =
        SupabaseOrganizationMembershipRepository()
) : OrganizationPermissionRepository {

    override suspend fun getAuthorizationContext(
        organizationId: OrganizationId,
        userId: String,
        accountStatus: AccountStatus
    ): OrganizationAuthorizationContext {
        if (userId.isBlank()) {
            return OrganizationAuthorizationContext.empty(userId, organizationId)
        }
        return try {
            val org = organizationRepository.getById(organizationId)
            val membership = membershipRepository.getActiveMembership(organizationId, userId)
            val permCodes = supabase.postgrest.rpc(
                function = "get_my_org_permissions",
                parameters = buildJsonObject {
                    put("p_organization_id", organizationId.value)
                }
            ).decodeAs<List<String>>()
            val permissions = permCodes.mapNotNull { OrganizationPermissionCode.fromCode(it) }.toSet()
            OrganizationAuthorizationContext(
                userId = userId,
                organizationId = organizationId,
                accountStatus = accountStatus,
                organizationStatus = org?.status ?: OrganizationStatus.DRAFT,
                membership = membership,
                permissions = permissions
            )
        } catch (_: Exception) {
            OrganizationAuthorizationContext.empty(userId, organizationId)
        }
    }

    override suspend fun hasPermission(
        organizationId: OrganizationId,
        userId: String,
        accountStatus: AccountStatus,
        permission: OrganizationPermissionCode
    ): Boolean {
        if (userId.isBlank()) return false
        if (accountStatus == AccountStatus.SUSPENDED || accountStatus == AccountStatus.BANNED) {
            return false
        }
        return try {
            supabase.postgrest.rpc(
                function = "has_org_permission",
                parameters = buildJsonObject {
                    put("p_organization_id", organizationId.value)
                    put("p_permission_code", permission.code)
                }
            ).decodeAs<Boolean>()
        } catch (_: Exception) {
            false
        }
    }
}
