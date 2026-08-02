package com.comunidapp.app.data.remote.supabase.m21

import com.comunidapp.app.data.model.M21PublicReputationSummary
import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21PublicReviewResponse
import com.comunidapp.app.data.model.M21PublicVerification
import com.comunidapp.app.data.model.M21RatingDistribution
import com.comunidapp.app.data.model.M21ReputationBreakdown
import com.comunidapp.app.data.model.M21ReviewContextReference
import com.comunidapp.app.data.model.M21ReviewContextType
import com.comunidapp.app.data.model.M21ReviewEligibility
import com.comunidapp.app.data.model.M21ReviewEligibilityReason
import com.comunidapp.app.data.model.M21ReviewResponseStatus
import com.comunidapp.app.data.model.M21ReviewStatus
import com.comunidapp.app.data.model.M21ReviewSubjectReference
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.M21VerificationStatus
import com.comunidapp.app.data.model.M21VerificationType
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private fun parseTs(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private fun JsonElement?.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonElement?.asIntOrNull(default: Int = 0): Int =
    (this as? JsonPrimitive)?.intOrNull ?: default

private fun JsonElement?.asDoubleOrNull(): Double? =
    (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

private fun JsonElement?.asBooleanOrNull(default: Boolean = false): Boolean =
    when (val p = this as? JsonPrimitive) {
        null -> default
        else -> when (p.contentOrNull?.lowercase()) {
            "true", "t", "1" -> true
            "false", "f", "0" -> false
            else -> default
        }
    }

private fun JsonObject.string(key: String): String? = this[key].asStringOrNull()

private fun JsonObject.int(key: String, default: Int = 0): Int = this[key].asIntOrNull(default)

private fun JsonObject.boolean(key: String, default: Boolean = false): Boolean =
    this[key].asBooleanOrNull(default)

private fun JsonObject.double(key: String): Double? = this[key].asDoubleOrNull()

private fun safeEnumReviewTargetType(raw: String?): M21ReviewTargetType =
    runCatching { M21ReviewTargetType.valueOf(raw.orEmpty()) }
        .getOrDefault(M21ReviewTargetType.SERVICE)

private fun safeEnumReviewStatus(raw: String?): M21ReviewStatus =
    runCatching { M21ReviewStatus.valueOf(raw.orEmpty()) }
        .getOrDefault(M21ReviewStatus.PUBLISHED)

private fun safeEnumReviewContextType(raw: String?): M21ReviewContextType? =
    runCatching { M21ReviewContextType.valueOf(raw.orEmpty()) }.getOrNull()

private fun safeEnumEligibilityReason(raw: String?): M21ReviewEligibilityReason =
    runCatching { M21ReviewEligibilityReason.valueOf(raw.orEmpty()) }
        .getOrDefault(M21ReviewEligibilityReason.NOT_ELIGIBLE)

private fun safeEnumReviewResponseStatus(raw: String?): M21ReviewResponseStatus =
    runCatching { M21ReviewResponseStatus.valueOf(raw.orEmpty()) }
        .getOrDefault(M21ReviewResponseStatus.PUBLISHED)

fun JsonObject.toM21ReviewSubjectReference(): M21ReviewSubjectReference = M21ReviewSubjectReference(
    targetType = safeEnumReviewTargetType(string("target_type")),
    targetId = string("target_id").orEmpty(),
    displayLabel = string("display_label").orEmpty()
)

fun JsonObject.toM21ReviewContextReference(): M21ReviewContextReference? {
    val contextType = safeEnumReviewContextType(string("context_type")) ?: return null
    val contextId = string("context_id") ?: return null
    return M21ReviewContextReference(
        contextType = contextType,
        contextId = contextId,
        publicLabel = string("public_label").orEmpty()
    )
}

fun JsonObject.toM21PublicReviewResponse(): M21PublicReviewResponse? {
    if (this.isEmpty()) return null
    return M21PublicReviewResponse(
        id = string("id").orEmpty(),
        content = string("content").orEmpty(),
        status = safeEnumReviewResponseStatus(string("status")),
        createdAt = parseTs(string("created_at")),
        updatedAt = parseTs(string("updated_at"))
    )
}

fun JsonObject.toM21PublicReview(): M21PublicReview = M21PublicReview(
    id = string("id").orEmpty(),
    targetType = safeEnumReviewTargetType(string("target_type")),
    targetDisplayLabel = string("target_display_label").orEmpty(),
    reviewerDisplayName = string("reviewer_display_name").orEmpty(),
    rating = int("rating"),
    content = string("content").orEmpty(),
    status = safeEnumReviewStatus(string("status")),
    createdAt = parseTs(string("created_at")),
    isOwnReview = boolean("is_own_review"),
    title = string("title"),
    hasResponse = boolean("has_response"),
    publicResponse = (this["public_response"] as? JsonObject)?.toM21PublicReviewResponse(),
    eligibleExperienceBadge = string("eligible_experience_badge")
)

fun JsonObject.toM21ReviewEligibility(): M21ReviewEligibility = M21ReviewEligibility(
    eligible = boolean("eligible"),
    reason = safeEnumEligibilityReason(string("reason")),
    subject = (this["subject"] as? JsonObject)?.toM21ReviewSubjectReference()
        ?: M21ReviewSubjectReference(M21ReviewTargetType.SERVICE, "", ""),
    contextReference = (this["context_reference"] as? JsonObject)?.toM21ReviewContextReference()
)

fun JsonObject.toM21RatingDistribution(): M21RatingDistribution = M21RatingDistribution(
    oneStar = int("one_star"),
    twoStars = int("two_stars"),
    threeStars = int("three_stars"),
    fourStars = int("four_stars"),
    fiveStars = int("five_stars")
)

fun JsonObject.toM21ReputationBreakdown(): M21ReputationBreakdown {
    val subjectObj = this["subject"] as? JsonObject
    val reviewsElement = this["reviews"]
    val reviews = if (reviewsElement is JsonArray) {
        reviewsElement.mapNotNull { (it as? JsonObject)?.toM21PublicReview() }
    } else {
        emptyList()
    }
    return M21ReputationBreakdown(
        subject = subjectObj?.toM21ReviewSubjectReference()
            ?: M21ReviewSubjectReference(M21ReviewTargetType.SERVICE, "", ""),
        averageRating = double("average_rating"),
        publishedReviewCount = int("published_review_count"),
        ratingDistribution = (this["rating_distribution"] as? JsonObject)?.toM21RatingDistribution()
            ?: M21RatingDistribution(),
        reviewsWithResponseCount = int("reviews_with_response_count"),
        lastReviewAt = string("last_review_at")?.let { parseTs(it) },
        reviews = reviews
    )
}

fun JsonObject.toM21PublicVerification(): M21PublicVerification = M21PublicVerification(
    id = string("id").orEmpty(),
    verificationType = runCatching {
        M21VerificationType.valueOf(string("verification_type").orEmpty())
    }.getOrDefault(M21VerificationType.IDENTITY),
    status = runCatching {
        M21VerificationStatus.valueOf(string("status").orEmpty())
    }.getOrDefault(M21VerificationStatus.PENDING),
    displayLabel = string("display_label").orEmpty(),
    licenseSummary = string("license_summary"),
    submittedAt = string("submitted_at")?.let { parseTs(it) },
    isOwnRequest = boolean("is_own_request")
)

fun JsonObject.toM21PublicSummary(): M21PublicReputationSummary = M21PublicReputationSummary(
    reputationScore = int("reputation_score"),
    publishedReviewCount = int("published_review_count"),
    averageRating = double("average_rating"),
    badges = emptyList(),
    identityVerified = boolean("identity_verified"),
    licenseVerified = boolean("license_verified")
)

class SupabaseM21RemoteDataSource {

    private suspend inline fun <reified T : Any> decodeOne(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeSingle()

    private suspend inline fun <reified T : Any> decodeList(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeList()

    suspend fun getMySummary(): JsonObject = decodeOne("m21_get_my_reputation_summary", buildJsonObject {})

    suspend fun listMyReviews(): List<JsonObject> = decodeList("m21_list_my_reviews", buildJsonObject {})

    suspend fun listReviewsForTarget(type: String, targetId: String): List<JsonObject> = decodeList(
        "m21_list_reviews_for_target",
        buildJsonObject {
            put("p_target_type", type)
            put("p_target_id", targetId)
        }
    )

    suspend fun checkEligibility(
        targetType: String,
        targetId: String,
        targetDisplayLabel: String,
        contextType: String? = null,
        contextId: String? = null,
        contextPublicLabel: String? = null
    ): JsonObject = decodeOne(
        "m21_check_eligibility",
        buildJsonObject {
            put("p_target_type", targetType)
            put("p_target_id", targetId)
            put("p_target_display_label", targetDisplayLabel)
            put("p_context_type", contextType)
            put("p_context_id", contextId)
            put("p_context_public_label", contextPublicLabel)
        }
    )

    suspend fun getSubjectBreakdown(targetType: String, targetId: String): JsonObject = decodeOne(
        "m21_get_subject_breakdown",
        buildJsonObject {
            put("p_target_type", targetType)
            put("p_target_id", targetId)
        }
    )

    suspend fun getReviewDetail(reviewId: String): JsonObject = decodeOne(
        "m21_get_review_detail",
        buildJsonObject { put("p_review_id", reviewId) }
    )

    suspend fun submitReview(
        targetType: String,
        targetId: String,
        targetDisplayLabel: String,
        rating: Int,
        content: String,
        title: String? = null,
        contextType: String? = null,
        contextId: String? = null,
        contextPublicLabel: String? = null
    ): JsonObject = decodeOne(
        "m21_submit_review",
        buildJsonObject {
            put("p_target_type", targetType)
            put("p_target_id", targetId)
            put("p_target_display_label", targetDisplayLabel)
            put("p_rating", rating)
            put("p_content", content)
            put("p_title", title)
            put("p_context_type", contextType)
            put("p_context_id", contextId)
            put("p_context_public_label", contextPublicLabel)
        }
    )

    suspend fun editReview(
        reviewId: String,
        rating: Int? = null,
        content: String? = null,
        title: String? = null
    ): JsonObject = decodeOne(
        "m21_edit_review",
        buildJsonObject {
            put("p_review_id", reviewId)
            put("p_rating", rating)
            put("p_content", content)
            put("p_title", title)
        }
    )

    suspend fun archiveReview(reviewId: String): JsonObject = decodeOne(
        "m21_archive_review",
        buildJsonObject { put("p_review_id", reviewId) }
    )

    suspend fun submitReviewResponse(reviewId: String, content: String): JsonObject = decodeOne(
        "m21_submit_review_response",
        buildJsonObject {
            put("p_review_id", reviewId)
            put("p_content", content)
        }
    )

    suspend fun submitDispute(
        reviewId: String,
        reason: String,
        details: String,
        evidenceRef: String? = null
    ): JsonObject = decodeOne(
        "m21_submit_dispute",
        buildJsonObject {
            put("p_review_id", reviewId)
            put("p_reason", reason)
            put("p_details", details)
            put("p_evidence_ref", evidenceRef)
        }
    )

    suspend fun reportReview(
        reviewId: String,
        reason: String,
        details: String? = null,
        reportResponse: Boolean = false
    ): JsonObject = decodeOne(
        "m21_report_review",
        buildJsonObject {
            put("p_review_id", reviewId)
            put("p_reason", reason)
            put("p_details", details)
            put("p_report_response", reportResponse)
        }
    )

    suspend fun listMyVerifications(): List<JsonObject> =
        decodeList("m21_list_my_verifications", buildJsonObject {})

    suspend fun submitVerification(
        verificationType: String,
        displayLabel: String,
        licenseNumber: String?,
        issuingAuthority: String?,
        jurisdiction: String?
    ): JsonObject = decodeOne(
        "m21_submit_verification",
        buildJsonObject {
            put("p_verification_type", verificationType)
            put("p_display_label", displayLabel)
            put("p_license_number", licenseNumber)
            put("p_issuing_authority", issuingAuthority)
            put("p_jurisdiction", jurisdiction)
        }
    )

    suspend fun submitAppeal(reviewId: String, reason: String): JsonObject = decodeOne(
        "m21_submit_appeal",
        buildJsonObject {
            put("p_review_id", reviewId)
            put("p_reason", reason)
        }
    )
}
