package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.context.ContextIdentityMapping
import com.comunidapp.app.domain.organization.CreateOrganizationBranchCommand
import com.comunidapp.app.domain.organization.Organization
import com.comunidapp.app.domain.organization.OrganizationBranch
import com.comunidapp.app.domain.organization.OrganizationBranchStatus
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.OrganizationResourceType
import com.comunidapp.app.domain.organization.OrganizationSlug
import com.comunidapp.app.domain.organization.OrganizationStatus
import com.comunidapp.app.domain.organization.OrganizationType
import com.comunidapp.app.domain.organization.OrganizationVerificationStatus
import com.comunidapp.app.domain.organization.PublicOrganization
import com.comunidapp.app.domain.organization.UpdateOrganizationBranchCommand
import com.comunidapp.app.domain.organization.UpdateOrganizationCommand
import com.comunidapp.app.domain.organization.ValidatedOrganizationDraft
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class CanonicalOrgRow(
    val id: String,
    val name: String,
    val slug: String,
    @SerialName("primary_label") val primaryLabel: String? = null,
    @SerialName("home_locality_id") val homeLocalityId: String? = null,
    @SerialName("lifecycle_status") val lifecycleStatus: String? = null
)

class CanonicalOrganizationRepository : OrganizationRepository {
    private var cache: List<Organization> = emptyList()

    override suspend fun getById(id: OrganizationId): Organization? {
        refresh()
        return cache.firstOrNull { it.id == id }
    }

    override suspend fun createDraft(
        draft: ValidatedOrganizationDraft,
        createdByUserId: String
    ): Result<Organization> = createOrganization(draft)

    override suspend fun createOrganization(draft: ValidatedOrganizationDraft): Result<Organization> =
        runCatching {
            val capability = capabilityFor(draft)
            val id: String = try {
                supabase.postgrest.rpc(
                    function = CanonicalBackend.RPC_CREATE_ORGANIZATION,
                    parameters = buildJsonObject {
                        put("p_name", draft.publicName.ifBlank { draft.legalName })
                        put("p_slug", draft.slug.value)
                        put("p_capability", capability)
                        draft.homeLocalityId?.let { put("p_home_locality_id", it) }
                    }
                ).decodeAs()
            } catch (error: Exception) {
                recoverExisting(draft.slug.value, error)
            }
            refresh()
            cache.firstOrNull { it.id.value == id } ?: toOrg(
                CanonicalOrgRow(id, draft.publicName, draft.slug.value, capability, draft.homeLocalityId, "ACTIVE")
            )
        }.recoverCatching { error ->
            throw IllegalStateException(
                com.comunidapp.app.domain.ux.CanonicalUiErrorMapper.userMessage(error)
            )
        }

    private suspend fun recoverExisting(slug: String, error: Exception): String {
        refresh()
        val mine = cache.firstOrNull { it.slug.value.equals(slug, ignoreCase = true) }
        if (mine != null) return mine.id.value
        val mapped = com.comunidapp.app.domain.ux.CanonicalUiErrorMapper.userMessage(error)
        throw IllegalStateException(mapped)
    }

    private fun capabilityFor(draft: ValidatedOrganizationDraft): String {
        val token = listOfNotNull(draft.typeDescription, draft.type.name).joinToString(" ")
        return when {
            ContextIdentityMapping.isVeterinary(token) ||
                draft.type == OrganizationType.VETERINARY_CLINIC -> "VETERINARY_CLINIC"
            ContextIdentityMapping.isShop(token) || draft.type == OrganizationType.PET_SHOP -> "PROVIDER"
            ContextIdentityMapping.isDaycare(token) -> "DAYCARE"
            ContextIdentityMapping.isGrooming(token) -> "GROOMING"
            ContextIdentityMapping.isWalkingCare(token) -> "WALKING_CARE"
            ContextIdentityMapping.isTraining(token) -> "TRAINING"
            ContextIdentityMapping.isBrand(token) -> "BRAND"
            ContextIdentityMapping.isNgo(token) || draft.type == OrganizationType.NGO -> "NGO"
            ContextIdentityMapping.isRefuge(token) ||
                draft.type == OrganizationType.SHELTER ||
                draft.type == OrganizationType.RESCUE_GROUP -> "SHELTER"
            else -> "OTHER"
        }
    }

    override suspend fun getMyOrganizations(): List<Organization> {
        refresh()
        return cache
    }

    override suspend fun updateMyOrganization(command: UpdateOrganizationCommand): Result<Organization> =
        getById(command.organizationId)?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("NOT_FOUND"))

    override suspend fun getPublicBySlug(slug: String): Result<PublicOrganization?> {
        refresh()
        val org = cache.firstOrNull { it.slug.value == slug } ?: return Result.success(null)
        return Result.success(
            PublicOrganization(
                id = org.id.value,
                publicName = org.publicName,
                slug = org.slug.value,
                type = org.type,
                status = org.status,
                verificationStatus = org.verificationStatus
            )
        )
    }

    override suspend fun searchPublic(
        query: String,
        type: OrganizationType?,
        city: String?,
        limit: Int
    ): Result<List<PublicOrganization>> {
        refresh()
        return Result.success(
            cache.take(limit).map {
                PublicOrganization(
                    id = it.id.value,
                    publicName = it.publicName,
                    slug = it.slug.value,
                    type = it.type,
                    status = it.status,
                    verificationStatus = it.verificationStatus
                )
            }
        )
    }

    override suspend fun requestVerification(organizationId: OrganizationId): Result<Organization> =
        runCatching {
            val org = getById(organizationId)
                ?: throw IllegalStateException("ORGANIZATION_INCOMPLETE")
            val functionCode = when (org.type) {
                OrganizationType.SHELTER, OrganizationType.RESCUE_GROUP -> "SHELTER"
                OrganizationType.NGO -> "NGO"
                OrganizationType.VETERINARY_CLINIC -> "VETERINARY"
                else -> "BUSINESS"
            }
            CanonicalVerificationRepository().request(
                functionCode = functionCode,
                termsAccepted = true,
                evidenceNote = null,
                organizationId = organizationId.value
            ).getOrThrow()
            refresh()
            getById(organizationId)?.copy(
                verificationStatus = OrganizationVerificationStatus.PENDING
            ) ?: org.copy(verificationStatus = OrganizationVerificationStatus.PENDING)
        }

    override suspend fun linkResource(
        organizationId: OrganizationId,
        resourceType: OrganizationResourceType,
        resourceId: String
    ): Result<Unit> = Result.success(Unit)

    override suspend fun unlinkResource(
        organizationId: OrganizationId,
        resourceType: OrganizationResourceType,
        resourceId: String
    ): Result<Unit> = Result.success(Unit)

    override suspend fun listBranches(
        organizationId: OrganizationId,
        includePrivate: Boolean
    ): Result<List<OrganizationBranch>> = Result.success(emptyList())

    override suspend fun createBranch(command: CreateOrganizationBranchCommand): Result<OrganizationBranch> =
        Result.failure(IllegalStateException("NOT_IMPLEMENTED_PRODUCT"))

    override suspend fun updateBranch(command: UpdateOrganizationBranchCommand): Result<OrganizationBranch> =
        Result.failure(IllegalStateException("NOT_IMPLEMENTED_PRODUCT"))

    override suspend fun setBranchStatus(
        branchId: String,
        status: OrganizationBranchStatus
    ): Result<OrganizationBranch> = Result.failure(IllegalStateException("NOT_IMPLEMENTED_PRODUCT"))

    override suspend fun closeOrganization(
        organizationId: OrganizationId,
        reasonCode: String
    ): Result<Organization> = Result.failure(IllegalStateException("NOT_IMPLEMENTED_PRODUCT"))

    private suspend fun refresh() {
        val element: JsonElement = supabase.postgrest.rpc(CanonicalBackend.RPC_LIST_MY_ORGANIZATIONS).decodeAs()
        val verifications = runCatching {
            CanonicalVerificationRepository().listMine().getOrDefault(emptyList())
        }.getOrDefault(emptyList())
        cache = M08RpcDecoding.decodeRows<CanonicalOrgRow>(element).map { row ->
            val raw = verifications.firstOrNull { it.organizationId == row.id }?.status
            toOrg(row, parseVerification(raw))
        }
    }

    private fun parseVerification(raw: String?): OrganizationVerificationStatus = when (raw?.trim()?.uppercase()) {
        "PENDING" -> OrganizationVerificationStatus.PENDING
        "VERIFIED" -> OrganizationVerificationStatus.VERIFIED
        "REJECTED", "SUSPENDED" -> OrganizationVerificationStatus.REJECTED
        else -> OrganizationVerificationStatus.NOT_REQUESTED
    }

    private fun toOrg(
        row: CanonicalOrgRow,
        verificationStatus: OrganizationVerificationStatus = OrganizationVerificationStatus.NOT_REQUESTED
    ): Organization = Organization(
        id = OrganizationId(row.id),
        legalName = row.name,
        publicName = row.name,
        slug = OrganizationSlug.ofNormalized(row.slug),
        type = when (row.primaryLabel) {
            "SHELTER" -> OrganizationType.SHELTER
            "NGO" -> OrganizationType.NGO
            "VETERINARY_CLINIC" -> OrganizationType.VETERINARY_CLINIC
            "DAYCARE", "BOARDING" -> OrganizationType.OTHER
            "PROVIDER" -> OrganizationType.PET_SHOP
            "GROOMING", "WALKING_CARE", "TRAINING", "BRAND" -> OrganizationType.OTHER
            else -> OrganizationType.OTHER
        },
        typeDescription = row.primaryLabel,
        status = if (row.lifecycleStatus == "ARCHIVED") OrganizationStatus.CLOSED else OrganizationStatus.ACTIVE,
        verificationStatus = verificationStatus,
        city = row.homeLocalityId,
        createdByUserId = ""
    )
}
