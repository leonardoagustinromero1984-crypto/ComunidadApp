package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M26AiJob
import com.comunidapp.app.data.model.M26AiJobStatus
import com.comunidapp.app.data.model.M26AiJobType
import com.comunidapp.app.data.model.M26AiProvenance
import com.comunidapp.app.data.model.M26AiResult
import com.comunidapp.app.data.model.M26AiResultStatus
import com.comunidapp.app.data.model.M26AssistanceSession
import com.comunidapp.app.data.model.M26AssistanceSessionStatus
import com.comunidapp.app.data.model.M26AssistanceTopic
import com.comunidapp.app.data.model.M26ConfidenceBand
import com.comunidapp.app.data.model.M26DuplicateCandidate
import com.comunidapp.app.data.model.M26DuplicateStatus
import com.comunidapp.app.data.model.M26EvaluatedRecommendation
import com.comunidapp.app.data.model.M26MockIds
import com.comunidapp.app.data.model.M26MockUsers
import com.comunidapp.app.data.model.M26NotificationHookState
import com.comunidapp.app.data.model.M26PublicAssistanceSession
import com.comunidapp.app.data.model.M26PublicDuplicateCandidate
import com.comunidapp.app.data.model.M26PublicRecommendation
import com.comunidapp.app.data.model.M26PublicVisualMatch
import com.comunidapp.app.data.model.M26RecommendationKind
import com.comunidapp.app.data.model.M26RecommendationStatus
import com.comunidapp.app.data.model.M26ReviewDecision
import com.comunidapp.app.data.model.M26VisualMatchStatus
import com.comunidapp.app.data.model.M26VisualMatchSuggestion
import com.comunidapp.app.data.model.RequestM26VisualMatchInput
import com.comunidapp.app.data.model.ReviewM26RecommendationInput
import com.comunidapp.app.data.model.StartM26AssistanceInput
import com.comunidapp.app.data.model.SubmitM26RecommendationInput
import com.comunidapp.app.data.model.M26ModelDescriptor
import com.comunidapp.app.data.model.M26PublicAiResultSummary
import com.comunidapp.app.data.model.M26PublicReviewQueueItem
import com.comunidapp.app.data.model.M26ReasonCode
import com.comunidapp.app.data.model.RequestM26AiJobInput
import com.comunidapp.app.data.model.ReviewM26AiResultInput
import com.comunidapp.app.domain.m26.M26AiOperationsService
import com.comunidapp.app.domain.m26.M26JobLifecycle
import com.comunidapp.app.domain.m26.M26PrivacySanitizer
import com.comunidapp.app.domain.m26.M26RecommendationEligibilityService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface M26AiRepository {
    fun observeVisualMatches(): Flow<List<M26PublicVisualMatch>>
    fun observeDuplicateCandidates(): Flow<List<M26PublicDuplicateCandidate>>
    fun observeAssistanceSessions(): Flow<List<M26PublicAssistanceSession>>
    fun observeEligibleRecommendations(): Flow<List<M26PublicRecommendation>>
    fun observeMyJobs(): Flow<List<M26AiJob>>
    fun observeMyResults(): Flow<List<M26PublicAiResultSummary>>
    fun observeReviewQueue(): Flow<List<M26PublicReviewQueueItem>>
    suspend fun requestAnalysis(input: RequestM26AiJobInput): Result<M26AiJob>
    suspend fun cancelJob(jobId: String): Result<M26AiJob>
    suspend fun submitResultForReview(resultId: String): Result<M26AiResult>
    suspend fun reviewResult(input: ReviewM26AiResultInput): Result<M26AiResult>
    suspend fun archiveResult(resultId: String): Result<M26AiResult>
    suspend fun requestVisualMatch(input: RequestM26VisualMatchInput): Result<M26VisualMatchSuggestion>
    suspend fun dismissVisualMatch(matchId: String): Result<Unit>
    suspend fun confirmDuplicate(candidateId: String): Result<Unit>
    suspend fun dismissDuplicate(candidateId: String): Result<Unit>
    suspend fun startAssistanceSession(input: StartM26AssistanceInput): Result<M26AssistanceSession>
    suspend fun closeAssistanceSession(sessionId: String): Result<Unit>
    suspend fun submitRecommendation(input: SubmitM26RecommendationInput): Result<M26EvaluatedRecommendation>
    suspend fun reviewRecommendation(input: ReviewM26RecommendationInput): Result<M26EvaluatedRecommendation>
    fun observeNotificationsHook(): Flow<M26NotificationHookState>
}

class M26AiMemoryStore {
    private val mutex = Mutex()
    private var sequence = 0
    val visualMatches = MutableStateFlow<List<M26VisualMatchSuggestion>>(emptyList())
    val duplicateCandidates = MutableStateFlow<List<M26DuplicateCandidate>>(emptyList())
    val assistanceSessions = MutableStateFlow<List<M26AssistanceSession>>(emptyList())
    val recommendations = MutableStateFlow<List<M26EvaluatedRecommendation>>(emptyList())
    val jobs = MutableStateFlow<List<M26AiJob>>(emptyList())
    val results = MutableStateFlow<List<M26AiResult>>(emptyList())
    val clientRequests = MutableStateFlow<Set<String>>(emptySet())
    val duplicateKeys = MutableStateFlow<Set<String>>(emptySet())

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
    fun nextId(prefix: String): String = "${prefix}_${++sequence}"

    fun seedDefaults() {
        if (visualMatches.value.isNotEmpty()) return
        val stamp = 1_700_000_000_000L
        visualMatches.value = listOf(
            visualMatch(M26MockIds.MATCH_HIGH, M26MockUsers.MEMBER, "Foto mascota perdida A", "Foto avistamiento B", 0.91, M26ConfidenceBand.HIGH, M26VisualMatchStatus.PENDING, stamp),
            visualMatch(M26MockIds.MATCH_PENDING, M26MockUsers.MEMBER, "Producto tienda X", "Producto tienda Y", 0.62, M26ConfidenceBand.MEDIUM, M26VisualMatchStatus.PENDING, stamp),
            visualMatch("m26_match_rejected", M26MockUsers.OTHER, "Anuncio adopción 1", "Anuncio adopción 2", 0.48, M26ConfidenceBand.LOW, M26VisualMatchStatus.REJECTED, stamp)
        )
        duplicateCandidates.value = listOf(
            duplicate(M26MockIds.DUPLICATE_OPEN, M26MockUsers.MEMBER, "Perfil servicio grooming", "Perfil grooming centro", 0.88, M26DuplicateStatus.OPEN, stamp),
            duplicate("m26_dup_dismissed", M26MockUsers.MEMBER, "Evento feria mascotas", "Evento feria adopta", 0.55, M26DuplicateStatus.DISMISSED, stamp)
        )
        assistanceSessions.value = listOf(
            assistance(M26MockIds.ASSISTANCE_ACTIVE, M26MockUsers.MEMBER, M26AssistanceTopic.LOST_PET, M26AssistanceSessionStatus.ACTIVE, "Sesión stub: orientación sobre avistamientos similares.", stamp),
            assistance("m26_assist_closed", M26MockUsers.MEMBER, M26AssistanceTopic.ADOPTION, M26AssistanceSessionStatus.CLOSED, "Sesión cerrada: checklist de adopción responsable.", stamp, stamp + 3_600_000)
        )
        recommendations.value = listOf(
            recommendation(M26MockIds.RECOMMENDATION_APPROVED, M26MockUsers.MEMBER, M26RecommendationKind.PROVIDER, "Prestador de grooming cercano", "Coincide con búsquedas recientes de la comunidad.", true, M26RecommendationStatus.APPROVED, stamp, "Revisado por equipo humano."),
            recommendation(M26MockIds.RECOMMENDATION_PENDING, M26MockUsers.MEMBER, M26RecommendationKind.CONTENT, "Artículo sobre paseos seguros", "Sugerencia generada automáticamente sin revisión.", false, M26RecommendationStatus.PENDING_REVIEW, stamp),
            recommendation("m26_rec_rejected", M26MockUsers.OTHER, M26RecommendationKind.PRODUCT, "Producto promocionado", "Descartado por baja relevancia.", true, M26RecommendationStatus.REJECTED, stamp, "No apto para mostrar.")
        )
    }

    private fun visualMatch(
        id: String, owner: String, source: String, target: String, score: Double,
        band: M26ConfidenceBand, status: M26VisualMatchStatus, stamp: Long
    ) = M26VisualMatchSuggestion(id, owner, source, target, score, band, status, stamp, stamp)

    private fun duplicate(
        id: String, owner: String, primary: String, duplicate: String, score: Double,
        status: M26DuplicateStatus, stamp: Long
    ) = M26DuplicateCandidate(id, owner, primary, duplicate, score, status, stamp, stamp)

    private fun assistance(
        id: String, userId: String, topic: M26AssistanceTopic, status: M26AssistanceSessionStatus,
        summary: String, stamp: Long, closedAt: Long? = null
    ) = M26AssistanceSession(id, userId, topic, status, summary, stamp, closedAt)

    private fun recommendation(
        id: String, subject: String, kind: M26RecommendationKind, title: String, rationale: String,
        reviewed: Boolean, status: M26RecommendationStatus, stamp: Long, note: String? = null
    ) = M26EvaluatedRecommendation(id, subject, kind, title, rationale, reviewed, note, status, stamp, stamp)
}

class MockM26AiRepository(
    private val actorUserId: () -> String?,
    private val store: M26AiMemoryStore = M26AiMemoryStore()
) : M26AiRepository {
    init { store.seedDefaults() }

    override fun observeMyJobs(): Flow<List<M26AiJob>> = store.jobs.map { jobs ->
        val actor = actorUserId() ?: return@map emptyList()
        jobs.filter { it.ownerUserId == actor }
    }

    override fun observeMyResults(): Flow<List<M26PublicAiResultSummary>> = store.results.map { results ->
        val actor = actorUserId() ?: return@map emptyList()
        results.filter { it.ownerUserId == actor }.map { it.toPublicSummary() }
    }

    override fun observeReviewQueue(): Flow<List<M26PublicReviewQueueItem>> = store.results.map { results ->
        if (!isReviewer()) return@map emptyList()
        results.filter { it.status == M26AiResultStatus.PENDING_REVIEW }.map {
            M26PublicReviewQueueItem(it.id, M26PrivacySanitizer.scrubPublicText(it.summary), it.resultType, it.status, it.model.version)
        }
    }

    private suspend fun requestAnalysisInternal(input: RequestM26AiJobInput): M26AiJob {
        val actor = requireActor()
        M26AiValidators.validateJobPayload(input.payloadSummary, input.jobType)?.let(::fail)
        input.clientRequestId?.let { key ->
            store.jobs.value.firstOrNull { it.ownerUserId == actor && it.clientRequestId == key }?.let { return it }
        }
        val (modelName, modelVersion) = M26AiOperationsService.stubModelVersion()
        val model = M26ModelDescriptor(modelName, modelVersion)
        val now = System.currentTimeMillis()
        val jobId = store.nextId("m26_job")
        var job = M26AiJob(jobId, actor, input.jobType, M26AiJobStatus.QUEUED, input.clientRequestId, model, now, now)
        store.jobs.value += job
        M26JobLifecycle.validateJobTransition(job.status, M26AiJobStatus.RUNNING)?.let(::fail)
        job = job.copy(status = M26AiJobStatus.RUNNING, updatedAt = System.currentTimeMillis())
        store.jobs.value = store.jobs.value.map { if (it.id == jobId) job else it }
        val result = createResultForJob(job, input.payloadSummary.trim())
        M26JobLifecycle.validateJobTransition(job.status, M26AiJobStatus.COMPLETED)?.let(::fail)
        job = job.copy(status = M26AiJobStatus.COMPLETED, updatedAt = System.currentTimeMillis(), completedAt = System.currentTimeMillis())
        store.jobs.value = store.jobs.value.map { if (it.id == jobId) job else it }
        store.results.value += result
        input.clientRequestId?.let { store.clientRequests.value = store.clientRequests.value + it }
        return job
    }

    override suspend fun requestAnalysis(input: RequestM26AiJobInput): Result<M26AiJob> = mutate {
        requestAnalysisInternal(input)
    }

    private fun createResultForJob(job: M26AiJob, payload: String): M26AiResult {
        val now = System.currentTimeMillis()
        val reasonCodes = defaultReasonCodes(job.jobType)
        val summary = buildResultSummary(job, payload)
        val initialStatus = when (job.jobType) {
            M26AiJobType.VISUAL_MATCH, M26AiJobType.DUPLICATE_SCAN, M26AiJobType.RECOMMENDATION ->
                M26AiResultStatus.PENDING_REVIEW
            M26AiJobType.ASSISTANCE -> M26AiResultStatus.DRAFT
        }
        when (job.jobType) {
            M26AiJobType.VISUAL_MATCH -> createVisualMatchArtifact(job, payload, now)
            M26AiJobType.DUPLICATE_SCAN -> createDuplicateArtifact(job, payload, now)
            M26AiJobType.ASSISTANCE -> createAssistanceArtifact(job, payload, now)
            M26AiJobType.RECOMMENDATION -> createRecommendationArtifact(job, payload, now)
        }
        return M26AiResult(
            id = store.nextId("m26_result"),
            jobId = job.id,
            ownerUserId = job.ownerUserId,
            resultType = job.jobType,
            status = initialStatus,
            summary = summary,
            reasonCodes = reasonCodes,
            model = job.model,
            provenance = M26AiProvenance("M26", job.id, now),
            createdAt = now,
            updatedAt = now
        )
    }

    private fun createVisualMatchArtifact(job: M26AiJob, payload: String, now: Long) {
        val parts = payload.split("|", limit = 2)
        if (parts.size != 2) return
        val score = 0.75
        M26AiValidators.validateScore(score)?.let { return }
        store.visualMatches.value += M26VisualMatchSuggestion(
            store.nextId("m26_match"),
            job.ownerUserId,
            parts[0].trim(),
            parts[1].trim(),
            score,
            confidenceBandFor(score),
            M26VisualMatchStatus.PENDING,
            now,
            now
        )
    }

    private fun createDuplicateArtifact(job: M26AiJob, payload: String, now: Long) {
        val parts = payload.split("|", limit = 2)
        if (parts.size != 2) return
        val key = M26AiOperationsService.canonicalDuplicateKey(parts[0], parts[1])
        if (key in store.duplicateKeys.value) return
        store.duplicateKeys.value = store.duplicateKeys.value + key
        store.duplicateCandidates.value += M26DuplicateCandidate(
            store.nextId("m26_dup"),
            job.ownerUserId,
            parts[0].trim(),
            parts[1].trim(),
            0.82,
            M26DuplicateStatus.OPEN,
            now,
            now
        )
    }

    private fun createAssistanceArtifact(job: M26AiJob, payload: String, now: Long) {
        store.assistanceSessions.value += M26AssistanceSession(
            store.nextId("m26_assist"),
            job.ownerUserId,
            M26AssistanceTopic.GENERAL,
            M26AssistanceSessionStatus.ACTIVE,
            "Sesión stub: ${payload.take(200)}",
            now
        )
    }

    private fun createRecommendationArtifact(job: M26AiJob, payload: String, now: Long) {
        val title = payload.take(80).ifBlank { "Sugerencia generada" }
        store.recommendations.value += M26EvaluatedRecommendation(
            store.nextId("m26_rec"),
            job.ownerUserId,
            M26RecommendationKind.CONTENT,
            title,
            "Sugerencia automática pendiente de revisión humana.",
            humanReviewed = false,
            reviewerNote = null,
            status = M26RecommendationStatus.PENDING_REVIEW,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun defaultReasonCodes(type: M26AiJobType): List<M26ReasonCode> = when (type) {
        M26AiJobType.VISUAL_MATCH -> listOf(
            M26ReasonCode("SIMILAR_COLOR_PATTERN", "Patrón de color similar"),
            M26ReasonCode("SIMILAR_BODY_SHAPE", "Forma corporal aproximada")
        )
        M26AiJobType.DUPLICATE_SCAN -> listOf(
            M26ReasonCode("SHARED_PUBLIC_ATTRIBUTES", "Atributos públicos compartidos")
        )
        M26AiJobType.ASSISTANCE -> listOf(
            M26ReasonCode("RECENT_RELEVANCE", "Contexto reciente del usuario")
        )
        M26AiJobType.RECOMMENDATION -> listOf(
            M26ReasonCode("USER_SELECTED_PREFERENCE", "Preferencias declaradas")
        )
    }

    private fun buildResultSummary(job: M26AiJob, payload: String): String = when (job.jobType) {
        M26AiJobType.VISUAL_MATCH -> "Posible coincidencia visual (estimación): ${payload.replace("|", " ↔ ")}"
        M26AiJobType.DUPLICATE_SCAN -> "Candidato de duplicado detectado: ${payload.replace("|", " / ")}"
        M26AiJobType.ASSISTANCE -> "Asistencia no autoritativa generada."
        M26AiJobType.RECOMMENDATION -> "Recomendación sugerida: ${payload.take(120)}"
    }

    override suspend fun cancelJob(jobId: String): Result<M26AiJob> = mutate {
        val actor = requireActor()
        val job = store.jobs.value.firstOrNull { it.id == jobId && it.ownerUserId == actor } ?: fail("M26_JOB_NOT_FOUND")
        if (job.status == M26AiJobStatus.CANCELLED) return@mutate job
        M26JobLifecycle.validateJobTransition(job.status, M26AiJobStatus.CANCELLED)?.let(::fail)
        job.copy(status = M26AiJobStatus.CANCELLED, updatedAt = System.currentTimeMillis()).also { updated ->
            store.jobs.value = store.jobs.value.map { if (it.id == jobId) updated else it }
        }
    }

    override suspend fun submitResultForReview(resultId: String): Result<M26AiResult> = mutate {
        val actor = requireActor()
        val result = ownedResult(resultId, actor)
        if (result.status == M26AiResultStatus.PENDING_REVIEW) return@mutate result
        M26JobLifecycle.validateResultTransition(result.status, M26AiResultStatus.PENDING_REVIEW)?.let(::fail)
        result.copy(status = M26AiResultStatus.PENDING_REVIEW, updatedAt = System.currentTimeMillis()).also { updated ->
            store.results.value = store.results.value.map { if (it.id == resultId) updated else it }
        }
    }

    override suspend fun reviewResult(input: ReviewM26AiResultInput): Result<M26AiResult> = mutate {
        val reviewer = requireReviewer()
        val result = store.results.value.firstOrNull { it.id == input.resultId } ?: fail("M26_RESULT_NOT_FOUND")
        val target = when (input.decision) {
            M26ReviewDecision.APPROVED -> M26AiResultStatus.APPROVED
            M26ReviewDecision.REJECTED -> M26AiResultStatus.REJECTED
            M26ReviewDecision.ARCHIVE -> M26AiResultStatus.ARCHIVED
        }
        M26JobLifecycle.validateReviewDecision(result.status, input.decision)?.let {
            if (result.status == target) return@mutate result else fail(it)
        }
        M26JobLifecycle.validateResultTransition(result.status, target)?.let(::fail)
        result.copy(status = target, updatedAt = System.currentTimeMillis()).also { updated ->
            store.results.value = store.results.value.map { if (it.id == input.resultId) updated else it }
            if (updated.resultType == M26AiJobType.RECOMMENDATION && target == M26AiResultStatus.APPROVED) {
                syncRecommendationApproval(updated)
            }
        }
    }

    override suspend fun archiveResult(resultId: String): Result<M26AiResult> = reviewResult(
        ReviewM26AiResultInput(resultId, M26ReviewDecision.ARCHIVE, null)
    )

    override fun observeVisualMatches(): Flow<List<M26PublicVisualMatch>> =
        store.visualMatches.map { matches ->
            val actor = actorUserId()
            matches.filter { actor == null || it.requesterUserId == actor }
                .filter { it.status != M26VisualMatchStatus.EXPIRED }
                .map { it.toPublic() }
        }

    override fun observeDuplicateCandidates(): Flow<List<M26PublicDuplicateCandidate>> =
        store.duplicateCandidates.map { candidates ->
            val actor = actorUserId()
            candidates.filter { actor == null || it.ownerUserId == actor }
                .filter { it.status == M26DuplicateStatus.OPEN }
                .map { it.toPublic() }
        }

    override fun observeAssistanceSessions(): Flow<List<M26PublicAssistanceSession>> =
        store.assistanceSessions.map { sessions ->
            val actor = actorUserId() ?: return@map emptyList()
            sessions.filter { it.userId == actor }.map { it.toPublic() }
        }

    override fun observeEligibleRecommendations(): Flow<List<M26PublicRecommendation>> =
        store.recommendations.map { recs ->
            M26RecommendationEligibilityService.filterEligiblePublic(recs)
        }

    override suspend fun requestVisualMatch(input: RequestM26VisualMatchInput): Result<M26VisualMatchSuggestion> {
        M26AiValidators.validateVisualMatch(input.sourceLabel, input.targetLabel)?.let { code ->
            return M26AiErrors.failure(M26AiException(code, M26AiErrors.userMessage(code)))
        }
        return requestAnalysis(
            RequestM26AiJobInput(M26AiJobType.VISUAL_MATCH, "${input.sourceLabel}|${input.targetLabel}", null)
        ).mapCatching { job ->
            store.visualMatches.value.last { it.requesterUserId == job.ownerUserId && it.updatedAt >= job.createdAt }
        }
    }

    override suspend fun dismissVisualMatch(matchId: String): Result<Unit> = mutate {
        val actor = requireActor()
        val match = ownedMatch(matchId, actor)
        M26AiValidators.validateMatchTransition(match.status, M26VisualMatchStatus.REJECTED)?.let(::fail)
        updateMatch(match.copy(status = M26VisualMatchStatus.REJECTED, updatedAt = System.currentTimeMillis()))
        Unit
    }

    override suspend fun confirmDuplicate(candidateId: String): Result<Unit> = transitionDuplicate(candidateId, M26DuplicateStatus.CONFIRMED)

    override suspend fun dismissDuplicate(candidateId: String): Result<Unit> = transitionDuplicate(candidateId, M26DuplicateStatus.DISMISSED)

    override suspend fun startAssistanceSession(input: StartM26AssistanceInput): Result<M26AssistanceSession> = mutate {
        val actor = requireActor()
        M26AiValidators.validateAssistancePrompt(input.initialPrompt)?.let(::fail)
        val now = System.currentTimeMillis()
        M26AssistanceSession(
            store.nextId("m26_assist"),
            actor,
            input.topic,
            M26AssistanceSessionStatus.ACTIVE,
            "Sesión stub: ${input.initialPrompt.trim()}",
            now
        ).also { store.assistanceSessions.value += it }
    }

    override suspend fun closeAssistanceSession(sessionId: String): Result<Unit> = mutate {
        val actor = requireActor()
        val session = ownedSession(sessionId, actor)
        M26AiValidators.validateSessionClose(session.status)?.let(::fail)
        val now = System.currentTimeMillis()
        store.assistanceSessions.value = store.assistanceSessions.value.map {
            if (it.id == sessionId) it.copy(status = M26AssistanceSessionStatus.CLOSED, closedAt = now) else it
        }
        Unit
    }

    override suspend fun submitRecommendation(input: SubmitM26RecommendationInput): Result<M26EvaluatedRecommendation> = mutate {
        val actor = requireActor()
        M26AiValidators.validateRecommendation(input.title, input.rationale)?.let(::fail)
        val now = System.currentTimeMillis()
        M26EvaluatedRecommendation(
            store.nextId("m26_rec"),
            actor,
            input.kind,
            input.title.trim(),
            input.rationale.trim(),
            humanReviewed = false,
            reviewerNote = null,
            status = M26RecommendationStatus.PENDING_REVIEW,
            createdAt = now,
            updatedAt = now
        ).also { store.recommendations.value += it }
    }

    override suspend fun reviewRecommendation(input: ReviewM26RecommendationInput): Result<M26EvaluatedRecommendation> = mutate {
        val actor = requireReviewer()
        val recommendation = store.recommendations.value.firstOrNull { it.id == input.recommendationId }
            ?: fail("M26_RECOMMENDATION_NOT_FOUND")
        val target = if (input.approved) M26RecommendationStatus.APPROVED else M26RecommendationStatus.REJECTED
        M26AiValidators.validateReviewTransition(recommendation.status, input.approved)?.let(::fail)
        recommendation.copy(
            humanReviewed = true,
            reviewerNote = input.reviewerNote?.trim(),
            status = target,
            updatedAt = System.currentTimeMillis()
        ).also { updated ->
            store.recommendations.value = store.recommendations.value.map { if (it.id == updated.id) updated else it }
        }
    }

    override fun observeNotificationsHook(): Flow<M26NotificationHookState> =
        kotlinx.coroutines.flow.flowOf(M26NotificationHookState())

    private suspend fun transitionDuplicate(candidateId: String, target: M26DuplicateStatus): Result<Unit> = mutate {
        val actor = requireActor()
        val candidate = ownedDuplicate(candidateId, actor)
        M26AiValidators.validateDuplicateTransition(candidate.status, target)?.let(::fail)
        store.duplicateCandidates.value = store.duplicateCandidates.value.map {
            if (it.id == candidateId) it.copy(status = target, updatedAt = System.currentTimeMillis()) else it
        }
        Unit
    }

    private fun updateMatch(updated: M26VisualMatchSuggestion) {
        store.visualMatches.value = store.visualMatches.value.map { if (it.id == updated.id) updated else it }
    }

    private fun confidenceBandFor(score: Double): M26ConfidenceBand = when {
        score >= 0.85 -> M26ConfidenceBand.HIGH
        score >= 0.60 -> M26ConfidenceBand.MEDIUM
        else -> M26ConfidenceBand.LOW
    }

    private fun isReviewer(): Boolean {
        val actor = actorUserId() ?: return false
        return actor == M26MockUsers.REVIEWER || actor == M26MockUsers.ADMIN
    }

    private fun ownedResult(resultId: String, actor: String): M26AiResult {
        val result = store.results.value.firstOrNull { it.id == resultId } ?: fail("M26_RESULT_NOT_FOUND")
        if (result.ownerUserId != actor) fail("M26_PERMISSION_DENIED")
        return result
    }

    private fun syncRecommendationApproval(result: M26AiResult) {
        val title = result.summary.removePrefix("Recomendación sugerida: ").trim()
        store.recommendations.value = store.recommendations.value.map { rec ->
            if (rec.subjectUserId == result.ownerUserId && rec.title == title.take(80)) {
                rec.copy(humanReviewed = true, status = M26RecommendationStatus.APPROVED, updatedAt = System.currentTimeMillis())
            } else rec
        }
    }

    private fun requireActor(): String = actorUserId() ?: fail("NOT_AUTHENTICATED")

    private fun requireReviewer(): String {
        val actor = requireActor()
        if (actor != M26MockUsers.REVIEWER && actor != M26MockUsers.ADMIN) fail("M26_PERMISSION_DENIED")
        return actor
    }

    private fun ownedMatch(matchId: String, actor: String): M26VisualMatchSuggestion {
        val match = store.visualMatches.value.firstOrNull { it.id == matchId } ?: fail("M26_MATCH_NOT_FOUND")
        if (match.requesterUserId != actor) fail("M26_PERMISSION_DENIED")
        return match
    }

    private fun ownedDuplicate(candidateId: String, actor: String): M26DuplicateCandidate {
        val candidate = store.duplicateCandidates.value.firstOrNull { it.id == candidateId } ?: fail("M26_DUPLICATE_NOT_FOUND")
        if (candidate.ownerUserId != actor) fail("M26_PERMISSION_DENIED")
        return candidate
    }

    private fun ownedSession(sessionId: String, actor: String): M26AssistanceSession {
        val session = store.assistanceSessions.value.firstOrNull { it.id == sessionId } ?: fail("M26_SESSION_NOT_FOUND")
        if (session.userId != actor) fail("M26_PERMISSION_DENIED")
        return session
    }

    private suspend fun <T> mutate(block: suspend () -> T): Result<T> = store.withLock {
        try {
            Result.success(block())
        } catch (error: Throwable) {
            M26AiErrors.failure(error)
        }
    }

    private fun fail(code: String): Nothing = throw M26AiException(code, M26AiErrors.userMessage(code))
}
