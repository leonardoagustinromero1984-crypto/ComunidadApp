package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.observability.ObservabilityErrorCode
import com.comunidapp.app.domain.observability.ObservabilityRetentionPolicyRecord
import com.comunidapp.app.domain.observability.ObservabilityRetentionRun
import com.comunidapp.app.domain.observability.RetentionDeleteMode
import com.comunidapp.app.domain.observability.RetentionExecuteResult
import com.comunidapp.app.domain.observability.RetentionPreviewResult
import com.comunidapp.app.domain.observability.RetentionRunKind
import com.comunidapp.app.domain.observability.RetentionRunStatus
import com.comunidapp.app.domain.observability.authorization.ObservabilityAccessDecision
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorization
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorizationContext
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermission
import com.comunidapp.app.domain.observability.authorization.ObservabilityRequestedAction
import com.comunidapp.app.domain.observability.correlation.CorrelationId
import com.comunidapp.app.domain.observability.retention.RetentionPolicyKey
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Retención M07 Etapa 5 — RPC-only / mock determinista.
 * Preview y execute son acciones distintas; no se devuelve contenido a eliminar.
 */
interface RetentionRepository {
    suspend fun listPolicies(
        auth: ObservabilityAuthorizationContext
    ): AppResult<List<ObservabilityRetentionPolicyRecord>>

    suspend fun preview(
        auth: ObservabilityAuthorizationContext,
        policyId: String,
        batchSize: Int = 100,
        correlationId: String
    ): AppResult<RetentionPreviewResult>

    suspend fun execute(
        auth: ObservabilityAuthorizationContext,
        previewRunId: String,
        correlationId: String
    ): AppResult<RetentionExecuteResult>

    suspend fun listRuns(
        auth: ObservabilityAuthorizationContext,
        offset: Int = 0,
        limit: Int = 50
    ): AppResult<List<ObservabilityRetentionRun>>

    suspend fun setLegalHold(
        auth: ObservabilityAuthorizationContext,
        policyId: String,
        correlationId: String
    ): AppResult<ObservabilityRetentionPolicyRecord>

    suspend fun releaseLegalHold(
        auth: ObservabilityAuthorizationContext,
        policyId: String,
        correlationId: String
    ): AppResult<ObservabilityRetentionPolicyRecord>
}

private fun retFail(
    code: ObservabilityErrorCode,
    kind: AppErrorKind = AppErrorKind.VALIDATION
): AppResult.Failure = AppResult.Failure(
    AppError(
        kind = kind,
        userMessage = "Operación de retención no permitida.",
        technicalMessage = code.name,
        code = code.name
    )
)

private fun requireRetention(auth: ObservabilityAuthorizationContext): ObservabilityAccessDecision =
    ObservabilityAuthorization.authorize(
        auth.copy(requestedAction = ObservabilityRequestedAction.MANAGE_RETENTION)
    )

class RetentionMockStore {
    val policies = ConcurrentHashMap<String, ObservabilityRetentionPolicyRecord>()
    val runs = ConcurrentHashMap<String, ObservabilityRetentionRun>()
    val previewConsumed = ConcurrentHashMap.newKeySet<String>()
    private val seq = AtomicInteger(1)
    fun nextId(prefix: String) = "%s-%04d".format(prefix, seq.getAndIncrement())
    fun clear() {
        policies.clear(); runs.clear(); previewConsumed.clear(); seq.set(1)
    }
}

class MockRetentionRepository(
    private val store: RetentionMockStore = RetentionMockStore(),
    private val clock: () -> Instant = { Instant.now() }
) : RetentionRepository {

    init {
        if (store.policies.isEmpty()) seed()
    }

    private fun seed() {
        fun add(
            key: RetentionPolicyKey,
            table: String,
            days: Int?,
            mode: RetentionDeleteMode,
            code: String,
            hold: Boolean = false
        ) {
            val id = store.nextId("pol")
            store.policies[id] = ObservabilityRetentionPolicyRecord(
                id = id,
                policyKey = key,
                targetTable = table,
                retentionDays = days,
                deleteMode = mode,
                enabled = true,
                legalHold = hold,
                descriptionCode = code
            )
        }
        add(RetentionPolicyKey.TECHNICAL_30_DAYS, "performance_metrics", 30, RetentionDeleteMode.HARD_DELETE, "TECH_METRICS_30")
        add(RetentionPolicyKey.TECHNICAL_30_DAYS, "health_checks", 30, RetentionDeleteMode.HARD_DELETE, "TECH_HEALTH_30")
        add(RetentionPolicyKey.TECHNICAL_90_DAYS, "application_errors", 90, RetentionDeleteMode.ANONYMIZE, "TECH_ERRORS_90")
        add(RetentionPolicyKey.AUDIT_12_MONTHS, "audit_events", 365, RetentionDeleteMode.ANONYMIZE, "AUDIT_12M")
        add(RetentionPolicyKey.SECURITY_24_MONTHS, "security_events", 730, RetentionDeleteMode.KEEP_UNTIL_RESOLVED, "SECURITY_24M")
        add(RetentionPolicyKey.LEGAL_REVIEW_REQUIRED, "audit_events", null, RetentionDeleteMode.LEGAL_REVIEW, "LEGAL_HOLD_AUDIT", hold = true)
        add(RetentionPolicyKey.NO_REMOTE, "observability_export_requests", 0, RetentionDeleteMode.NO_DELETE, "EXPORT_NO_REMOTE")
    }

    override suspend fun listPolicies(
        auth: ObservabilityAuthorizationContext
    ): AppResult<List<ObservabilityRetentionPolicyRecord>> {
        if (requireRetention(auth) != ObservabilityAccessDecision.ALLOWED &&
            ObservabilityPermission.OBSERVABILITY_VIEW !in auth.permissions
        ) {
            return retFail(ObservabilityErrorCode.OBS_RETENTION_EXECUTION_DENIED, AppErrorKind.FORBIDDEN)
        }
        return AppResult.Success(store.policies.values.sortedBy { it.policyKey.name + it.targetTable })
    }

    override suspend fun preview(
        auth: ObservabilityAuthorizationContext,
        policyId: String,
        batchSize: Int,
        correlationId: String
    ): AppResult<RetentionPreviewResult> {
        if (requireRetention(auth) != ObservabilityAccessDecision.ALLOWED) {
            return retFail(ObservabilityErrorCode.OBS_RETENTION_EXECUTION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val policy = store.policies[policyId]
            ?: return retFail(ObservabilityErrorCode.OBS_RETENTION_POLICY_UNKNOWN)
        if (policy.legalHold ||
            policy.policyKey == RetentionPolicyKey.LEGAL_REVIEW_REQUIRED ||
            policy.deleteMode == RetentionDeleteMode.LEGAL_REVIEW
        ) {
            return retFail(ObservabilityErrorCode.OBS_RETENTION_LEGAL_HOLD)
        }
        val cid = CorrelationId.parseOrNull(correlationId)
            ?: return retFail(ObservabilityErrorCode.OBS_CORRELATION_INVALID)
        val estimated = when (policy.targetTable) {
            "performance_metrics", "health_checks" -> 12L
            "application_errors", "audit_events" -> 8L
            else -> 0L
        }.coerceAtMost(batchSize.coerceIn(1, 500).toLong())
        val runId = store.nextId("run")
        val expires = clock().plusSeconds(15 * 60)
        store.runs[runId] = ObservabilityRetentionRun(
            id = runId,
            policyId = policyId,
            runKind = RetentionRunKind.PREVIEW,
            status = RetentionRunStatus.PREVIEWED,
            estimatedCount = estimated,
            batchSize = batchSize.coerceIn(1, 500),
            correlationId = cid,
            previewExpiresAt = expires,
            createdAt = clock()
        )
        return AppResult.Success(
            RetentionPreviewResult(
                runId = runId,
                status = RetentionRunStatus.PREVIEWED,
                estimatedCount = estimated,
                targetTable = policy.targetTable,
                policyKey = policy.policyKey,
                previewExpiresAt = expires,
                correlationId = cid
            )
        )
    }

    override suspend fun execute(
        auth: ObservabilityAuthorizationContext,
        previewRunId: String,
        correlationId: String
    ): AppResult<RetentionExecuteResult> {
        if (requireRetention(auth) != ObservabilityAccessDecision.ALLOWED) {
            return retFail(ObservabilityErrorCode.OBS_RETENTION_EXECUTION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val preview = store.runs[previewRunId]
            ?: return retFail(ObservabilityErrorCode.OBS_RETENTION_PREVIEW_REQUIRED)
        if (preview.runKind != RetentionRunKind.PREVIEW || preview.status != RetentionRunStatus.PREVIEWED) {
            return retFail(ObservabilityErrorCode.OBS_RETENTION_PREVIEW_REQUIRED)
        }
        if (preview.previewExpiresAt != null && preview.previewExpiresAt!!.isBefore(clock())) {
            store.runs[previewRunId] = preview.copy(status = RetentionRunStatus.EXPIRED)
            return retFail(ObservabilityErrorCode.OBS_RETENTION_PREVIEW_EXPIRED)
        }
        if (!store.previewConsumed.add(previewRunId)) {
            // Idempotent: second execute with same preview is denied as consumed
            return retFail(ObservabilityErrorCode.OBS_RETENTION_PREVIEW_REQUIRED)
        }
        val policy = store.policies[preview.policyId]
            ?: return retFail(ObservabilityErrorCode.OBS_RETENTION_POLICY_UNKNOWN)
        if (policy.legalHold) {
            return retFail(ObservabilityErrorCode.OBS_RETENTION_LEGAL_HOLD)
        }
        val cid = CorrelationId.parseOrNull(correlationId)
            ?: return retFail(ObservabilityErrorCode.OBS_CORRELATION_INVALID)
        val affected = preview.estimatedCount.coerceAtMost(preview.batchSize.toLong())
        val execId = store.nextId("run")
        store.runs[execId] = ObservabilityRetentionRun(
            id = execId,
            policyId = policy.id,
            runKind = RetentionRunKind.EXECUTE,
            status = RetentionRunStatus.EXECUTED,
            estimatedCount = preview.estimatedCount,
            affectedCount = affected,
            batchSize = preview.batchSize,
            correlationId = cid,
            executedAt = clock(),
            createdAt = clock()
        )
        store.runs[previewRunId] = preview.copy(status = RetentionRunStatus.EXECUTED)
        return AppResult.Success(
            RetentionExecuteResult(
                runId = execId,
                status = RetentionRunStatus.EXECUTED,
                affectedCount = affected,
                targetTable = policy.targetTable,
                correlationId = cid
            )
        )
    }

    override suspend fun listRuns(
        auth: ObservabilityAuthorizationContext,
        offset: Int,
        limit: Int
    ): AppResult<List<ObservabilityRetentionRun>> {
        if (requireRetention(auth) != ObservabilityAccessDecision.ALLOWED) {
            return retFail(ObservabilityErrorCode.OBS_RETENTION_EXECUTION_DENIED, AppErrorKind.FORBIDDEN)
        }
        return AppResult.Success(
            store.runs.values.sortedByDescending { it.createdAt }
                .drop(offset.coerceAtLeast(0))
                .take(limit.coerceIn(1, 200))
        )
    }

    override suspend fun setLegalHold(
        auth: ObservabilityAuthorizationContext,
        policyId: String,
        correlationId: String
    ): AppResult<ObservabilityRetentionPolicyRecord> {
        if (requireRetention(auth) != ObservabilityAccessDecision.ALLOWED) {
            return retFail(ObservabilityErrorCode.OBS_RETENTION_EXECUTION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val policy = store.policies[policyId]
            ?: return retFail(ObservabilityErrorCode.OBS_RETENTION_POLICY_UNKNOWN)
        val updated = policy.copy(legalHold = true)
        store.policies[policyId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun releaseLegalHold(
        auth: ObservabilityAuthorizationContext,
        policyId: String,
        correlationId: String
    ): AppResult<ObservabilityRetentionPolicyRecord> {
        if (requireRetention(auth) != ObservabilityAccessDecision.ALLOWED) {
            return retFail(ObservabilityErrorCode.OBS_RETENTION_EXECUTION_DENIED, AppErrorKind.FORBIDDEN)
        }
        val policy = store.policies[policyId]
            ?: return retFail(ObservabilityErrorCode.OBS_RETENTION_POLICY_UNKNOWN)
        if (policy.policyKey == RetentionPolicyKey.LEGAL_REVIEW_REQUIRED) {
            return retFail(ObservabilityErrorCode.OBS_RETENTION_LEGAL_HOLD)
        }
        val updated = policy.copy(legalHold = false)
        store.policies[policyId] = updated
        return AppResult.Success(updated)
    }
}

class SupabaseRetentionRepository : RetentionRepository {

    private suspend fun rpc(function: String, parameters: JsonObject): JsonElement =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeAs()

    private fun fail(
        code: ObservabilityErrorCode,
        kind: AppErrorKind = AppErrorKind.VALIDATION
    ): AppResult.Failure = AppResult.Failure(
        AppError(
            kind = kind,
            userMessage = "Operación de retención no permitida.",
            technicalMessage = code.name,
            code = code.name
        )
    )

    private fun mapRpcError(t: Throwable): AppResult.Failure {
        val msg = t.message.orEmpty()
        val code = ObservabilityErrorCode.entries.firstOrNull { msg.contains(it.name) }
            ?: ObservabilityErrorCode.OBS_RETENTION_RUN_FAILED
        return fail(code, AppErrorKind.NETWORK)
    }

    override suspend fun listPolicies(
        auth: ObservabilityAuthorizationContext
    ): AppResult<List<ObservabilityRetentionPolicyRecord>> = runCatching {
        val element = rpc("m07_list_retention_policies", buildJsonObject { })
        val rows = element as? JsonArray ?: JsonArray(emptyList())
        AppResult.Success(rows.mapNotNull { (it as? JsonObject)?.toPolicy() })
    }.getOrElse { mapRpcError(it) }

    override suspend fun preview(
        auth: ObservabilityAuthorizationContext,
        policyId: String,
        batchSize: Int,
        correlationId: String
    ): AppResult<RetentionPreviewResult> = runCatching {
        val element = rpc(
            "m07_preview_retention_run",
            buildJsonObject {
                put("p_policy_id", policyId)
                put("p_batch_size", batchSize)
                put("p_correlation_id", correlationId)
            }
        ) as JsonObject
        AppResult.Success(
            RetentionPreviewResult(
                runId = element.str("run_id") ?: return fail(ObservabilityErrorCode.OBS_RETENTION_RUN_FAILED),
                status = RetentionRunStatus.PREVIEWED,
                estimatedCount = element.long("estimated_count") ?: 0L,
                targetTable = element.str("target_table").orEmpty(),
                policyKey = RetentionPolicyKey.fromString(element.str("policy_key"))
                    ?: RetentionPolicyKey.NO_REMOTE,
                previewExpiresAt = element.instant("preview_expires_at"),
                correlationId = CorrelationId.parseOrNull(element.str("correlation_id"))
            )
        )
    }.getOrElse { mapRpcError(it) }

    override suspend fun execute(
        auth: ObservabilityAuthorizationContext,
        previewRunId: String,
        correlationId: String
    ): AppResult<RetentionExecuteResult> = runCatching {
        val element = rpc(
            "m07_execute_retention_run",
            buildJsonObject {
                put("p_preview_run_id", previewRunId)
                put("p_correlation_id", correlationId)
            }
        ) as JsonObject
        AppResult.Success(
            RetentionExecuteResult(
                runId = element.str("run_id") ?: return fail(ObservabilityErrorCode.OBS_RETENTION_RUN_FAILED),
                status = RetentionRunStatus.EXECUTED,
                affectedCount = element.long("affected_count") ?: 0L,
                targetTable = element.str("target_table").orEmpty(),
                correlationId = CorrelationId.parseOrNull(element.str("correlation_id"))
            )
        )
    }.getOrElse { mapRpcError(it) }

    override suspend fun listRuns(
        auth: ObservabilityAuthorizationContext,
        offset: Int,
        limit: Int
    ): AppResult<List<ObservabilityRetentionRun>> = runCatching {
        val element = rpc(
            "m07_list_retention_runs",
            buildJsonObject {
                put("p_limit", limit)
                put("p_offset", offset)
            }
        )
        val rows = element as? JsonArray ?: JsonArray(emptyList())
        AppResult.Success(rows.mapNotNull { (it as? JsonObject)?.toRun() })
    }.getOrElse { mapRpcError(it) }

    override suspend fun setLegalHold(
        auth: ObservabilityAuthorizationContext,
        policyId: String,
        correlationId: String
    ): AppResult<ObservabilityRetentionPolicyRecord> = runCatching {
        rpc(
            "m07_set_legal_hold",
            buildJsonObject {
                put("p_policy_id", policyId)
                put("p_correlation_id", correlationId)
            }
        )
        listPolicies(auth).let { listed ->
            when (listed) {
                is AppResult.Success -> {
                    val found = listed.data.firstOrNull { it.id == policyId }
                        ?: return fail(ObservabilityErrorCode.OBS_RETENTION_POLICY_UNKNOWN)
                    AppResult.Success(found.copy(legalHold = true))
                }
                is AppResult.Failure -> listed
            }
        }
    }.getOrElse { mapRpcError(it) }

    override suspend fun releaseLegalHold(
        auth: ObservabilityAuthorizationContext,
        policyId: String,
        correlationId: String
    ): AppResult<ObservabilityRetentionPolicyRecord> = runCatching {
        rpc(
            "m07_release_legal_hold",
            buildJsonObject {
                put("p_policy_id", policyId)
                put("p_correlation_id", correlationId)
            }
        )
        listPolicies(auth).let { listed ->
            when (listed) {
                is AppResult.Success -> {
                    val found = listed.data.firstOrNull { it.id == policyId }
                        ?: return fail(ObservabilityErrorCode.OBS_RETENTION_POLICY_UNKNOWN)
                    AppResult.Success(found.copy(legalHold = false))
                }
                is AppResult.Failure -> listed
            }
        }
    }.getOrElse { mapRpcError(it) }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false

    private fun JsonObject.instant(key: String): Instant? =
        str(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun JsonObject.toPolicy(): ObservabilityRetentionPolicyRecord? {
        val id = str("id") ?: return null
        return ObservabilityRetentionPolicyRecord(
            id = id,
            policyKey = RetentionPolicyKey.fromString(str("policy_key")) ?: RetentionPolicyKey.NO_REMOTE,
            targetTable = str("target_table").orEmpty(),
            retentionDays = str("retention_days")?.toIntOrNull()
                ?: (this["retention_days"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            deleteMode = RetentionDeleteMode.entries.firstOrNull {
                it.name.equals(str("delete_mode"), ignoreCase = true)
            } ?: RetentionDeleteMode.NO_DELETE,
            enabled = bool("enabled"),
            legalHold = bool("legal_hold"),
            descriptionCode = str("description_code").orEmpty()
        )
    }

    private fun JsonObject.toRun(): ObservabilityRetentionRun? {
        val id = str("id") ?: return null
        return ObservabilityRetentionRun(
            id = id,
            policyId = str("policy_id").orEmpty(),
            runKind = RetentionRunKind.entries.firstOrNull {
                it.name.equals(str("run_kind"), ignoreCase = true)
            } ?: RetentionRunKind.PREVIEW,
            status = RetentionRunStatus.entries.firstOrNull {
                it.name.equals(str("status"), ignoreCase = true)
            } ?: RetentionRunStatus.FAILED,
            estimatedCount = long("estimated_count") ?: 0L,
            affectedCount = long("affected_count") ?: 0L,
            batchSize = str("batch_size")?.toIntOrNull() ?: 100,
            correlationId = CorrelationId.parseOrNull(str("correlation_id")),
            previewExpiresAt = instant("preview_expires_at"),
            executedAt = instant("executed_at"),
            createdAt = instant("created_at") ?: Instant.EPOCH
        )
    }

    @Suppress("unused")
    private val jsonNull: JsonNull = JsonNull
}
