package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM17CampaignInput
import com.comunidapp.app.data.model.M17CampaignFinancialSummary
import com.comunidapp.app.data.model.M17CampaignReference
import com.comunidapp.app.data.model.M17CampaignSearchFilter
import com.comunidapp.app.data.model.M17CampaignStatus
import com.comunidapp.app.data.model.M17Contribution
import com.comunidapp.app.data.model.M17DonationCampaign
import com.comunidapp.app.data.model.M17PublicCampaign
import com.comunidapp.app.data.model.M17PublicContribution
import com.comunidapp.app.data.model.RegisterM17MockContributionInput
import com.comunidapp.app.data.model.UpdateM17CampaignDetailsInput
import com.comunidapp.app.data.model.UpdateM17CampaignGoalInput
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m17.M17DonationErrorMapper
import com.comunidapp.app.data.remote.supabase.m17.SupabaseM17RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m17.toM17CampaignFinancialSummary
import com.comunidapp.app.data.remote.supabase.m17.toM17DonationCampaign
import com.comunidapp.app.data.remote.supabase.m17.toM17PublicCampaign
import com.comunidapp.app.data.remote.supabase.m17.toM17PublicContribution
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class SupabaseM17DonationRepository(
    private val remote: SupabaseM17RemoteDataSource = SupabaseM17RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M17DonationRepository {

    override fun observeCampaignById(campaignId: String): Flow<M17DonationCampaign?> = flow {
        emit(getCampaignInternal(campaignId).getOrNull())
    }

    override fun observeCampaignsForOrganization(organizationId: String): Flow<List<M17DonationCampaign>> =
        flow {
            emit(
                runCatching {
                    remote.listOrgCampaigns(organizationId).map { it.toM17DonationCampaign() }
                }.getOrElse { emptyList() }
            )
        }

    override suspend fun searchPublicCampaigns(filter: M17CampaignSearchFilter): Result<List<M17PublicCampaign>> =
        try {
            val list = remote.listPublic(
                buildJsonObject {
                    put("p_query", filter.query.takeIf { it.isNotBlank() })
                    put("p_type", filter.type?.name)
                    put("p_organization_id", filter.organizationId)
                    put("p_shelter_profile_id", filter.shelterProfileId)
                    put("p_with_pet_only", filter.withPetOnly)
                    put("p_active_only", filter.activeOnly)
                    put("p_completed_only", filter.completedOnly)
                    put("p_near_goal_only", filter.nearGoalOnly)
                }
            ).map { it.toM17PublicCampaign() }
            Result.success(list)
        } catch (t: Throwable) {
            M17DonationErrorMapper.failure(t)
        }

    override suspend fun getPublicCampaignById(campaignId: String): Result<M17PublicCampaign> = try {
        if (campaignId.isBlank()) M17DonationErrorMapper.fail("M17_CAMPAIGN_NOT_FOUND")
        else Result.success(remote.getPublic(campaignId).toM17PublicCampaign())
    } catch (t: Throwable) {
        M17DonationErrorMapper.failure(t)
    }

    override suspend fun createCampaign(input: CreateM17CampaignInput): Result<M17DonationCampaign> = try {
        Result.success(
            remote.createCampaign(
                buildJsonObject {
                    put("p_organization_id", input.organizationId)
                    put("p_title", input.title)
                    put("p_description", input.description)
                    put("p_campaign_type", input.campaignType.name)
                    put("p_goal_amount_minor", input.goalAmountMinor)
                    put("p_currency", input.currency)
                    input.endsAt?.let { put("p_ends_at", java.time.Instant.ofEpochMilli(it).toString()) }
                }
            ).toM17DonationCampaign()
        )
    } catch (t: Throwable) {
        M17DonationErrorMapper.failure(t)
    }

    override suspend fun updateCampaignDetails(
        input: UpdateM17CampaignDetailsInput
    ): Result<M17DonationCampaign> = try {
        Result.success(
            remote.updateCampaignDetails(
                buildJsonObject {
                    put("p_campaign_id", input.campaignId)
                    put("p_title", input.title)
                    put("p_description", input.description)
                    put("p_campaign_type", input.campaignType.name)
                    put("p_pet_id", input.reference.petId)
                    put("p_pet_public_name", input.reference.petPublicName)
                    put("p_shelter_profile_id", input.reference.shelterProfileId)
                    put("p_shelter_public_name", input.reference.shelterPublicName)
                    put("p_need_description", input.reference.needDescription)
                    put("p_public_location_text", input.reference.publicLocationText)
                }
            ).toM17DonationCampaign()
        )
    } catch (t: Throwable) {
        M17DonationErrorMapper.failure(t)
    }

    override suspend fun updateCampaignGoal(input: UpdateM17CampaignGoalInput): Result<M17DonationCampaign> =
        try {
            Result.success(
                remote.updateCampaignGoal(
                    buildJsonObject {
                        put("p_campaign_id", input.campaignId)
                        put("p_goal_amount_minor", input.goalAmountMinor)
                        put("p_currency", input.currency)
                    }
                ).toM17DonationCampaign()
            )
        } catch (t: Throwable) {
            M17DonationErrorMapper.failure(t)
        }

    override suspend fun updateCampaignReferences(
        campaignId: String,
        reference: M17CampaignReference
    ): Result<M17DonationCampaign> {
        val current = getCampaignInternal(campaignId).getOrNull()
            ?: return M17DonationErrorMapper.fail("M17_CAMPAIGN_NOT_FOUND")
        return updateCampaignDetails(
            UpdateM17CampaignDetailsInput(
                campaignId = campaignId,
                title = current.title,
                description = current.description,
                campaignType = current.campaignType,
                reference = reference
            )
        )
    }

    override suspend fun updateCampaignImages(
        campaignId: String,
        coverImageRef: String?,
        galleryImageRefs: List<String>
    ): Result<M17DonationCampaign> = try {
        Result.success(
            remote.updateCampaignImages(
                buildJsonObject {
                    put("p_campaign_id", campaignId)
                    put("p_cover_image_ref", coverImageRef)
                    putJsonArray("p_gallery_image_refs") {
                        galleryImageRefs.forEach { add(JsonPrimitive(it)) }
                    }
                }
            ).toM17DonationCampaign()
        )
    } catch (t: Throwable) {
        M17DonationErrorMapper.failure(t)
    }

    override suspend fun publishCampaign(campaignId: String): Result<M17DonationCampaign> =
        transition(campaignId, M17CampaignStatus.PUBLISHED)

    override suspend fun pauseCampaign(campaignId: String): Result<M17DonationCampaign> =
        transition(campaignId, M17CampaignStatus.PAUSED)

    override suspend fun completeCampaign(campaignId: String): Result<M17DonationCampaign> =
        transition(campaignId, M17CampaignStatus.COMPLETED)

    override suspend fun cancelCampaign(campaignId: String): Result<M17DonationCampaign> =
        transition(campaignId, M17CampaignStatus.CANCELLED)

    override suspend fun addPublicUpdate(campaignId: String, message: String): Result<M17DonationCampaign> =
        try {
            Result.success(remote.addCampaignUpdate(campaignId, message).toM17DonationCampaign())
        } catch (t: Throwable) {
            M17DonationErrorMapper.failure(t)
        }

    override suspend fun observeFinancialSummary(campaignId: String): Result<M17CampaignFinancialSummary> =
        try {
            Result.success(remote.getFinancialSummary(campaignId).toM17CampaignFinancialSummary())
        } catch (t: Throwable) {
            M17DonationErrorMapper.failure(t)
        }

    override suspend fun observePublicContributions(campaignId: String): Result<List<M17PublicContribution>> =
        try {
            Result.success(
                remote.listPublicContributions(campaignId).mapNotNull { it.toM17PublicContribution() }
            )
        } catch (t: Throwable) {
            M17DonationErrorMapper.failure(t)
        }

    override suspend fun registerMockContribution(
        input: RegisterM17MockContributionInput
    ): Result<M17Contribution> =
        M17DonationErrorMapper.fail("M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE")

    override suspend fun refreshCampaign(campaignId: String): Result<M17DonationCampaign> =
        getCampaignInternal(campaignId)

    override suspend fun canManageOrganization(organizationId: String): Boolean {
        val user = AuthProvider.repository.getCurrentUser() ?: return false
        val accountStatus = runCatching { AccountStatus.valueOf(user.accountStatus) }
            .getOrDefault(AccountStatus.ACTIVE)
        return runCatching {
            DataProvider.organizationPermissionRepository.hasPermission(
                organizationId = OrganizationId(organizationId),
                userId = user.id,
                accountStatus = accountStatus,
                permission = OrganizationPermissionCode.DONATION_MANAGE
            )
        }.getOrDefault(false)
    }

    override suspend fun isOrganizationEligible(organizationId: String): Boolean =
        runCatching { remote.isOrganizationEligible(organizationId) }.getOrDefault(false)

    private suspend fun getCampaignInternal(campaignId: String): Result<M17DonationCampaign> = try {
        if (campaignId.isBlank()) M17DonationErrorMapper.fail("M17_CAMPAIGN_NOT_FOUND")
        else Result.success(remote.getCampaign(campaignId).toM17DonationCampaign())
    } catch (t: Throwable) {
        M17DonationErrorMapper.failure(t)
    }

    private suspend fun transition(campaignId: String, status: M17CampaignStatus): Result<M17DonationCampaign> =
        try {
            Result.success(remote.transitionCampaign(campaignId, status.name).toM17DonationCampaign())
        } catch (t: Throwable) {
            M17DonationErrorMapper.failure(t)
        }
}
