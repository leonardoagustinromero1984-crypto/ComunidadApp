package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M26AiJob
import com.comunidapp.app.data.model.M26AiResult
import com.comunidapp.app.data.model.M26PublicAiResultSummary
import com.comunidapp.app.data.model.M26PublicReviewQueueItem
import com.comunidapp.app.data.model.RequestM26AiJobInput
import com.comunidapp.app.data.model.ReviewM26AiResultInput
import com.comunidapp.app.data.model.M26AssistanceSession
import com.comunidapp.app.data.model.M26EvaluatedRecommendation
import com.comunidapp.app.data.model.M26NotificationHookState
import com.comunidapp.app.data.model.M26PublicAssistanceSession
import com.comunidapp.app.data.model.M26PublicDuplicateCandidate
import com.comunidapp.app.data.model.M26PublicRecommendation
import com.comunidapp.app.data.model.M26PublicVisualMatch
import com.comunidapp.app.data.model.M26VisualMatchSuggestion
import com.comunidapp.app.data.model.M26ReviewDecision
import com.comunidapp.app.data.model.RequestM26VisualMatchInput
import com.comunidapp.app.data.model.ReviewM26RecommendationInput
import com.comunidapp.app.data.model.StartM26AssistanceInput
import com.comunidapp.app.data.model.SubmitM26RecommendationInput
import com.comunidapp.app.data.remote.supabase.m26.M26AiErrorMapper
import com.comunidapp.app.data.remote.supabase.m26.SupabaseM26RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m26.toM26AssistanceSession
import com.comunidapp.app.data.remote.supabase.m26.toM26EvaluatedRecommendation
import com.comunidapp.app.data.remote.supabase.m26.toM26PublicAssistance
import com.comunidapp.app.data.remote.supabase.m26.toM26PublicDuplicate
import com.comunidapp.app.data.remote.supabase.m26.toM26PublicRecommendation
import com.comunidapp.app.data.remote.supabase.m26.toM26PublicVisualMatch
import com.comunidapp.app.data.remote.supabase.m26.toM26VisualMatchSuggestion
import com.comunidapp.app.data.remote.supabase.m26.toM26AiJob
import com.comunidapp.app.data.remote.supabase.m26.toM26AiResult
import com.comunidapp.app.data.remote.supabase.m26.toM26PublicAiResultSummary
import com.comunidapp.app.data.remote.supabase.m26.toM26PublicReviewQueueItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SupabaseM26AiRepository(
    private val remote: SupabaseM26RemoteDataSource = SupabaseM26RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M26AiRepository {

    private fun requireActor() {
        if (actorUserId() == null) throw M26AiException(
            "NOT_AUTHENTICATED", M26AiErrors.userMessage("NOT_AUTHENTICATED")
        )
    }

    override fun observeMyJobs(): Flow<List<M26AiJob>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyJobs().map { it.toM26AiJob() } }.getOrElse { emptyList() })
    }

    override fun observeMyResults(): Flow<List<M26PublicAiResultSummary>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyResults().map { it.toM26PublicAiResultSummary() } }.getOrElse { emptyList() })
    }

    override fun observeReviewQueue(): Flow<List<M26PublicReviewQueueItem>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listReviewQueue().map { it.toM26PublicReviewQueueItem() } }.getOrElse { emptyList() })
    }

    override suspend fun requestAnalysis(input: RequestM26AiJobInput): Result<M26AiJob> = try {
        requireActor()
        M26AiValidators.validateJobPayload(input.payloadSummary, input.jobType)?.let { return M26AiErrorMapper.fail(it) }
        Result.success(
            remote.requestAiJob(input.jobType.name, input.payloadSummary.trim(), input.clientRequestId?.trim())
                .toM26AiJob()
        )
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override suspend fun cancelJob(jobId: String): Result<M26AiJob> = try {
        requireActor()
        Result.success(remote.cancelAiJob(jobId).toM26AiJob())
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override suspend fun submitResultForReview(resultId: String): Result<M26AiResult> = try {
        requireActor()
        Result.success(remote.submitResultForReview(resultId).toM26AiResult())
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override suspend fun reviewResult(input: ReviewM26AiResultInput): Result<M26AiResult> = try {
        requireActor()
        Result.success(
            remote.reviewAiResult(input.resultId, input.decision.name, input.publicReason?.trim())
                .toM26AiResult()
        )
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override suspend fun archiveResult(resultId: String): Result<M26AiResult> =
        reviewResult(ReviewM26AiResultInput(resultId, M26ReviewDecision.ARCHIVE, null))

    override fun observeVisualMatches(): Flow<List<M26PublicVisualMatch>> = flow {
        emit(runCatching { remote.listVisualMatches().map { it.toM26PublicVisualMatch() } }.getOrElse { emptyList() })
    }

    override fun observeDuplicateCandidates(): Flow<List<M26PublicDuplicateCandidate>> = flow {
        emit(runCatching { remote.listDuplicateCandidates().map { it.toM26PublicDuplicate() } }.getOrElse { emptyList() })
    }

    override fun observeAssistanceSessions(): Flow<List<M26PublicAssistanceSession>> = flow {
        if (actorUserId() == null) {
            emit(emptyList())
        } else {
            emit(runCatching { remote.listAssistanceSessions().map { it.toM26PublicAssistance() } }.getOrElse { emptyList() })
        }
    }

    override fun observeEligibleRecommendations(): Flow<List<M26PublicRecommendation>> = flow {
        emit(runCatching { remote.listEligibleRecommendations().map { it.toM26PublicRecommendation() } }.getOrElse { emptyList() })
    }

    override suspend fun requestVisualMatch(input: RequestM26VisualMatchInput): Result<M26VisualMatchSuggestion> = try {
        requireActor()
        M26AiValidators.validateVisualMatch(input.sourceLabel, input.targetLabel)?.let { return M26AiErrorMapper.fail(it) }
        Result.success(remote.requestVisualMatch(input.sourceLabel.trim(), input.targetLabel.trim()).toM26VisualMatchSuggestion())
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override suspend fun dismissVisualMatch(matchId: String): Result<Unit> = try {
        requireActor()
        remote.dismissVisualMatch(matchId)
        Result.success(Unit)
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override suspend fun confirmDuplicate(candidateId: String): Result<Unit> = try {
        requireActor()
        remote.confirmDuplicate(candidateId)
        Result.success(Unit)
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override suspend fun dismissDuplicate(candidateId: String): Result<Unit> = try {
        requireActor()
        remote.dismissDuplicate(candidateId)
        Result.success(Unit)
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override suspend fun startAssistanceSession(input: StartM26AssistanceInput): Result<M26AssistanceSession> = try {
        requireActor()
        M26AiValidators.validateAssistancePrompt(input.initialPrompt)?.let { return M26AiErrorMapper.fail(it) }
        Result.success(remote.startAssistanceSession(input.topic.name, input.initialPrompt.trim()).toM26AssistanceSession())
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override suspend fun closeAssistanceSession(sessionId: String): Result<Unit> = try {
        requireActor()
        remote.closeAssistanceSession(sessionId)
        Result.success(Unit)
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override suspend fun submitRecommendation(input: SubmitM26RecommendationInput): Result<M26EvaluatedRecommendation> = try {
        requireActor()
        M26AiValidators.validateRecommendation(input.title, input.rationale)?.let { return M26AiErrorMapper.fail(it) }
        Result.success(remote.submitRecommendation(input.kind.name, input.title.trim(), input.rationale.trim()).toM26EvaluatedRecommendation())
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override suspend fun reviewRecommendation(input: ReviewM26RecommendationInput): Result<M26EvaluatedRecommendation> = try {
        requireActor()
        Result.success(
            remote.reviewRecommendation(input.recommendationId, input.approved, input.reviewerNote?.trim())
                .toM26EvaluatedRecommendation()
        )
    } catch (error: Throwable) {
        M26AiErrorMapper.failure(error)
    }

    override fun observeNotificationsHook(): Flow<M26NotificationHookState> =
        flow { emit(M26NotificationHookState()) }
}
