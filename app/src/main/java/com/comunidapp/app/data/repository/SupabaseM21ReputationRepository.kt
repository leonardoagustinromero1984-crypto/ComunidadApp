package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M21PublicReputationSummary
import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21PublicVerification
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.SubmitM21AppealInput
import com.comunidapp.app.data.model.SubmitM21ReviewInput
import com.comunidapp.app.data.model.SubmitM21VerificationInput
import com.comunidapp.app.data.remote.supabase.m21.M21ReputationErrorMapper
import com.comunidapp.app.data.remote.supabase.m21.SupabaseM21RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m21.toM21PublicReview
import com.comunidapp.app.data.remote.supabase.m21.toM21PublicSummary
import com.comunidapp.app.data.remote.supabase.m21.toM21PublicVerification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SupabaseM21ReputationRepository(
    private val remote: SupabaseM21RemoteDataSource = SupabaseM21RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M21ReputationRepository {

    private fun requireActor(): String =
        actorUserId() ?: throw com.comunidapp.app.data.remote.supabase.m21.M21Exception(
            "NOT_AUTHENTICATED",
            M21ReputationErrorMapper.userMessage("NOT_AUTHENTICATED")
        )

    override fun observeMySummary(): Flow<M21PublicReputationSummary> = flow {
        emit(runCatching { remote.getMySummary().toM21PublicSummary() }.getOrElse {
            M21PublicReputationSummary(0, 0, null, emptyList(), false, false)
        })
    }

    override fun observeReviewsForTarget(type: M21ReviewTargetType, targetId: String): Flow<List<M21PublicReview>> =
        flow {
            emit(
                runCatching {
                    remote.listReviewsForTarget(type.name, targetId).map { it.toM21PublicReview() }
                }.getOrElse { emptyList() }
            )
        }

    override fun observeMyReviews(): Flow<List<M21PublicReview>> = flow {
        emit(runCatching { remote.listMyReviews().map { it.toM21PublicReview() } }.getOrElse { emptyList() })
    }

    override suspend fun submitReview(input: SubmitM21ReviewInput): Result<M21PublicReview> =
        try {
            requireActor()
            M21ReputationValidators.validateReviewContent(input.content, input.rating)?.let {
                return M21ReputationErrorMapper.fail(it)
            }
            Result.success(
                remote.submitReview(
                    targetType = input.targetType.name,
                    targetId = input.targetId,
                    targetDisplayLabel = input.targetDisplayLabel,
                    rating = input.rating,
                    content = input.content.trim()
                ).toM21PublicReview()
            )
        } catch (t: Throwable) {
            M21ReputationErrorMapper.failure(t)
        }

    override suspend fun getMyVerifications(): Result<List<M21PublicVerification>> =
        try {
            requireActor()
            Result.success(remote.listMyVerifications().map { it.toM21PublicVerification() })
        } catch (t: Throwable) {
            M21ReputationErrorMapper.failure(t)
        }

    override suspend fun submitVerification(input: SubmitM21VerificationInput): Result<M21PublicVerification> =
        try {
            requireActor()
            M21ReputationValidators.validateVerificationInput(
                input.verificationType, input.displayLabel, input.licenseCredential
            )?.let { return M21ReputationErrorMapper.fail(it) }
            val lic = input.licenseCredential
            Result.success(
                remote.submitVerification(
                    verificationType = input.verificationType.name,
                    displayLabel = input.displayLabel,
                    licenseNumber = lic?.licenseNumber,
                    issuingAuthority = lic?.issuingAuthority,
                    jurisdiction = lic?.jurisdiction
                ).toM21PublicVerification()
            )
        } catch (t: Throwable) {
            M21ReputationErrorMapper.failure(t)
        }

    override suspend fun submitAppeal(input: SubmitM21AppealInput): Result<Unit> =
        try {
            requireActor()
            M21ReputationValidators.validateAppealReason(input.reason)?.let {
                return M21ReputationErrorMapper.fail(it)
            }
            remote.submitAppeal(input.reviewId, input.reason.trim())
            Result.success(Unit)
        } catch (t: Throwable) {
            M21ReputationErrorMapper.failure(t)
        }
}
