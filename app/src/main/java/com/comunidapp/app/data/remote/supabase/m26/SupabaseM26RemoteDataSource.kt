package com.comunidapp.app.data.remote.supabase.m26

import com.comunidapp.app.data.model.M26AiJob
import com.comunidapp.app.data.model.M26AiJobStatus
import com.comunidapp.app.data.model.M26AiJobType
import com.comunidapp.app.data.model.M26AiProvenance
import com.comunidapp.app.data.model.M26AiResult
import com.comunidapp.app.data.model.M26AiResultStatus
import com.comunidapp.app.data.model.M26AssistanceSession
import com.comunidapp.app.data.model.M26ModelDescriptor
import com.comunidapp.app.data.model.M26PublicAiResultSummary
import com.comunidapp.app.data.model.M26PublicReviewQueueItem
import com.comunidapp.app.data.model.M26ReasonCode
import com.comunidapp.app.data.model.M26AssistanceSessionStatus
import com.comunidapp.app.data.model.M26AssistanceTopic
import com.comunidapp.app.data.model.M26ConfidenceBand
import com.comunidapp.app.data.model.M26DuplicateStatus
import com.comunidapp.app.data.model.M26EvaluatedRecommendation
import com.comunidapp.app.data.model.M26PublicAssistanceSession
import com.comunidapp.app.data.model.M26PublicDuplicateCandidate
import com.comunidapp.app.data.model.M26PublicRecommendation
import com.comunidapp.app.data.model.M26PublicVisualMatch
import com.comunidapp.app.data.model.M26RecommendationKind
import com.comunidapp.app.data.model.M26RecommendationStatus
import com.comunidapp.app.data.model.M26VisualMatchStatus
import com.comunidapp.app.data.model.M26VisualMatchSuggestion
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonElement?.doubleOrNull(): Double? =
    (this as? JsonPrimitive)?.doubleOrNull

private fun JsonElement?.boolean(default: Boolean = false): Boolean =
    (this as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: default

private fun JsonObject.string(key: String): String? = this[key].stringOrNull()
private fun JsonObject.double(key: String): Double? = this[key].doubleOrNull()
private fun JsonObject.boolean(key: String, default: Boolean = false): Boolean = this[key].boolean(default)

private fun parseTimestamp(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback

fun JsonObject.toM26PublicVisualMatch(): M26PublicVisualMatch = M26PublicVisualMatch(
    sourceLabel = string("source_label").orEmpty(),
    targetLabel = string("target_label").orEmpty(),
    score = double("score") ?: 0.0,
    confidenceBand = enumOr(string("confidence_band"), M26ConfidenceBand.LOW),
    status = enumOr(string("status"), M26VisualMatchStatus.PENDING)
)

fun JsonObject.toM26PublicDuplicate(): M26PublicDuplicateCandidate = M26PublicDuplicateCandidate(
    primaryLabel = string("primary_label").orEmpty(),
    duplicateLabel = string("duplicate_label").orEmpty(),
    similarityScore = double("similarity_score") ?: 0.0,
    status = enumOr(string("status"), M26DuplicateStatus.OPEN)
)

fun JsonObject.toM26PublicAssistance(): M26PublicAssistanceSession = M26PublicAssistanceSession(
    topic = enumOr(string("topic"), M26AssistanceTopic.GENERAL),
    status = enumOr(string("status"), M26AssistanceSessionStatus.ACTIVE),
    summary = string("summary").orEmpty()
)

fun JsonObject.toM26PublicRecommendation(): M26PublicRecommendation = M26PublicRecommendation(
    title = string("title").orEmpty(),
    rationale = string("rationale").orEmpty(),
    kind = enumOr(string("kind"), M26RecommendationKind.OTHER),
    humanReviewed = boolean("human_reviewed"),
    approvedForDisplay = boolean("approved_for_display")
)

fun JsonObject.toM26VisualMatchSuggestion(): M26VisualMatchSuggestion = M26VisualMatchSuggestion(
    id = string("id").orEmpty(),
    requesterUserId = string("requester_user_id").orEmpty(),
    sourceLabel = string("source_label").orEmpty(),
    targetLabel = string("target_label").orEmpty(),
    score = double("score") ?: 0.0,
    confidenceBand = enumOr(string("confidence_band"), M26ConfidenceBand.LOW),
    status = enumOr(string("status"), M26VisualMatchStatus.PENDING),
    createdAt = parseTimestamp(string("created_at")),
    updatedAt = parseTimestamp(string("updated_at"))
)

fun JsonObject.toM26AssistanceSession(): M26AssistanceSession = M26AssistanceSession(
    id = string("id").orEmpty(),
    userId = string("user_id").orEmpty(),
    topic = enumOr(string("topic"), M26AssistanceTopic.GENERAL),
    status = enumOr(string("status"), M26AssistanceSessionStatus.ACTIVE),
    summary = string("summary").orEmpty(),
    createdAt = parseTimestamp(string("created_at")),
    closedAt = string("closed_at")?.let(::parseTimestamp)
)

fun JsonObject.toM26EvaluatedRecommendation(): M26EvaluatedRecommendation = M26EvaluatedRecommendation(
    id = string("id").orEmpty(),
    subjectUserId = string("subject_user_id").orEmpty(),
    kind = enumOr(string("kind"), M26RecommendationKind.OTHER),
    title = string("title").orEmpty(),
    rationale = string("rationale").orEmpty(),
    humanReviewed = boolean("human_reviewed"),
    reviewerNote = string("reviewer_note"),
    status = enumOr(string("status"), M26RecommendationStatus.DRAFT),
    createdAt = parseTimestamp(string("created_at")),
    updatedAt = parseTimestamp(string("updated_at"))
)

fun JsonObject.toM26AiJob(): M26AiJob = M26AiJob(
    id = string("id").orEmpty(),
    ownerUserId = string("owner_user_id").orEmpty(),
    jobType = enumOr(string("job_type"), M26AiJobType.ASSISTANCE),
    status = enumOr(string("status"), M26AiJobStatus.QUEUED),
    clientRequestId = string("client_request_id"),
    model = M26ModelDescriptor(string("model_name").orEmpty(), string("model_version").orEmpty()),
    createdAt = parseTimestamp(string("created_at")),
    updatedAt = parseTimestamp(string("updated_at")),
    completedAt = string("completed_at")?.let(::parseTimestamp),
    errorCode = string("error_code")
)

fun JsonObject.toM26AiResult(): M26AiResult {
    val reasonRaw = this["reason_codes"]
    val codes = when (reasonRaw) {
        is JsonArray -> reasonRaw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let { c -> M26ReasonCode(c, c) } }
        else -> emptyList()
    }
    return M26AiResult(
        id = string("id").orEmpty(),
        jobId = string("job_id").orEmpty(),
        ownerUserId = string("owner_user_id").orEmpty(),
        resultType = enumOr(string("result_type"), M26AiJobType.ASSISTANCE),
        status = enumOr(string("status"), M26AiResultStatus.DRAFT),
        summary = string("summary").orEmpty(),
        reasonCodes = codes,
        model = M26ModelDescriptor(string("model_name").orEmpty(), string("model_version").orEmpty()),
        provenance = M26AiProvenance(
            string("source_module").orEmpty(),
            string("provenance_job_id"),
            parseTimestamp(string("created_at"))
        ),
        createdAt = parseTimestamp(string("created_at")),
        updatedAt = parseTimestamp(string("updated_at"))
    )
}

fun JsonObject.toM26PublicAiResultSummary(): M26PublicAiResultSummary {
    val reasonRaw = this["reason_codes"]
    val codes = when (reasonRaw) {
        is JsonArray -> reasonRaw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }
    return M26PublicAiResultSummary(
        summary = string("summary").orEmpty(),
        resultType = enumOr(string("result_type"), M26AiJobType.ASSISTANCE),
        status = enumOr(string("status"), M26AiResultStatus.DRAFT),
        reasonCodes = codes,
        modelName = string("model_name").orEmpty(),
        modelVersion = string("model_version").orEmpty(),
        isEstimate = boolean("is_estimate", true)
    )
}

fun JsonObject.toM26PublicReviewQueueItem(): M26PublicReviewQueueItem = M26PublicReviewQueueItem(
    resultId = string("result_id").orEmpty(),
    summary = string("summary").orEmpty(),
    resultType = enumOr(string("result_type"), M26AiJobType.ASSISTANCE),
    status = enumOr(string("status"), M26AiResultStatus.PENDING_REVIEW),
    modelVersion = string("model_version").orEmpty()
)

class SupabaseM26RemoteDataSource {
    private suspend inline fun <reified T : Any> one(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function, parameters).decodeSingle()

    private suspend inline fun <reified T : Any> list(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function, parameters).decodeList()

    suspend fun listVisualMatches(): List<JsonObject> =
        list("m26_list_visual_matches", buildJsonObject {})

    suspend fun listDuplicateCandidates(): List<JsonObject> =
        list("m26_list_duplicate_candidates", buildJsonObject {})

    suspend fun listAssistanceSessions(): List<JsonObject> =
        list("m26_list_assistance_sessions", buildJsonObject {})

    suspend fun listEligibleRecommendations(): List<JsonObject> =
        list("m26_list_eligible_recommendations", buildJsonObject {})

    suspend fun requestVisualMatch(sourceLabel: String, targetLabel: String): JsonObject =
        one("m26_request_visual_match", buildJsonObject {
            put("p_source_label", sourceLabel)
            put("p_target_label", targetLabel)
        })

    suspend fun dismissVisualMatch(matchId: String): JsonObject =
        one("m26_dismiss_visual_match", buildJsonObject { put("p_match_id", matchId) })

    suspend fun confirmDuplicate(candidateId: String): JsonObject =
        one("m26_confirm_duplicate", buildJsonObject { put("p_candidate_id", candidateId) })

    suspend fun dismissDuplicate(candidateId: String): JsonObject =
        one("m26_dismiss_duplicate", buildJsonObject { put("p_candidate_id", candidateId) })

    suspend fun startAssistanceSession(topic: String, initialPrompt: String): JsonObject =
        one("m26_start_assistance_session", buildJsonObject {
            put("p_topic", topic)
            put("p_initial_prompt", initialPrompt)
        })

    suspend fun closeAssistanceSession(sessionId: String): JsonObject =
        one("m26_close_assistance_session", buildJsonObject { put("p_session_id", sessionId) })

    suspend fun submitRecommendation(kind: String, title: String, rationale: String): JsonObject =
        one("m26_submit_recommendation", buildJsonObject {
            put("p_kind", kind)
            put("p_title", title)
            put("p_rationale", rationale)
        })

    suspend fun reviewRecommendation(recommendationId: String, approved: Boolean, reviewerNote: String?): JsonObject =
        one("m26_review_recommendation", buildJsonObject {
            put("p_recommendation_id", recommendationId)
            put("p_approved", approved)
            put("p_reviewer_note", reviewerNote)
        })

    suspend fun listMyJobs(): List<JsonObject> = list("m26_list_my_jobs", buildJsonObject {})

    suspend fun listMyResults(): List<JsonObject> = list("m26_list_my_results", buildJsonObject {})

    suspend fun listReviewQueue(): List<JsonObject> = list("m26_list_review_queue", buildJsonObject {})

    suspend fun requestAiJob(jobType: String, payloadSummary: String, clientRequestId: String?): JsonObject =
        one("m26_request_ai_job", buildJsonObject {
            put("p_job_type", jobType)
            put("p_payload_summary", payloadSummary)
            put("p_client_request_id", clientRequestId)
        })

    suspend fun cancelAiJob(jobId: String): JsonObject =
        one("m26_cancel_ai_job", buildJsonObject { put("p_job_id", jobId) })

    suspend fun submitResultForReview(resultId: String): JsonObject =
        one("m26_submit_result_for_review", buildJsonObject { put("p_result_id", resultId) })

    suspend fun reviewAiResult(resultId: String, decision: String, publicReason: String?): JsonObject =
        one("m26_review_ai_result", buildJsonObject {
            put("p_result_id", resultId)
            put("p_decision", decision)
            put("p_public_reason", publicReason)
        })
}
