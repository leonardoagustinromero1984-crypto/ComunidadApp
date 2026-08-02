package com.comunidapp.app.data.remote.supabase.m21

import com.comunidapp.app.data.model.M21PublicReputationSummary
import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21PublicVerification
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private fun JsonObject.string(key: String): String? =
    (this[key]?.toString()?.trim('"'))?.takeIf { it.isNotBlank() }

fun JsonObject.toM21PublicReview(): M21PublicReview = M21PublicReview(
    id = string("id").orEmpty(),
    targetType = runCatching { M21ReviewTargetType.valueOf(string("target_type").orEmpty()) }
        .getOrDefault(M21ReviewTargetType.SERVICE),
    targetDisplayLabel = string("target_display_label").orEmpty(),
    reviewerDisplayName = string("reviewer_display_name").orEmpty(),
    rating = this["rating"]?.toString()?.trim('"')?.toIntOrNull() ?: 0,
    content = string("content").orEmpty(),
    status = runCatching {
        com.comunidapp.app.data.model.M21ReviewStatus.valueOf(string("status").orEmpty())
    }.getOrDefault(com.comunidapp.app.data.model.M21ReviewStatus.PUBLISHED),
    createdAt = string("created_at")?.let {
        runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
    } ?: System.currentTimeMillis(),
    isOwnReview = string("is_own_review") == "true"
)

fun JsonObject.toM21PublicVerification(): M21PublicVerification = M21PublicVerification(
    id = string("id").orEmpty(),
    verificationType = runCatching {
        com.comunidapp.app.data.model.M21VerificationType.valueOf(string("verification_type").orEmpty())
    }.getOrDefault(com.comunidapp.app.data.model.M21VerificationType.IDENTITY),
    status = runCatching {
        com.comunidapp.app.data.model.M21VerificationStatus.valueOf(string("status").orEmpty())
    }.getOrDefault(com.comunidapp.app.data.model.M21VerificationStatus.PENDING),
    displayLabel = string("display_label").orEmpty(),
    licenseSummary = string("license_summary"),
    submittedAt = string("submitted_at")?.let {
        runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
    },
    isOwnRequest = string("is_own_request") == "true"
)

fun JsonObject.toM21PublicSummary(): M21PublicReputationSummary = M21PublicReputationSummary(
    reputationScore = this["reputation_score"]?.toString()?.trim('"')?.toIntOrNull() ?: 0,
    publishedReviewCount = this["published_review_count"]?.toString()?.trim('"')?.toIntOrNull() ?: 0,
    averageRating = this["average_rating"]?.toString()?.trim('"')?.toDoubleOrNull(),
    badges = emptyList(),
    identityVerified = string("identity_verified") == "true",
    licenseVerified = string("license_verified") == "true"
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

    suspend fun submitReview(
        targetType: String,
        targetId: String,
        targetDisplayLabel: String,
        rating: Int,
        content: String
    ): JsonObject = decodeOne(
        "m21_submit_review",
        buildJsonObject {
            put("p_target_type", targetType)
            put("p_target_id", targetId)
            put("p_target_display_label", targetDisplayLabel)
            put("p_rating", rating)
            put("p_content", content)
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
