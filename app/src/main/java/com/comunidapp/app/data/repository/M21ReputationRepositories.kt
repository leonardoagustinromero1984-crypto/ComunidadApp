package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.BadgeType
import com.comunidapp.app.data.model.CheckM21EligibilityInput
import com.comunidapp.app.data.model.EditM21ReviewInput
import com.comunidapp.app.data.model.M21Appeal
import com.comunidapp.app.data.model.M21AppealStatus
import com.comunidapp.app.data.model.M21LicenseCredential
import com.comunidapp.app.data.model.M21MockEligibilityIds
import com.comunidapp.app.data.model.M21MockTargetIds
import com.comunidapp.app.data.model.M21MockUsers
import com.comunidapp.app.data.model.M21NotificationHookState
import com.comunidapp.app.data.model.M21PublicReputationSummary
import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21PublicReviewResponse
import com.comunidapp.app.data.model.M21PublicVerification
import com.comunidapp.app.data.model.M21ReputationBreakdown
import com.comunidapp.app.data.model.M21ReputationSummary
import com.comunidapp.app.data.model.M21Review
import com.comunidapp.app.data.model.M21ReviewContextReference
import com.comunidapp.app.data.model.M21ReviewContextType
import com.comunidapp.app.data.model.M21ReviewDispute
import com.comunidapp.app.data.model.M21ReviewDisputeReason
import com.comunidapp.app.data.model.M21ReviewDisputeStatus
import com.comunidapp.app.data.model.M21ReviewEligibility
import com.comunidapp.app.data.model.M21ReviewEligibilityReason
import com.comunidapp.app.data.model.M21ReviewResponse
import com.comunidapp.app.data.model.M21ReviewResponseStatus
import com.comunidapp.app.data.model.M21ReviewRiskReason
import com.comunidapp.app.data.model.M21ReviewRiskSignal
import com.comunidapp.app.data.model.M21ReviewStatus
import com.comunidapp.app.data.model.M21ReviewSubjectReference
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.M21VerificationRequest
import com.comunidapp.app.data.model.M21VerificationStatus
import com.comunidapp.app.data.model.M21VerificationType
import com.comunidapp.app.data.model.ReportM21ReviewInput
import com.comunidapp.app.data.model.SubmitM21AppealInput
import com.comunidapp.app.data.model.SubmitM21DisputeInput
import com.comunidapp.app.data.model.SubmitM21ReviewInput
import com.comunidapp.app.data.model.SubmitM21ReviewResponseInput
import com.comunidapp.app.data.model.SubmitM21VerificationInput
import com.comunidapp.app.data.model.UserBadge
import com.comunidapp.app.data.remote.supabase.m21.M21Exception
import com.comunidapp.app.domain.m21.M21PrivacySanitizer
import com.comunidapp.app.domain.m21.M21ReputationAggregator
import com.comunidapp.app.domain.m21.M21ReviewEligibilityService
import com.comunidapp.app.ui.components.defaultBadgesForScore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface M21ReputationRepository {
    fun observeMySummary(): Flow<M21PublicReputationSummary>
    fun observeReviewsForTarget(type: M21ReviewTargetType, targetId: String): Flow<List<M21PublicReview>>
    fun observeMyReviews(): Flow<List<M21PublicReview>>
    fun observeSubjectBreakdown(type: M21ReviewTargetType, targetId: String): Flow<M21ReputationBreakdown>
    fun observeNotificationsHook(): Flow<M21NotificationHookState>
    suspend fun checkEligibility(input: CheckM21EligibilityInput): Result<M21ReviewEligibility>
    suspend fun getReviewDetail(reviewId: String): Result<M21PublicReview>
    suspend fun submitReview(input: SubmitM21ReviewInput): Result<M21PublicReview>
    suspend fun editReview(input: EditM21ReviewInput): Result<M21PublicReview>
    suspend fun archiveReview(reviewId: String): Result<Unit>
    suspend fun submitReviewResponse(input: SubmitM21ReviewResponseInput): Result<M21PublicReviewResponse>
    suspend fun reportReview(input: ReportM21ReviewInput): Result<Unit>
    suspend fun submitDispute(input: SubmitM21DisputeInput): Result<Unit>
    suspend fun getMyVerifications(): Result<List<M21PublicVerification>>
    suspend fun submitVerification(input: SubmitM21VerificationInput): Result<M21PublicVerification>
    suspend fun submitAppeal(input: SubmitM21AppealInput): Result<Unit>
}

class M21ReputationMemoryStore {
    private val mutex = Mutex()
    private var seq = 0
    val reviews = MutableStateFlow<List<M21Review>>(emptyList())
    val responses = MutableStateFlow<List<M21ReviewResponse>>(emptyList())
    val verifications = MutableStateFlow<List<M21VerificationRequest>>(emptyList())
    val appeals = MutableStateFlow<List<M21Appeal>>(emptyList())
    val disputes = MutableStateFlow<List<M21ReviewDispute>>(emptyList())
    val eligibilityRecords = MutableStateFlow<List<M21EligibilityRecord>>(emptyList())
    val riskSignals = MutableStateFlow<List<M21ReviewRiskSignal>>(emptyList())
    val scores = MutableStateFlow<Map<String, Int>>(emptyMap())

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun nextId(prefix: String): String {
        seq += 1
        return "${prefix}_$seq"
    }

    fun seedDefaults() {
        if (reviews.value.isNotEmpty()) return
        val now = System.currentTimeMillis()
        eligibilityRecords.value = M21EligibilityAdapter.allRecords()

        val ctxAdoption = context(M21ReviewContextType.ADOPTION_COMPLETED, M21MockEligibilityIds.ADOPTION_COMPLETED, "Adopción completada")
        val ctxService = context(M21ReviewContextType.SERVICE_COMPLETED, M21MockEligibilityIds.SERVICE_COMPLETED, "Servicio completado")
        val ctxShelter = context(M21ReviewContextType.SHELTER_INTERACTION, M21MockEligibilityIds.SHELTER_INTERACTION, "Voluntariado confirmado")
        val ctxDuplicate = context(M21ReviewContextType.SERVICE_COMPLETED, M21MockEligibilityIds.DUPLICATE_CONTEXT, "Contexto ya reseñado")

        reviews.value = listOf(
            // 1. Reseña publicada positiva
            review(
                id = "m21_rev_positive", type = M21ReviewTargetType.ADOPTION, targetId = M21MockTargetIds.ADOPTION,
                label = "Adopción Luna", reviewer = M21MockUsers.REVIEWER, name = "Adoptante Martín",
                rating = 5, content = "Excelente acompañamiento durante la adopción.",
                status = M21ReviewStatus.PUBLISHED, createdAt = now - 86_400_000, ctx = ctxAdoption
            ),
            // 2. Reseña publicada crítica
            review(
                id = "m21_rev_critical", type = M21ReviewTargetType.SERVICE, targetId = M21MockTargetIds.SERVICE,
                label = "Turno veterinario", reviewer = M21MockUsers.ADMIN, name = "Usuario demo",
                rating = 2, content = "Espera larga y poca comunicación.",
                status = M21ReviewStatus.PUBLISHED, createdAt = now - 80_000_000, ctx = ctxService
            ),
            // 3. Reseña con respuesta (hasResponse=true, response seeded below)
            review(
                id = "m21_rev_with_response", type = M21ReviewTargetType.ORGANIZATION, targetId = M21MockTargetIds.ORGANIZATION,
                label = "Refugio Comunitario Norte", reviewer = M21MockUsers.REVIEWER, name = "Voluntaria Ana",
                rating = 4, content = "Buen refugio, falta más difusión.",
                status = M21ReviewStatus.PUBLISHED, createdAt = now - 75_000_000, ctx = ctxShelter, hasResponse = true
            ),
            // 4. Reseña editada
            review(
                id = "m21_rev_edited", type = M21ReviewTargetType.SERVICE, targetId = M21MockTargetIds.SERVICE,
                label = "Turno veterinario", reviewer = M21MockUsers.REVIEWER, name = "Voluntaria Ana",
                rating = 4, content = "Atención mejorada tras el seguimiento.",
                status = M21ReviewStatus.EDITED, createdAt = now - 70_000_000, ctx = ctxService,
                editCount = 1, title = "Segunda opinión"
            ),
            // 5. Reseña archivada
            review(
                id = "m21_rev_archived", type = M21ReviewTargetType.DONATION, targetId = M21MockTargetIds.DONATION,
                label = "Campaña solidaria", reviewer = M21MockUsers.ADMIN, name = "Usuario demo",
                rating = 5, content = "Archivada por el autor.",
                status = M21ReviewStatus.ARCHIVED, createdAt = now - 65_000_000,
                ctx = context(M21ReviewContextType.DONATION_COMPLETED, M21MockEligibilityIds.DONATION_COMPLETED, "Donación confirmada")
            ),
            // 6. Reseña moderada
            review(
                id = "m21_rev_moderated", type = M21ReviewTargetType.USER, targetId = M21MockUsers.ORG_MANAGER,
                label = "Gestor refugio", reviewer = M21MockUsers.ADMIN, name = "Usuario demo",
                rating = 1, content = "Removida por moderación.",
                status = M21ReviewStatus.REMOVED_BY_MODERATION, createdAt = now - 60_000_000,
                ctx = context(M21ReviewContextType.SUPPORT_CONVERSATION, M21MockEligibilityIds.NOT_ELIGIBLE_VISIT, "Soporte")
            ),
            // 7. Reseña disputada
            review(
                id = "m21_rev_disputed", type = M21ReviewTargetType.ORGANIZATION, targetId = M21MockTargetIds.ORGANIZATION,
                label = "Refugio Comunitario Norte", reviewer = M21MockUsers.ADMIN, name = "Usuario demo",
                rating = 2, content = "Disputada por el refugio.",
                status = M21ReviewStatus.DISPUTED, createdAt = now - 55_000_000, ctx = ctxShelter
            ),
            // 8. Contexto duplicado (existing review for DUPLICATE_CONTEXT)
            review(
                id = "m21_rev_duplicate_ctx", type = M21ReviewTargetType.SERVICE, targetId = M21MockTargetIds.SERVICE,
                label = "Turno veterinario", reviewer = M21MockUsers.REVIEWER, name = "Voluntaria Ana",
                rating = 5, content = "Primera reseña del contexto duplicado.",
                status = M21ReviewStatus.PUBLISHED, createdAt = now - 50_000_000, ctx = ctxDuplicate
            ),
            // Hidden legacy
            review(
                id = "m21_rev_hidden", type = M21ReviewTargetType.USER, targetId = M21MockUsers.ORG_MANAGER,
                label = "Gestor refugio", reviewer = M21MockUsers.ADMIN, name = "Usuario demo",
                rating = 2, content = "Reseña oculta por moderación.",
                status = M21ReviewStatus.HIDDEN, createdAt = now - 300_000,
                ctx = context(M21ReviewContextType.SUPPORT_CONVERSATION, "mock_ctx_hidden", "Oculta")
            ),
            // Draft (not public)
            review(
                id = "m21_rev_draft", type = M21ReviewTargetType.ADOPTION, targetId = M21MockTargetIds.ADOPTION,
                label = "Adopción Luna", reviewer = M21MockUsers.ADMIN, name = "Usuario demo",
                rating = 5, content = "Borrador privado.",
                status = M21ReviewStatus.DRAFT, createdAt = now - 100_000, ctx = ctxAdoption
            )
        )

        responses.value = listOf(
            M21ReviewResponse(
                id = "m21_resp_1",
                reviewId = "m21_rev_with_response",
                responderUserId = M21MockUsers.ORG_MANAGER,
                content = "Gracias por tu aporte como voluntaria.",
                status = M21ReviewResponseStatus.PUBLISHED,
                createdAt = now - 74_000_000,
                updatedAt = now - 74_000_000
            )
        )

        disputes.value = listOf(
            M21ReviewDispute(
                id = "m21_dispute_1",
                reviewId = "m21_rev_disputed",
                claimantUserId = M21MockUsers.ORG_MANAGER,
                reason = M21ReviewDisputeReason.FACTUAL_ERROR,
                details = "Los hechos mencionados no coinciden con nuestros registros internos.",
                status = M21ReviewDisputeStatus.OPEN,
                createdAt = now - 54_000_000,
                evidenceRef = "m05_private_evidence_m21_1"
            )
        )

        riskSignals.value = listOf(
            M21ReviewRiskSignal(
                reviewId = "m21_rev_moderated",
                reason = M21ReviewRiskReason.INELIGIBLE_CONTEXT,
                detectedAt = now - 59_000_000,
                explanation = "Contexto no elegible detectado en mock"
            )
        )

        verifications.value = listOf(
            // 12. pending
            M21VerificationRequest(
                id = "m21_ver_pending", userId = M21MockUsers.ORG_MANAGER,
                verificationType = M21VerificationType.PROFESSIONAL_LICENSE,
                status = M21VerificationStatus.PENDING,
                displayLabel = "Matrícula veterinaria",
                licenseCredential = M21LicenseCredential("MV-12345", "Colegio Médico Vet", "CABA"),
                submittedAt = now - 100_000
            ),
            // 13. approved
            M21VerificationRequest(
                id = "m21_ver_approved", userId = M21MockUsers.ADMIN,
                verificationType = M21VerificationType.IDENTITY,
                status = M21VerificationStatus.APPROVED,
                displayLabel = "Identidad verificada",
                submittedAt = now - 500_000, reviewedAt = now - 400_000
            ),
            // 14. rejected (internal reason not public)
            M21VerificationRequest(
                id = "m21_ver_rejected", userId = M21MockUsers.REVIEWER,
                verificationType = M21VerificationType.IDENTITY,
                status = M21VerificationStatus.REJECTED,
                displayLabel = "Verificación identidad",
                submittedAt = now - 200_000, reviewedAt = now - 150_000,
                rejectionReason = "Documento ilegible — nota interna"
            ),
            // 15. expired
            M21VerificationRequest(
                id = "m21_ver_expired", userId = M21MockUsers.SERVICE_PROVIDER,
                verificationType = M21VerificationType.PROFESSIONAL_LICENSE,
                status = M21VerificationStatus.EXPIRED,
                displayLabel = "Matrícula expirada",
                licenseCredential = M21LicenseCredential("MV-99999", "Colegio Vet", "BA"),
                submittedAt = now - 900_000_000, reviewedAt = now - 800_000_000
            ),
            // 16. revoked
            M21VerificationRequest(
                id = "m21_ver_revoked", userId = M21MockUsers.ORG_MANAGER,
                verificationType = M21VerificationType.IDENTITY,
                status = M21VerificationStatus.REVOKED,
                displayLabel = "Identidad revocada",
                submittedAt = now - 700_000, reviewedAt = now - 600_000
            ),
            // 17. evidence private
            M21VerificationRequest(
                id = "m21_ver_evidence", userId = M21MockUsers.REVIEWER,
                verificationType = M21VerificationType.PROFESSIONAL_LICENSE,
                status = M21VerificationStatus.UNDER_REVIEW,
                displayLabel = "Matrícula en revisión",
                licenseCredential = M21LicenseCredential("MV-77777", "Colegio Vet", "CABA"),
                submittedAt = now - 50_000,
                evidenceRef = "m05_private_cert_m21_1"
            )
        )

        scores.value = mapOf(
            M21MockUsers.ADMIN to 120,
            M21MockUsers.REVIEWER to 55,
            M21MockUsers.ORG_MANAGER to 80,
            M21MockUsers.EMPTY_PROFILE to 0,
            M21MockUsers.SERVICE_PROVIDER to 30
        )
    }

    private fun context(type: M21ReviewContextType, id: String, label: String) =
        M21ReviewContextReference(type, id, label)

    private fun review(
        id: String,
        type: M21ReviewTargetType,
        targetId: String,
        label: String,
        reviewer: String,
        name: String,
        rating: Int,
        content: String,
        status: M21ReviewStatus,
        createdAt: Long,
        ctx: M21ReviewContextReference,
        editCount: Int = 0,
        title: String? = null,
        hasResponse: Boolean = false
    ) = M21Review(
        id = id,
        targetType = type,
        targetId = targetId,
        targetDisplayLabel = label,
        reviewerUserId = reviewer,
        reviewerDisplayName = name,
        rating = rating,
        content = content,
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
        title = title,
        contextReference = ctx,
        editCount = editCount,
        hasResponse = hasResponse
    )
}

class MockM21ReputationRepository(
    private val actorUserId: () -> String?,
    private val store: M21ReputationMemoryStore = M21ReputationMemoryStore()
) : M21ReputationRepository {

    init {
        store.seedDefaults()
    }

    private fun requireActor(): String =
        actorUserId() ?: throw M21Exception("NOT_AUTHENTICATED", M21ReputationErrors.userMessage("NOT_AUTHENTICATED"))

    private fun subjectOf(type: M21ReviewTargetType, targetId: String, label: String) =
        M21ReviewSubjectReference(type, targetId, label)

    private fun subjectOwner(review: M21Review): String = when (review.targetType) {
        M21ReviewTargetType.USER -> review.targetId
        M21ReviewTargetType.ORGANIZATION -> M21MockUsers.ORG_MANAGER
        M21ReviewTargetType.SERVICE -> M21MockUsers.SERVICE_PROVIDER
        M21ReviewTargetType.ADOPTION -> M21MockUsers.ORG_MANAGER
        M21ReviewTargetType.DONATION -> M21MockUsers.ORG_MANAGER
    }

    private fun publicResponseFor(reviewId: String): M21PublicReviewResponse? =
        M21ReputationAggregator.activeResponse(store.responses.value, reviewId)
            ?.toPublicResponse()

    private fun toPublic(review: M21Review, actor: String?): M21PublicReview {
        val response = if (review.hasResponse) publicResponseFor(review.id) else null
        return review.toPublicReview(
            isOwnReview = review.reviewerUserId == actor,
            publicResponse = response
        )
    }

    private fun isPublic(review: M21Review): Boolean =
        M21PrivacySanitizer.isPublicReviewStatus(review.status)

    private fun reviewsForSubject(type: M21ReviewTargetType, targetId: String): List<M21Review> =
        store.reviews.value.filter { it.targetType == type && it.targetId == targetId }

    private fun summaryFor(userId: String): M21ReputationSummary {
        val received = store.reviews.value.filter {
            subjectOwner(it) == userId && M21ReputationAggregator.isCountable(it)
        }
        val myPublished = store.reviews.value.filter {
            it.reviewerUserId == userId && M21ReputationAggregator.isCountable(it)
        }
        val score = store.scores.value[userId] ?: 0
        val verifications = store.verifications.value.filter { it.userId == userId }
        return M21ReputationSummary(
            userId = userId,
            reputationScore = score,
            publishedReviewCount = received.size,
            averageRating = M21ReputationAggregator.averageRating(received),
            badges = defaultBadgesForScore(score).ifEmpty {
                if (score > 0) listOf(UserBadge("", "", BadgeType.SOLIDARY_HOME)) else emptyList()
            },
            identityVerified = verifications.any {
                it.verificationType == M21VerificationType.IDENTITY &&
                    it.status == M21VerificationStatus.APPROVED
            },
            licenseVerified = verifications.any {
                it.verificationType == M21VerificationType.PROFESSIONAL_LICENSE &&
                    it.status == M21VerificationStatus.APPROVED
            },
            ratingDistribution = M21ReputationAggregator.ratingDistribution(received),
            reviewsWithResponseCount = M21ReputationAggregator.reviewsWithResponseCount(received),
            lastReviewAt = M21ReputationAggregator.lastReviewAt(received)
        )
    }

    override fun observeMySummary(): Flow<M21PublicReputationSummary> =
        store.reviews.map {
            val actor = actorUserId() ?: M21MockUsers.ADMIN
            summaryFor(actor).toPublicSummary()
        }

    override fun observeReviewsForTarget(type: M21ReviewTargetType, targetId: String): Flow<List<M21PublicReview>> =
        store.reviews.map { list ->
            val actor = actorUserId()
            list.filter { it.targetType == type && it.targetId == targetId && isPublic(it) }
                .map { toPublic(it, actor) }
        }

    override fun observeSubjectBreakdown(type: M21ReviewTargetType, targetId: String): Flow<M21ReputationBreakdown> =
        store.reviews.map { list ->
            val actor = actorUserId()
            val subjectReviews = list.filter { it.targetType == type && it.targetId == targetId }
            val public = subjectReviews.filter { isPublic(it) }.map { toPublic(it, actor) }
            val subject = subjectOf(type, targetId, public.firstOrNull()?.targetDisplayLabel ?: type.name)
            M21ReputationAggregator.buildBreakdown(subject, subjectReviews, public)
        }

    override fun observeNotificationsHook(): Flow<M21NotificationHookState> =
        kotlinx.coroutines.flow.flowOf(M21NotificationHookState(available = false))

    override suspend fun checkEligibility(input: CheckM21EligibilityInput): Result<M21ReviewEligibility> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val subject = subjectOf(input.targetType, input.targetId, input.targetDisplayLabel)
                M21ReviewEligibilityService.evaluate(
                    reviewerUserId = actor,
                    subject = subject,
                    contextReference = input.contextReference,
                    existingReviews = store.reviews.value,
                    additionalEligibilityRecords = store.eligibilityRecords.value
                )
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M21ReputationErrors.failure(it) }
            )
        }

    override suspend fun getReviewDetail(reviewId: String): Result<M21PublicReview> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val review = store.reviews.value.firstOrNull { it.id == reviewId }
                    ?: failM21("M21_REVIEW_NOT_FOUND")
                if (review.status == M21ReviewStatus.DRAFT && review.reviewerUserId != actor) {
                    failM21("M21_PERMISSION_DENIED")
                }
                if (!isPublic(review) && review.reviewerUserId != actor && subjectOwner(review) != actor) {
                    failM21("M21_PERMISSION_DENIED")
                }
                toPublic(review, actor)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M21ReputationErrors.failure(it) }
            )
        }

    override fun observeMyReviews(): Flow<List<M21PublicReview>> =
        store.reviews.map { list ->
            val actor = actorUserId() ?: return@map emptyList()
            list.filter { it.reviewerUserId == actor }
                .map { toPublic(it, actor) }
        }

    override suspend fun submitReview(input: SubmitM21ReviewInput): Result<M21PublicReview> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                M21ReputationValidators.validateReviewContent(input.content, input.rating)?.let { failM21(it) }
                M21ReputationValidators.validateReviewTitle(input.title)?.let { failM21(it) }
                M21ReputationValidators.validateSelfReview(actor, input.targetType, input.targetId)?.let { failM21(it) }

                val subject = subjectOf(input.targetType, input.targetId, input.targetDisplayLabel)
                val eligibility = M21ReviewEligibilityService.evaluate(
                    reviewerUserId = actor,
                    subject = subject,
                    contextReference = input.contextReference,
                    existingReviews = store.reviews.value,
                    additionalEligibilityRecords = store.eligibilityRecords.value
                )
                if (!eligibility.eligible) {
                    val code = when (eligibility.reason) {
                        M21ReviewEligibilityReason.ELIGIBILITY_UNAVAILABLE -> "M21_REVIEW_ELIGIBILITY_UNAVAILABLE"
                        M21ReviewEligibilityReason.SELF_REVIEW -> "M21_SELF_REVIEW"
                        M21ReviewEligibilityReason.ALREADY_REVIEWED -> "M21_DUPLICATE_REVIEW"
                        else -> "M21_NOT_ELIGIBLE"
                    }
                    failM21(code)
                }

                val contextRef = input.contextReference ?: eligibility.contextReference
                    ?: failM21("M21_NOT_ELIGIBLE")

                val now = System.currentTimeMillis()
                val review = M21Review(
                    id = store.nextId("m21_rev"),
                    targetType = input.targetType,
                    targetId = input.targetId,
                    targetDisplayLabel = input.targetDisplayLabel.trim(),
                    reviewerUserId = actor,
                    reviewerDisplayName = "Usuario demo",
                    rating = input.rating,
                    content = input.content.trim(),
                    status = M21ReviewStatus.PUBLISHED,
                    createdAt = now,
                    updatedAt = now,
                    title = input.title?.trim(),
                    contextReference = contextRef
                )
                store.reviews.value = store.reviews.value + review
                val newScore = (store.scores.value[actor] ?: 0) + 5
                store.scores.value = store.scores.value + (actor to newScore)
                toPublic(review, actor)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M21ReputationErrors.failure(it) }
            )
        }

    override suspend fun editReview(input: EditM21ReviewInput): Result<M21PublicReview> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val review = store.reviews.value.firstOrNull { it.id == input.reviewId }
                    ?: failM21("M21_REVIEW_NOT_FOUND")
                M21ReputationValidators.validateEditReview(
                    actor, review.reviewerUserId, input.rating, input.content, input.title
                )?.let { failM21(it) }
                if (subjectOwner(review) == actor && review.reviewerUserId != actor) {
                    failM21("M21_PERMISSION_DENIED")
                }
                val now = System.currentTimeMillis()
                val updated = review.copy(
                    rating = input.rating ?: review.rating,
                    content = input.content?.trim() ?: review.content,
                    title = input.title?.trim() ?: review.title,
                    status = if (review.status == M21ReviewStatus.PUBLISHED) M21ReviewStatus.EDITED else review.status,
                    updatedAt = now,
                    editCount = review.editCount + 1
                )
                store.reviews.value = store.reviews.value.map { if (it.id == review.id) updated else it }
                toPublic(updated, actor)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M21ReputationErrors.failure(it) }
            )
        }

    override suspend fun archiveReview(reviewId: String): Result<Unit> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val review = store.reviews.value.firstOrNull { it.id == reviewId }
                    ?: failM21("M21_REVIEW_NOT_FOUND")
                if (review.reviewerUserId != actor) failM21("M21_PERMISSION_DENIED")
                if (review.status == M21ReviewStatus.ARCHIVED) return@runCatching
                store.reviews.value = store.reviews.value.map {
                    if (it.id == reviewId) it.copy(status = M21ReviewStatus.ARCHIVED, updatedAt = System.currentTimeMillis())
                    else it
                }
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { M21ReputationErrors.failure(it) }
            )
        }

    override suspend fun submitReviewResponse(input: SubmitM21ReviewResponseInput): Result<M21PublicReviewResponse> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                M21ReputationValidators.validateReviewResponse(input.content)?.let { failM21(it) }
                val review = store.reviews.value.firstOrNull { it.id == input.reviewId }
                    ?: failM21("M21_REVIEW_NOT_FOUND")
                if (review.reviewerUserId == actor) failM21("M21_PERMISSION_DENIED")
                if (subjectOwner(review) != actor) failM21("M21_PERMISSION_DENIED")
                if (!isPublic(review)) failM21("M21_PERMISSION_DENIED")

                val existing = M21ReputationAggregator.activeResponse(store.responses.value, review.id)
                val now = System.currentTimeMillis()
                val response = if (existing != null) {
                    existing.copy(
                        content = input.content.trim(),
                        status = M21ReviewResponseStatus.EDITED,
                        updatedAt = now,
                        editCount = existing.editCount + 1
                    )
                } else {
                    M21ReviewResponse(
                        id = store.nextId("m21_resp"),
                        reviewId = review.id,
                        responderUserId = actor,
                        content = input.content.trim(),
                        status = M21ReviewResponseStatus.PUBLISHED,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                store.responses.value = if (existing != null) {
                    store.responses.value.map { if (it.id == existing.id) response else it }
                } else {
                    store.responses.value + response
                }
                store.reviews.value = store.reviews.value.map {
                    if (it.id == review.id) it.copy(hasResponse = true, updatedAt = now) else it
                }
                response.toPublicResponse() ?: failM21("M21_INVALID_RESPONSE")
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M21ReputationErrors.failure(it) }
            )
        }

    override suspend fun reportReview(input: ReportM21ReviewInput): Result<Unit> =
        runCatching {
            val actor = requireActor()
            store.reviews.value.firstOrNull { it.id == input.reviewId }
                ?: failM21("M21_REVIEW_NOT_FOUND")
            if (input.reportResponse) {
                val response = M21ReputationAggregator.activeResponse(store.responses.value, input.reviewId)
                    ?: failM21("M21_REVIEW_NOT_FOUND")
                M21ReputationModerationAdapter.reportReviewResponse(
                    responseId = response.id,
                    reason = input.reason,
                    details = input.details,
                    reporterId = actor
                ).getOrThrow()
            } else {
                M21ReputationModerationAdapter.reportReview(
                    reviewId = input.reviewId,
                    reason = input.reason,
                    details = input.details,
                    reporterId = actor
                ).getOrThrow()
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { M21ReputationErrors.failure(it) }
        )

    override suspend fun submitDispute(input: SubmitM21DisputeInput): Result<Unit> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                M21ReputationValidators.validateDispute(input.details)?.let { failM21(it) }
                M21ReputationValidators.validateDisputeReason(input.reason)?.let { failM21(it) }
                val review = store.reviews.value.firstOrNull { it.id == input.reviewId }
                    ?: failM21("M21_REVIEW_NOT_FOUND")
                if (subjectOwner(review) != actor) failM21("M21_PERMISSION_DENIED")
                if (store.disputes.value.any {
                        it.reviewId == input.reviewId && it.status == M21ReviewDisputeStatus.OPEN
                    }
                ) {
                    failM21("M21_DISPUTE_EXISTS")
                }
                val dispute = M21ReviewDispute(
                    id = store.nextId("m21_dispute"),
                    reviewId = input.reviewId,
                    claimantUserId = actor,
                    reason = input.reason,
                    details = input.details.trim(),
                    status = M21ReviewDisputeStatus.OPEN,
                    createdAt = System.currentTimeMillis(),
                    evidenceRef = input.evidenceRef
                )
                store.disputes.value = store.disputes.value + dispute
                store.reviews.value = store.reviews.value.map {
                    if (it.id == input.reviewId && isPublic(it)) {
                        it.copy(status = M21ReviewStatus.DISPUTED, updatedAt = System.currentTimeMillis())
                    } else {
                        it
                    }
                }
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { M21ReputationErrors.failure(it) }
            )
        }

    override suspend fun getMyVerifications(): Result<List<M21PublicVerification>> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                store.verifications.value.filter { it.userId == actor }
                    .map { it.toPublicVerification(isOwn = true) }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M21ReputationErrors.failure(it) }
            )
        }

    override suspend fun submitVerification(input: SubmitM21VerificationInput): Result<M21PublicVerification> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                M21ReputationValidators.validateVerificationInput(
                    input.verificationType, input.displayLabel, input.licenseCredential
                )?.let { failM21(it) }
                val now = System.currentTimeMillis()
                val request = M21VerificationRequest(
                    id = store.nextId("m21_ver"),
                    userId = actor,
                    verificationType = input.verificationType,
                    status = M21VerificationStatus.PENDING,
                    displayLabel = input.displayLabel.trim(),
                    licenseCredential = input.licenseCredential,
                    submittedAt = now,
                    evidenceRef = input.evidenceRef
                )
                store.verifications.value = store.verifications.value + request
                request.toPublicVerification(isOwn = true)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M21ReputationErrors.failure(it) }
            )
        }

    override suspend fun submitAppeal(input: SubmitM21AppealInput): Result<Unit> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                M21ReputationValidators.validateAppealReason(input.reason)?.let { failM21(it) }
                val review = store.reviews.value.firstOrNull { it.id == input.reviewId }
                    ?: failM21("M21_REVIEW_NOT_FOUND")
                if (subjectOwner(review) != actor) failM21("M21_PERMISSION_DENIED")
                if (store.appeals.value.any { it.reviewId == input.reviewId && it.status == M21AppealStatus.OPEN }) {
                    failM21("M21_APPEAL_EXISTS")
                }
                val appeal = M21Appeal(
                    id = store.nextId("m21_appeal"),
                    reviewId = input.reviewId,
                    appellantUserId = actor,
                    reason = input.reason.trim(),
                    status = M21AppealStatus.OPEN,
                    createdAt = System.currentTimeMillis()
                )
                store.appeals.value = store.appeals.value + appeal
                store.reviews.value = store.reviews.value.map {
                    if (it.id == input.reviewId && isPublic(it)) {
                        it.copy(status = M21ReviewStatus.APPEALED, updatedAt = System.currentTimeMillis())
                    } else {
                        it
                    }
                }
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { M21ReputationErrors.failure(it) }
            )
        }
}

private fun failM21(code: String): Nothing =
    throw M21Exception(code, M21ReputationErrors.userMessage(code))
