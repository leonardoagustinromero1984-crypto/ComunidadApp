package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CheckM21EligibilityInput
import com.comunidapp.app.data.model.EditM21ReviewInput
import com.comunidapp.app.data.model.M21NotificationHookState
import com.comunidapp.app.data.model.M21PublicReputationSummary
import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21PublicReviewResponse
import com.comunidapp.app.data.model.M21PublicVerification
import com.comunidapp.app.data.model.M21RatingDistribution
import com.comunidapp.app.data.model.M21ReputationBreakdown
import com.comunidapp.app.data.model.M21ReviewSubjectReference
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.ReportM21ReviewInput
import com.comunidapp.app.data.model.SubmitM21AppealInput
import com.comunidapp.app.data.model.SubmitM21DisputeInput
import com.comunidapp.app.data.model.SubmitM21ReviewInput
import com.comunidapp.app.data.model.SubmitM21ReviewResponseInput
import com.comunidapp.app.data.model.SubmitM21VerificationInput
import com.comunidapp.app.data.remote.supabase.m21.M21Exception
import com.comunidapp.app.data.remote.supabase.m21.M21ReputationErrorMapper
import com.comunidapp.app.data.remote.supabase.m21.SupabaseM21RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m21.toM21PublicReview
import com.comunidapp.app.data.remote.supabase.m21.toM21PublicReviewResponse
import com.comunidapp.app.data.remote.supabase.m21.toM21PublicSummary
import com.comunidapp.app.data.remote.supabase.m21.toM21PublicVerification
import com.comunidapp.app.data.remote.supabase.m21.toM21ReputationBreakdown
import com.comunidapp.app.data.remote.supabase.m21.toM21ReviewEligibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SupabaseM21ReputationRepository(
    private val remote: SupabaseM21RemoteDataSource = SupabaseM21RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M21ReputationRepository {

    private fun requireActor(): String =
        actorUserId() ?: throw M21Exception(
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

    override fun observeSubjectBreakdown(type: M21ReviewTargetType, targetId: String): Flow<M21ReputationBreakdown> =
        flow {
            emit(
                runCatching {
                    remote.getSubjectBreakdown(type.name, targetId).toM21ReputationBreakdown()
                }.getOrElse {
                    M21ReputationBreakdown(
                        subject = M21ReviewSubjectReference(type, targetId, type.name),
                        averageRating = null,
                        publishedReviewCount = 0,
                        ratingDistribution = M21RatingDistribution(),
                        reviewsWithResponseCount = 0,
                        lastReviewAt = null,
                        reviews = emptyList()
                    )
                }
            )
        }

    override fun observeNotificationsHook(): Flow<M21NotificationHookState> =
        flow { emit(M21NotificationHookState(available = false)) }

    override suspend fun checkEligibility(input: CheckM21EligibilityInput) =
        try {
            requireActor()
            Result.success(
                remote.checkEligibility(
                    targetType = input.targetType.name,
                    targetId = input.targetId,
                    targetDisplayLabel = input.targetDisplayLabel,
                    contextType = input.contextReference?.contextType?.name,
                    contextId = input.contextReference?.contextId,
                    contextPublicLabel = input.contextReference?.publicLabel
                ).toM21ReviewEligibility()
            )
        } catch (t: Throwable) {
            M21ReputationErrorMapper.failure(t)
        }

    override suspend fun getReviewDetail(reviewId: String): Result<M21PublicReview> =
        try {
            requireActor()
            Result.success(remote.getReviewDetail(reviewId).toM21PublicReview())
        } catch (t: Throwable) {
            M21ReputationErrorMapper.failure(t)
        }

    override fun observeMyReviews(): Flow<List<M21PublicReview>> = flow {
        emit(runCatching { remote.listMyReviews().map { it.toM21PublicReview() } }.getOrElse { emptyList() })
    }

    override suspend fun submitReview(input: SubmitM21ReviewInput): Result<M21PublicReview> =
        try {
            val actor = requireActor()
            M21ReputationValidators.validateReviewContent(input.content, input.rating)?.let {
                return M21ReputationErrorMapper.fail(it)
            }
            M21ReputationValidators.validateReviewTitle(input.title)?.let {
                return M21ReputationErrorMapper.fail(it)
            }
            M21ReputationValidators.validateSelfReview(actor, input.targetType, input.targetId)?.let {
                return M21ReputationErrorMapper.fail(it)
            }
            Result.success(
                remote.submitReview(
                    targetType = input.targetType.name,
                    targetId = input.targetId,
                    targetDisplayLabel = input.targetDisplayLabel,
                    rating = input.rating,
                    content = input.content.trim(),
                    title = input.title?.trim(),
                    contextType = input.contextReference?.contextType?.name,
                    contextId = input.contextReference?.contextId,
                    contextPublicLabel = input.contextReference?.publicLabel
                ).toM21PublicReview()
            )
        } catch (t: Throwable) {
            M21ReputationErrorMapper.failure(t)
        }

    override suspend fun editReview(input: EditM21ReviewInput): Result<M21PublicReview> =
        try {
            requireActor()
            input.rating?.let {
                if (it !in 1..5) return M21ReputationErrorMapper.fail("M21_INVALID_RATING")
            }
            input.content?.let { content ->
                M21ReputationValidators.validateReviewContent(content, input.rating ?: 5)?.let {
                    return M21ReputationErrorMapper.fail(it)
                }
            }
            M21ReputationValidators.validateReviewTitle(input.title)?.let {
                return M21ReputationErrorMapper.fail(it)
            }
            Result.success(
                remote.editReview(
                    reviewId = input.reviewId,
                    rating = input.rating,
                    content = input.content?.trim(),
                    title = input.title?.trim()
                ).toM21PublicReview()
            )
        } catch (t: Throwable) {
            M21ReputationErrorMapper.failure(t)
        }

    override suspend fun archiveReview(reviewId: String): Result<Unit> =
        try {
            requireActor()
            remote.archiveReview(reviewId)
            Result.success(Unit)
        } catch (t: Throwable) {
            M21ReputationErrorMapper.failure(t)
        }

    override suspend fun submitReviewResponse(input: SubmitM21ReviewResponseInput): Result<M21PublicReviewResponse> =
        try {
            requireActor()
            M21ReputationValidators.validateReviewResponse(input.content)?.let {
                return M21ReputationErrorMapper.fail(it)
            }
            val response = remote.submitReviewResponse(input.reviewId, input.content.trim())
                .toM21PublicReviewResponse()
                ?: return M21ReputationErrorMapper.fail("M21_INVALID_RESPONSE")
            Result.success(response)
        } catch (t: Throwable) {
            M21ReputationErrorMapper.failure(t)
        }

    override suspend fun reportReview(input: ReportM21ReviewInput): Result<Unit> =
        try {
            requireActor()
            remote.reportReview(
                reviewId = input.reviewId,
                reason = input.reason,
                details = input.details,
                reportResponse = input.reportResponse
            )
            Result.success(Unit)
        } catch (t: Throwable) {
            M21ReputationErrorMapper.failure(t)
        }

    override suspend fun submitDispute(input: SubmitM21DisputeInput): Result<Unit> =
        try {
            requireActor()
            M21ReputationValidators.validateDispute(input.details)?.let {
                return M21ReputationErrorMapper.fail(it)
            }
            M21ReputationValidators.validateDisputeReason(input.reason)?.let {
                return M21ReputationErrorMapper.fail(it)
            }
            remote.submitDispute(
                reviewId = input.reviewId,
                reason = input.reason.name,
                details = input.details.trim(),
                evidenceRef = input.evidenceRef
            )
            Result.success(Unit)
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
