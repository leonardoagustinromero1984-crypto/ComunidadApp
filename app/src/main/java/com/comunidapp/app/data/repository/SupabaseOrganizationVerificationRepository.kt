package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeArray
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeObject
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.longFromIso
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.requireString
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.runRpc
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.string
import com.comunidapp.app.domain.organization.OrganizationVerificationStatus
import com.comunidapp.app.domain.verification.OrganizationVerificationDecision
import com.comunidapp.app.domain.verification.OrganizationVerificationReview
import com.comunidapp.app.domain.verification.OrganizationVerificationReviewStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseOrganizationVerificationRepository : OrganizationVerificationRepository {

    override suspend fun listPendingVerificationRequests(): AppResult<List<OrganizationVerificationReview>> =
        runRpc {
            val element = supabase.postgrest.rpc(
                function = "list_organization_verification_queue",
                parameters = buildJsonObject { put("p_limit", 50) }
            ).decodeAs<JsonElement>()
            decodeArray(element).mapNotNull { item ->
                decodeObject(item)?.let(::parseReview)
            }
        }

    override suspend fun getVerificationReview(reviewId: String): AppResult<OrganizationVerificationReview> =
        runRpc {
            val element = supabase.postgrest.rpc(
                function = "get_organization_verification_review",
                parameters = buildJsonObject { put("p_review_id", reviewId) }
            ).decodeAs<JsonElement>()
            val root = decodeObject(element) ?: throw IllegalStateException("NOT_FOUND")
            val reviewObj = root["review"]?.let { decodeObject(it) }
                ?: throw IllegalStateException("NOT_FOUND")
            val noteOverride = root.string("review_note")
            parseReview(reviewObj).let { review ->
                if (noteOverride != null) review.copy(reviewNote = noteOverride) else review
            }
        }

    override suspend fun assignVerificationReview(
        reviewId: String,
        assigneeUserId: String,
        nowEpochMs: Long
    ): AppResult<OrganizationVerificationReview> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "assign_organization_verification_review",
            parameters = buildJsonObject {
                put("p_review_id", reviewId)
                put("p_assigned_to_user_id", assigneeUserId)
            }
        ).decodeAs<JsonElement>()
        val obj = decodeObject(element) ?: throw IllegalStateException("ASSIGN_REVIEW_EMPTY")
        parseReview(obj)
    }

    override suspend fun recordVerificationDecision(
        reviewId: String,
        currentOrgStatus: OrganizationVerificationStatus,
        decision: OrganizationVerificationDecision,
        reviewNote: String?,
        actorUserId: String,
        actorIsOrgMember: Boolean,
        nowEpochMs: Long
    ): AppResult<OrganizationVerificationReview> = runRpc {
        // Server enforces conflict / permission; client params except decision/note are ignored.
        val element = supabase.postgrest.rpc(
            function = "record_organization_verification_decision",
            parameters = buildJsonObject {
                put("p_review_id", reviewId)
                put("p_decision", decision.name)
                reviewNote?.let { put("p_review_note", it) }
            }
        ).decodeAs<JsonElement>()
        val root = decodeObject(element) ?: throw IllegalStateException("DECISION_EMPTY")
        val reviewObj = root["review"]?.let { decodeObject(it) }
            ?: throw IllegalStateException("DECISION_EMPTY")
        parseReview(reviewObj)
    }

    private fun parseReview(obj: JsonObject): OrganizationVerificationReview {
        val created = obj.longFromIso("created_at")
        val updated = obj.longFromIso("updated_at", created)
        val status = runCatching {
            OrganizationVerificationReviewStatus.valueOf(
                obj.string("status")?.trim()?.uppercase().orEmpty()
            )
        }.getOrDefault(OrganizationVerificationReviewStatus.PENDING_REVIEW)
        val decision = obj.string("decision")?.let { raw ->
            runCatching { OrganizationVerificationDecision.valueOf(raw.trim().uppercase()) }.getOrNull()
        }
        return OrganizationVerificationReview(
            id = obj.requireString("id"),
            organizationId = obj.string("organization_id").orEmpty(),
            requestedByUserId = obj.string("requested_by_user_id").orEmpty(),
            assignedToUserId = obj.string("assigned_to_user_id"),
            status = status,
            decision = decision,
            reviewNote = obj.string("review_note"),
            createdAtEpochMs = created,
            updatedAtEpochMs = updated,
            decidedAtEpochMs = obj.string("decided_at")?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            decidedByUserId = obj.string("decided_by_user_id")
        )
    }
}
