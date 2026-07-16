package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeArray
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeObject
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.epochToIsoOrNull
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.longFromIso
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.parsePriority
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.parseReportStatus
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.parseTarget
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.requireString
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.runRpc
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.string
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.toRpcTargetType
import com.comunidapp.app.domain.moderation.ModerationAction
import com.comunidapp.app.domain.moderation.ModerationActionType
import com.comunidapp.app.domain.moderation.ModerationAppeal
import com.comunidapp.app.domain.moderation.ModerationAppealStatus
import com.comunidapp.app.domain.moderation.ModerationCase
import com.comunidapp.app.domain.moderation.ModerationCaseStatus
import com.comunidapp.app.domain.moderation.ModerationPriority
import com.comunidapp.app.domain.moderation.ModerationReport
import com.comunidapp.app.domain.moderation.ModerationReportRules
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseModerationRepository : ModerationRepository {

    override suspend fun createReport(
        reporterId: String,
        target: ModerationTargetRef,
        reasonCode: String,
        description: String?,
        nowEpochMs: Long
    ): AppResult<ModerationReport> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "create_content_report",
            parameters = buildJsonObject {
                put("p_target_type", toRpcTargetType(target.type))
                put("p_target_id", target.targetId.trim())
                put("p_reason_code", reasonCode.trim().lowercase())
                description?.let { put("p_description", it) }
            }
        ).decodeAs<JsonElement>()
        val obj = decodeObject(element)
            ?: throw IllegalStateException("CREATE_REPORT_EMPTY")
        parseReport(obj, fallbackReporterId = reporterId, fallbackNow = nowEpochMs)
    }

    override suspend fun getMyReports(reporterId: String): AppResult<List<ModerationReport>> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "get_my_content_reports",
            parameters = buildJsonObject { put("p_limit", 50) }
        ).decodeAs<JsonElement>()
        decodeArray(element).mapNotNull { item ->
            decodeObject(item)?.let { parseReport(it, fallbackReporterId = reporterId) }
        }
    }

    override suspend fun getReportForStaff(reportId: String): AppResult<ModerationReport> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "get_moderation_report_for_staff",
            parameters = buildJsonObject { put("p_report_id", reportId) }
        ).decodeAs<JsonElement>()
        val obj = decodeObject(element) ?: throw IllegalStateException("NOT_FOUND")
        parseReport(obj, fallbackReporterId = "")
    }

    override suspend fun listModerationQueue(): AppResult<List<ModerationReport>> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "list_moderation_queue",
            parameters = buildJsonObject { put("p_limit", 50) }
        ).decodeAs<JsonElement>()
        decodeArray(element).mapNotNull { item ->
            decodeObject(item)?.let { parseReport(it, fallbackReporterId = "") }
        }
    }

    override suspend fun createCase(
        title: String,
        createdByUserId: String,
        nowEpochMs: Long
    ): AppResult<ModerationCase> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "create_moderation_case",
            parameters = buildJsonObject {
                put("p_title", title)
                put("p_priority", ModerationPriority.NORMAL.name)
            }
        ).decodeAs<JsonElement>()
        val obj = decodeObject(element) ?: throw IllegalStateException("CREATE_CASE_EMPTY")
        parseCase(obj, fallbackCreator = createdByUserId, fallbackNow = nowEpochMs)
    }

    override suspend fun attachReportToCase(
        reportId: String,
        caseId: String,
        nowEpochMs: Long
    ): AppResult<Unit> = runRpc {
        supabase.postgrest.rpc(
            function = "attach_report_to_moderation_case",
            parameters = buildJsonObject {
                put("p_case_id", caseId)
                put("p_report_id", reportId)
            }
        ).decodeAs<JsonElement>()
        Unit
    }

    override suspend fun assignCase(
        caseId: String,
        assigneeUserId: String,
        nowEpochMs: Long
    ): AppResult<ModerationCase> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "assign_moderation_case",
            parameters = buildJsonObject {
                put("p_case_id", caseId)
                put("p_assigned_to_user_id", assigneeUserId)
            }
        ).decodeAs<JsonElement>()
        val obj = decodeObject(element) ?: throw IllegalStateException("ASSIGN_CASE_EMPTY")
        parseCase(obj, fallbackCreator = "", fallbackNow = nowEpochMs)
    }

    override suspend fun changeCaseStatus(
        caseId: String,
        status: ModerationCaseStatus,
        closeReasonCode: String?,
        nowEpochMs: Long
    ): AppResult<ModerationCase> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "change_moderation_case_status",
            parameters = buildJsonObject {
                put("p_case_id", caseId)
                put("p_status", status.name)
                closeReasonCode?.let { put("p_close_reason_code", it) }
            }
        ).decodeAs<JsonElement>()
        val obj = decodeObject(element) ?: throw IllegalStateException("CHANGE_CASE_EMPTY")
        parseCase(obj, fallbackCreator = "", fallbackNow = nowEpochMs)
    }

    override suspend fun recordAction(
        caseId: String,
        target: ModerationTargetRef,
        actionType: ModerationActionType,
        reasonCode: String,
        reasonDetail: String?,
        appliedByUserId: String,
        nowEpochMs: Long,
        expiresAtEpochMs: Long?
    ): AppResult<ModerationAction> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "apply_moderation_action",
            parameters = buildJsonObject {
                put("p_case_id", caseId)
                put("p_target_type", toRpcTargetType(target.type))
                put("p_target_id", target.targetId.trim())
                put("p_action_type", actionType.name)
                put("p_reason_code", reasonCode.trim().lowercase())
                reasonDetail?.let { put("p_reason_detail", it) }
                epochToIsoOrNull(expiresAtEpochMs)?.let { put("p_expires_at", it) }
                target.otherDescription?.let { put("p_other_description", it) }
            }
        ).decodeAs<JsonElement>()
        val obj = decodeObject(element) ?: throw IllegalStateException("APPLY_ACTION_EMPTY")
        parseAction(obj, fallbackActor = appliedByUserId, fallbackNow = nowEpochMs)
    }

    override suspend fun submitAppeal(
        actionId: String,
        submittedByUserId: String,
        statement: String,
        nowEpochMs: Long
    ): AppResult<ModerationAppeal> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "submit_moderation_appeal",
            parameters = buildJsonObject {
                put("p_action_id", actionId)
                put("p_statement", statement)
            }
        ).decodeAs<JsonElement>()
        val obj = decodeObject(element) ?: throw IllegalStateException("SUBMIT_APPEAL_EMPTY")
        parseAppeal(obj, fallbackSubmitter = submittedByUserId, fallbackNow = nowEpochMs)
    }

    override suspend fun listAppeals(): AppResult<List<ModerationAppeal>> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "list_moderation_appeals",
            parameters = buildJsonObject { put("p_limit", 50) }
        ).decodeAs<JsonElement>()
        decodeArray(element).mapNotNull { item ->
            decodeObject(item)?.let { parseAppeal(it, fallbackSubmitter = "", fallbackNow = 0L) }
        }
    }

    override suspend fun reviewAppeal(
        appealId: String,
        decision: ModerationAppealStatus,
        decisionReason: String,
        reviewerUserId: String,
        nowEpochMs: Long
    ): AppResult<ModerationAppeal> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "review_moderation_appeal",
            parameters = buildJsonObject {
                put("p_appeal_id", appealId)
                put("p_decision", decision.name)
                put("p_decision_reason", decisionReason)
            }
        ).decodeAs<JsonElement>()
        val obj = decodeObject(element) ?: throw IllegalStateException("REVIEW_APPEAL_EMPTY")
        parseAppeal(obj, fallbackSubmitter = "", fallbackNow = nowEpochMs)
    }

    private fun parseReport(
        obj: JsonObject,
        fallbackReporterId: String,
        fallbackNow: Long = 0L
    ): ModerationReport {
        val created = obj.longFromIso("created_at", fallbackNow)
        val updated = obj.longFromIso("updated_at", created)
        val reason = obj.string("reason_code")
            ?: obj.string("reason")
            ?: "other"
        val description = obj.string("reason_detail") ?: obj.string("details")
        return ModerationReport(
            id = obj.requireString("id"),
            reporterId = obj.string("reporter_id") ?: fallbackReporterId,
            target = parseTarget(
                obj.string("target_type"),
                obj.string("target_id").orEmpty(),
                obj.string("other_description")
            ),
            reasonCode = reason,
            description = description,
            priority = parsePriority(obj.string("priority")),
            status = parseReportStatus(obj.string("status")),
            createdAtEpochMs = created,
            updatedAtEpochMs = updated,
            caseId = obj.string("case_id"),
            duplicateOfReportId = obj.string("duplicate_of_report_id")
        )
    }

    private fun parseCase(
        obj: JsonObject,
        fallbackCreator: String,
        fallbackNow: Long
    ): ModerationCase {
        val created = obj.longFromIso("created_at", fallbackNow)
        val updated = obj.longFromIso("updated_at", created)
        val statusRaw = obj.string("status")?.trim()?.uppercase().orEmpty()
        val status = runCatching { ModerationCaseStatus.valueOf(statusRaw) }
            .getOrDefault(ModerationCaseStatus.OPEN)
        return ModerationCase(
            id = obj.requireString("id"),
            title = obj.string("title").orEmpty(),
            status = status,
            priority = parsePriority(obj.string("priority")),
            assignedToUserId = obj.string("assigned_to_user_id"),
            createdByUserId = obj.string("created_by_user_id") ?: fallbackCreator,
            createdAtEpochMs = created,
            updatedAtEpochMs = updated,
            closedAtEpochMs = obj.string("closed_at")?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            closeReasonCode = obj.string("close_reason_code")
        )
    }

    private fun parseAction(
        obj: JsonObject,
        fallbackActor: String,
        fallbackNow: Long
    ): ModerationAction {
        val appliedAt = obj.longFromIso("applied_at", fallbackNow)
        val actionType = runCatching {
            ModerationActionType.valueOf(obj.string("action_type")?.trim()?.uppercase().orEmpty())
        }.getOrDefault(ModerationActionType.NO_ACTION)
        return ModerationAction(
            id = obj.requireString("id"),
            caseId = obj.string("case_id").orEmpty(),
            target = parseTarget(
                obj.string("target_type"),
                obj.string("target_id").orEmpty(),
                obj.string("other_description")
            ),
            actionType = actionType,
            reasonCode = obj.string("reason_code").orEmpty(),
            reasonDetail = obj.string("reason_detail"),
            appliedByUserId = obj.string("applied_by_user_id") ?: fallbackActor,
            appliedAtEpochMs = appliedAt,
            expiresAtEpochMs = obj.string("expires_at")?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            reversedAtEpochMs = obj.string("reversed_at")?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            reversedByUserId = obj.string("reversed_by_user_id")
        )
    }

    private fun parseAppeal(
        obj: JsonObject,
        fallbackSubmitter: String,
        fallbackNow: Long
    ): ModerationAppeal {
        val statusRaw = obj.string("status")?.trim()?.uppercase().orEmpty()
        val status = runCatching { ModerationAppealStatus.valueOf(statusRaw) }
            .getOrDefault(ModerationAppealStatus.SUBMITTED)
        return ModerationAppeal(
            id = obj.requireString("id"),
            actionId = obj.string("action_id").orEmpty(),
            submittedByUserId = obj.string("submitted_by_user_id") ?: fallbackSubmitter,
            statement = obj.string("statement").orEmpty(),
            status = status,
            reviewedByUserId = obj.string("reviewed_by_user_id"),
            decisionReason = obj.string("decision_reason"),
            createdAtEpochMs = obj.longFromIso("created_at", fallbackNow),
            reviewedAtEpochMs = obj.string("reviewed_at")?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            }
        )
    }
}

/** Maps legacy triage status names used by AdminModerationScreen. */
object ModerationLegacyTriageStatus {
    fun toRpcStatus(legacy: String): String = legacy.trim().uppercase()

    /** ACTIONED is accepted by triage_content_report but is not a real measure. */
    fun actionedMeansRealMeasure(): Boolean = ModerationReportRules.legacyActionedMeansRealMeasure()
}
