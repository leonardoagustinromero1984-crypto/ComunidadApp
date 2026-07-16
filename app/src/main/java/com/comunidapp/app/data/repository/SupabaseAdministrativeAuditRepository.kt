package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeArray
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeObject
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.longFromIso
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.requireString
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.runRpc
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.string
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseAdministrativeAuditRepository : AdministrativeAuditRepository {

    /**
     * Audit rows are written inside privileged RPCs. Client must not invent audit events.
     */
    override suspend fun recordAdministrativeEvent(
        actorUserId: String,
        action: String,
        resourceType: String,
        resourceId: String,
        reasonCode: String,
        nowEpochMs: Long
    ): AppResult<Unit> = AppResult.Failure(
        AppError(
            kind = AppErrorKind.FORBIDDEN,
            userMessage = "No tenés permiso para esta acción.",
            technicalMessage = "AUDIT_SERVER_SIDE_ONLY",
            code = "AUDIT_SERVER_SIDE_ONLY"
        )
    )

    override suspend fun listAdministrativeEvents(): AppResult<List<AdministrativeAuditEvent>> = runRpc {
        val element = supabase.postgrest.rpc(
            function = "list_administrative_events",
            parameters = buildJsonObject { put("p_limit", 50) }
        ).decodeAs<JsonElement>()
        decodeArray(element).mapNotNull { item ->
            decodeObject(item)?.let(::parseEvent)
        }
    }

    private fun parseEvent(obj: JsonObject): AdministrativeAuditEvent =
        AdministrativeAuditEvent(
            id = obj.requireString("id"),
            actorUserId = obj.string("actor_user_id").orEmpty(),
            action = obj.string("action").orEmpty(),
            resourceType = obj.string("resource_type").orEmpty(),
            resourceId = obj.string("resource_id").orEmpty(),
            reasonCode = obj.string("reason_code").orEmpty(),
            createdAtEpochMs = obj.longFromIso("created_at")
        )
}
