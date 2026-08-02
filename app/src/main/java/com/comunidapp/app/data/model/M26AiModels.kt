package com.comunidapp.app.data.model

import com.comunidapp.app.domain.m26.M26PrivacySanitizer

/** LeoVer M26 — Inteligencia asistida (Bloque 1 local; sin pagos ni M24). */
enum class M26AiJobType { VISUAL_MATCH, DUPLICATE_SCAN, ASSISTANCE, RECOMMENDATION }
enum class M26AiJobStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, EXPIRED }
enum class M26AiResultStatus { DRAFT, PENDING_REVIEW, APPROVED, REJECTED, ARCHIVED }
enum class M26ReviewDecision { APPROVED, REJECTED, ARCHIVE }
enum class M26VisualMatchStatus { PENDING, ACCEPTED, REJECTED, EXPIRED }
enum class M26ConfidenceBand { LOW, MEDIUM, HIGH }
enum class M26DuplicateStatus { OPEN, CONFIRMED, DISMISSED }
enum class M26AssistanceTopic { GENERAL, ADOPTION, LOST_PET, MARKETPLACE, OTHER }
enum class M26AssistanceSessionStatus { ACTIVE, CLOSED, EXPIRED }
enum class M26RecommendationKind { CONTENT, PROVIDER, PRODUCT, EVENT, OTHER }
enum class M26RecommendationStatus { DRAFT, PENDING_REVIEW, APPROVED, REJECTED, EXPIRED }

data class M26ModelDescriptor(val name: String, val version: String)
data class M26AiProvenance(val sourceModule: String, val jobId: String?, val createdAt: Long)
data class M26ReasonCode(val code: String, val publicExplanation: String)

data class M26AiJob(
    val id: String,
    val ownerUserId: String,
    val jobType: M26AiJobType,
    val status: M26AiJobStatus,
    val clientRequestId: String?,
    val model: M26ModelDescriptor,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val errorCode: String? = null
)

data class M26AiResult(
    val id: String,
    val jobId: String,
    val ownerUserId: String,
    val resultType: M26AiJobType,
    val status: M26AiResultStatus,
    val summary: String,
    val reasonCodes: List<M26ReasonCode>,
    val model: M26ModelDescriptor,
    val provenance: M26AiProvenance,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublicSummary(): M26PublicAiResultSummary = M26PrivacySanitizer.toPublicResult(this)
}

data class M26HumanReview(
    val id: String,
    val resultId: String,
    val reviewerUserId: String,
    val decision: M26ReviewDecision,
    val publicReason: String?,
    val createdAt: Long
)

data class M26PublicAiResultSummary(
    val summary: String,
    val resultType: M26AiJobType,
    val status: M26AiResultStatus,
    val reasonCodes: List<String>,
    val modelName: String,
    val modelVersion: String,
    val isEstimate: Boolean = true
)

data class M26PublicReviewQueueItem(
    val resultId: String,
    val summary: String,
    val resultType: M26AiJobType,
    val status: M26AiResultStatus,
    val modelVersion: String
)

/** Stub M06 — delivery infrastructure is not coupled to M26 operations. */
data class M26NotificationHookState(
    val available: Boolean = false,
    val matchSuggested: Boolean = false,
    val duplicateDetected: Boolean = false,
    val recommendationReviewed: Boolean = false,
    val message: String = "M26_NOTIFICATIONS_UNAVAILABLE"
)

data class M26VisualMatchSuggestion(
    val id: String,
    val requesterUserId: String,
    val sourceLabel: String,
    val targetLabel: String,
    val score: Double,
    val confidenceBand: M26ConfidenceBand,
    val status: M26VisualMatchStatus,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublic(): M26PublicVisualMatch = M26PrivacySanitizer.toPublicVisualMatch(this)
}

data class M26DuplicateCandidate(
    val id: String,
    val ownerUserId: String,
    val primaryLabel: String,
    val duplicateLabel: String,
    val similarityScore: Double,
    val status: M26DuplicateStatus,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublic(): M26PublicDuplicateCandidate = M26PrivacySanitizer.toPublicDuplicate(this)
}

data class M26AssistanceSession(
    val id: String,
    val userId: String,
    val topic: M26AssistanceTopic,
    val status: M26AssistanceSessionStatus,
    val summary: String,
    val createdAt: Long,
    val closedAt: Long? = null
) {
    fun toPublic(): M26PublicAssistanceSession = M26PrivacySanitizer.toPublicAssistance(this)
}

data class M26EvaluatedRecommendation(
    val id: String,
    val subjectUserId: String,
    val kind: M26RecommendationKind,
    val title: String,
    val rationale: String,
    val humanReviewed: Boolean,
    val reviewerNote: String? = null,
    val status: M26RecommendationStatus,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublic(): M26PublicRecommendation = M26PrivacySanitizer.toPublicRecommendation(this)
}

data class M26PublicVisualMatch(
    val sourceLabel: String,
    val targetLabel: String,
    val score: Double,
    val confidenceBand: M26ConfidenceBand,
    val status: M26VisualMatchStatus
)

data class M26PublicDuplicateCandidate(
    val primaryLabel: String,
    val duplicateLabel: String,
    val similarityScore: Double,
    val status: M26DuplicateStatus
)

data class M26PublicAssistanceSession(
    val topic: M26AssistanceTopic,
    val status: M26AssistanceSessionStatus,
    val summary: String
)

data class M26PublicRecommendation(
    val title: String,
    val rationale: String,
    val kind: M26RecommendationKind,
    val humanReviewed: Boolean,
    val approvedForDisplay: Boolean
)

data class RequestM26VisualMatchInput(
    val sourceLabel: String,
    val targetLabel: String
)

data class StartM26AssistanceInput(
    val topic: M26AssistanceTopic,
    val initialPrompt: String
)

data class SubmitM26RecommendationInput(
    val kind: M26RecommendationKind,
    val title: String,
    val rationale: String
)

data class ReviewM26RecommendationInput(
    val recommendationId: String,
    val approved: Boolean,
    val reviewerNote: String? = null
)

data class RequestM26AiJobInput(
    val jobType: M26AiJobType,
    val payloadSummary: String,
    val clientRequestId: String? = null
)

data class ReviewM26AiResultInput(
    val resultId: String,
    val decision: M26ReviewDecision,
    val publicReason: String? = null
)

object M26MockUsers {
    const val ADMIN = "mock_user_admin"
    const val MEMBER = "mock_user_member"
    const val REVIEWER = "mock_user_reviewer"
    const val OTHER = "mock_user_other"
    const val UNAUTHORIZED = "mock_user_unauthorized"
}

object M26MockIds {
    const val MATCH_HIGH = "m26_match_high"
    const val MATCH_PENDING = "m26_match_pending"
    const val DUPLICATE_OPEN = "m26_dup_open"
    const val ASSISTANCE_ACTIVE = "m26_assist_active"
    const val RECOMMENDATION_APPROVED = "m26_rec_approved"
    const val RECOMMENDATION_PENDING = "m26_rec_pending"
}
