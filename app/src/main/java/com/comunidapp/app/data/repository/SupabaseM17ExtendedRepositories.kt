package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M17CampaignTransparencyReport
import com.comunidapp.app.data.model.M17InKindDonationNeed
import com.comunidapp.app.data.model.M17InKindPledge
import com.comunidapp.app.data.model.M17InKindSearchFilter
import com.comunidapp.app.data.model.M17PublicInKindNeed
import com.comunidapp.app.data.model.M17PublicVolunteerOpportunity
import com.comunidapp.app.data.model.M17VolunteerApplication
import com.comunidapp.app.data.model.M17VolunteerOpportunity
import com.comunidapp.app.data.model.M17VolunteerSearchFilter
import com.comunidapp.app.data.remote.supabase.m17.M17DonationErrorMapper
import com.comunidapp.app.data.remote.supabase.m17.SupabaseM17ExtendedRemoteDataSource
import com.comunidapp.app.data.remote.supabase.m17.toM17CampaignTransparencyReport
import com.comunidapp.app.data.remote.supabase.m17.toM17InKindPledgeFromRpc
import com.comunidapp.app.data.remote.supabase.m17.toM17PublicInKindNeed
import com.comunidapp.app.data.remote.supabase.m17.toM17PublicVolunteerOpportunity
import com.comunidapp.app.data.remote.supabase.m17.toM17VolunteerApplicationFromRpc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SupabaseM17InKindRepository(
    private val remote: SupabaseM17ExtendedRemoteDataSource = SupabaseM17ExtendedRemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M17InKindRepository {

    override suspend fun searchPublicNeeds(filter: M17InKindSearchFilter): Result<List<M17PublicInKindNeed>> =
        try {
            Result.success(
                remote.listPublicInKindNeeds(
                    query = filter.query.takeIf { it.isNotBlank() },
                    category = filter.category?.name,
                    organizationId = filter.organizationId
                ).map { it.toM17PublicInKindNeed() }
            )
        } catch (t: Throwable) {
            M17DonationErrorMapper.failure(t)
        }

    override suspend fun getPublicNeed(id: String): Result<M17PublicInKindNeed> = try {
        if (id.isBlank()) M17DonationErrorMapper.fail("M17_NEED_NOT_FOUND")
        else Result.success(remote.getPublicInKindNeed(id).toM17PublicInKindNeed())
    } catch (t: Throwable) {
        M17DonationErrorMapper.failure(t)
    }

    override fun observeNeedsForOrganization(orgId: String): Flow<List<M17InKindDonationNeed>> = flow {
        emit(emptyList())
    }

    override suspend fun createPledge(needId: String, quantity: Int, message: String?): Result<M17InKindPledge> =
        try {
            val user = actorUserId() ?: return M17DonationErrorMapper.fail("NOT_AUTHENTICATED")
            Result.success(
                remote.createInKindPledge(needId, quantity, message)
                    .toM17InKindPledgeFromRpc(needId, user)
            )
        } catch (t: Throwable) {
            M17DonationErrorMapper.failure(t)
        }

    override suspend fun markDelivered(pledgeId: String): Result<M17InKindPledge> = try {
        val json = remote.markInKindPledgeDelivered(pledgeId)
        Result.success(json.toM17InKindPledgeFromRpc(needId = "", actorUserId().orEmpty()))
    } catch (t: Throwable) {
        M17DonationErrorMapper.failure(t)
    }
}

class SupabaseM17VolunteerRepository(
    private val remote: SupabaseM17ExtendedRemoteDataSource = SupabaseM17ExtendedRemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M17VolunteerRepository {

    override suspend fun searchPublicOpportunities(
        filter: M17VolunteerSearchFilter
    ): Result<List<M17PublicVolunteerOpportunity>> = try {
        Result.success(
            remote.listPublicVolunteerOpportunities(
                query = filter.query.takeIf { it.isNotBlank() },
                type = filter.type?.name,
                organizationId = filter.organizationId
            ).map { it.toM17PublicVolunteerOpportunity() }
        )
    } catch (t: Throwable) {
        M17DonationErrorMapper.failure(t)
    }

    override suspend fun getPublicOpportunity(id: String): Result<M17PublicVolunteerOpportunity> = try {
        if (id.isBlank()) M17DonationErrorMapper.fail("M17_OPPORTUNITY_NOT_FOUND")
        else Result.success(remote.getPublicVolunteerOpportunity(id).toM17PublicVolunteerOpportunity())
    } catch (t: Throwable) {
        M17DonationErrorMapper.failure(t)
    }

    override fun observeOpportunitiesForOrganization(orgId: String): Flow<List<M17VolunteerOpportunity>> =
        flow { emit(emptyList()) }

    override suspend fun submitApplication(opportunityId: String, message: String?): Result<M17VolunteerApplication> =
        try {
            val user = actorUserId() ?: return M17DonationErrorMapper.fail("NOT_AUTHENTICATED")
            Result.success(
                remote.submitVolunteerApplication(opportunityId, message)
                    .toM17VolunteerApplicationFromRpc(opportunityId, user)
            )
        } catch (t: Throwable) {
            M17DonationErrorMapper.failure(t)
        }

    override suspend fun acceptApplication(applicationId: String): Result<M17VolunteerApplication> = try {
        val json = remote.acceptVolunteerApplication(applicationId)
        Result.success(json.toM17VolunteerApplicationFromRpc("", actorUserId().orEmpty()))
    } catch (t: Throwable) {
        M17DonationErrorMapper.failure(t)
    }
}

class SupabaseM17TransparencyRepository(
    private val remote: SupabaseM17ExtendedRemoteDataSource = SupabaseM17ExtendedRemoteDataSource()
) : M17TransparencyRepository {

    override suspend fun getTransparencyReport(campaignId: String): Result<M17CampaignTransparencyReport> =
        try {
            if (campaignId.isBlank()) M17DonationErrorMapper.fail("M17_CAMPAIGN_NOT_FOUND")
            else Result.success(remote.getPublicCampaignTransparency(campaignId).toM17CampaignTransparencyReport())
        } catch (t: Throwable) {
            M17DonationErrorMapper.failure(t)
        }
}
