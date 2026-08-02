package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.BadgeType
import com.comunidapp.app.data.model.M21Appeal
import com.comunidapp.app.data.model.M21AppealStatus
import com.comunidapp.app.data.model.M21LicenseCredential
import com.comunidapp.app.data.model.M21MockTargetIds
import com.comunidapp.app.data.model.M21MockUsers
import com.comunidapp.app.data.model.M21PublicReputationSummary
import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21PublicVerification
import com.comunidapp.app.data.model.M21ReputationSummary
import com.comunidapp.app.data.model.M21Review
import com.comunidapp.app.data.model.M21ReviewStatus
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.M21VerificationRequest
import com.comunidapp.app.data.model.M21VerificationStatus
import com.comunidapp.app.data.model.M21VerificationType
import com.comunidapp.app.data.model.SubmitM21AppealInput
import com.comunidapp.app.data.model.SubmitM21ReviewInput
import com.comunidapp.app.data.model.SubmitM21VerificationInput
import com.comunidapp.app.data.model.UserBadge
import com.comunidapp.app.data.remote.supabase.m21.M21Exception
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
    suspend fun submitReview(input: SubmitM21ReviewInput): Result<M21PublicReview>
    suspend fun getMyVerifications(): Result<List<M21PublicVerification>>
    suspend fun submitVerification(input: SubmitM21VerificationInput): Result<M21PublicVerification>
    suspend fun submitAppeal(input: SubmitM21AppealInput): Result<Unit>
}

class M21ReputationMemoryStore {
    private val mutex = Mutex()
    private var seq = 0
    val reviews = MutableStateFlow<List<M21Review>>(emptyList())
    val verifications = MutableStateFlow<List<M21VerificationRequest>>(emptyList())
    val appeals = MutableStateFlow<List<M21Appeal>>(emptyList())
    val scores = MutableStateFlow<Map<String, Int>>(emptyMap())

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun nextId(prefix: String): String {
        seq += 1
        return "${prefix}_$seq"
    }

    fun seedDefaults() {
        if (reviews.value.isNotEmpty()) return
        val now = System.currentTimeMillis()
        reviews.value = listOf(
            M21Review(
                id = "m21_rev_1", targetType = M21ReviewTargetType.ADOPTION,
                targetId = M21MockTargetIds.ADOPTION, targetDisplayLabel = "Adopción Luna",
                reviewerUserId = M21MockUsers.REVIEWER, reviewerDisplayName = "Adoptante Martín",
                rating = 5, content = "Excelente acompañamiento durante la adopción.",
                status = M21ReviewStatus.PUBLISHED, createdAt = now - 86_400_000, updatedAt = now - 86_400_000
            ),
            M21Review(
                id = "m21_rev_2", targetType = M21ReviewTargetType.SERVICE,
                targetId = M21MockTargetIds.SERVICE, targetDisplayLabel = "Turno veterinario",
                reviewerUserId = M21MockUsers.ADMIN, reviewerDisplayName = "Usuario demo",
                rating = 4, content = "Buena atención, espera moderada.",
                status = M21ReviewStatus.PUBLISHED, createdAt = now - 172_800_000, updatedAt = now - 172_800_000
            ),
            M21Review(
                id = "m21_rev_3", targetType = M21ReviewTargetType.ORGANIZATION,
                targetId = M21MockTargetIds.ORGANIZATION, targetDisplayLabel = "Refugio Comunitario Norte",
                reviewerUserId = M21MockUsers.REVIEWER, reviewerDisplayName = "Voluntaria Ana",
                rating = 5, content = "Organización transparente y comprometida.",
                status = M21ReviewStatus.PUBLISHED, createdAt = now - 259_200_000, updatedAt = now - 259_200_000
            ),
            M21Review(
                id = "m21_rev_hidden", targetType = M21ReviewTargetType.USER,
                targetId = M21MockUsers.ORG_MANAGER, targetDisplayLabel = "Gestor refugio",
                reviewerUserId = M21MockUsers.ADMIN, reviewerDisplayName = "Usuario demo",
                rating = 2, content = "Reseña oculta por moderación.",
                status = M21ReviewStatus.HIDDEN, createdAt = now - 300_000, updatedAt = now - 300_000
            )
        )
        verifications.value = listOf(
            M21VerificationRequest(
                id = "m21_ver_id", userId = M21MockUsers.ADMIN,
                verificationType = M21VerificationType.IDENTITY,
                status = M21VerificationStatus.APPROVED,
                displayLabel = "Identidad verificada",
                submittedAt = now - 500_000, reviewedAt = now - 400_000
            ),
            M21VerificationRequest(
                id = "m21_ver_lic", userId = M21MockUsers.ORG_MANAGER,
                verificationType = M21VerificationType.PROFESSIONAL_LICENSE,
                status = M21VerificationStatus.PENDING,
                displayLabel = "Matrícula veterinaria",
                licenseCredential = M21LicenseCredential("MV-12345", "Colegio Médico Vet", "CABA"),
                submittedAt = now - 100_000
            )
        )
        scores.value = mapOf(
            M21MockUsers.ADMIN to 120,
            M21MockUsers.REVIEWER to 55,
            M21MockUsers.ORG_MANAGER to 80,
            M21MockUsers.EMPTY_PROFILE to 0
        )
    }
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

    private fun summaryFor(userId: String): M21ReputationSummary {
        val published = store.reviews.value.filter {
            it.status == M21ReviewStatus.PUBLISHED &&
                (it.reviewerUserId == userId || it.targetId == userId)
        }
        val myPublished = store.reviews.value.filter {
            it.reviewerUserId == userId && it.status == M21ReviewStatus.PUBLISHED
        }
        val score = store.scores.value[userId] ?: 0
        val verifications = store.verifications.value.filter { it.userId == userId }
        return M21ReputationSummary(
            userId = userId,
            reputationScore = score,
            publishedReviewCount = myPublished.size,
            averageRating = myPublished.takeIf { it.isNotEmpty() }?.map { it.rating }?.average(),
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
            }
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
            list.filter { it.targetType == type && it.targetId == targetId && it.status == M21ReviewStatus.PUBLISHED }
                .map { it.toPublicReview(isOwnReview = it.reviewerUserId == actor) }
        }

    override fun observeMyReviews(): Flow<List<M21PublicReview>> =
        store.reviews.map { list ->
            val actor = actorUserId() ?: return@map emptyList()
            list.filter { it.reviewerUserId == actor }
                .map { it.toPublicReview(isOwnReview = true) }
        }

    override suspend fun submitReview(input: SubmitM21ReviewInput): Result<M21PublicReview> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                M21ReputationValidators.validateReviewContent(input.content, input.rating)?.let { failM21(it) }
                val dup = store.reviews.value.any {
                    it.reviewerUserId == actor &&
                        it.targetType == input.targetType &&
                        it.targetId == input.targetId &&
                        it.status != M21ReviewStatus.REMOVED
                }
                if (dup) failM21("M21_DUPLICATE_REVIEW")
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
                    updatedAt = now
                )
                store.reviews.value = store.reviews.value + review
                val newScore = (store.scores.value[actor] ?: 0) + 5
                store.scores.value = store.scores.value + (actor to newScore)
                review.toPublicReview(isOwnReview = true)
            }.fold(
                onSuccess = { Result.success(it) },
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
                    submittedAt = now
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
                if (review.reviewerUserId != actor) failM21("M21_PERMISSION_DENIED")
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
                    if (it.id == input.reviewId) it.copy(status = M21ReviewStatus.APPEALED) else it
                }
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { M21ReputationErrors.failure(it) }
            )
        }
}

private fun failM21(code: String): Nothing =
    throw M21Exception(code, M21ReputationErrors.userMessage(code))
