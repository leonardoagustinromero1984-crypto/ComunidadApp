package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class LeoverVerificationRow(
    val id: String,
    val functionCode: String,
    val status: String,
    val reviewNote: String?,
    val createdAt: String?,
    val organizationId: String? = null
)

class CanonicalVerificationRepository {
    suspend fun request(
        functionCode: String,
        termsAccepted: Boolean,
        evidenceNote: String?,
        organizationId: String? = null
    ): Result<String> =
        runCatching {
            val evidence = buildJsonObject {
                put("terms_accepted", termsAccepted)
                if (!evidenceNote.isNullOrBlank()) put("note", evidenceNote)
            }
            val element: JsonElement = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_REQUEST_LEOVER_VERIFICATION,
                parameters = buildJsonObject {
                    put("p_function_code", functionCode)
                    put("p_evidence", evidence)
                    if (!organizationId.isNullOrBlank()) put("p_organization_id", organizationId)
                    else put("p_organization_id", JsonNull)
                }
            ).decodeAs()
            (element as? JsonPrimitive)?.contentOrNull ?: element.toString().trim('"')
        }

    suspend fun listMine(): Result<List<LeoverVerificationRow>> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(
            CanonicalBackend.RPC_LIST_MY_LEOVER_VERIFICATIONS
        ).decodeAs()
        val array = element as? JsonArray ?: return@runCatching emptyList()
        array.mapNotNull { item ->
            val obj = item.jsonObject
            LeoverVerificationRow(
                id = obj.string("id") ?: return@mapNotNull null,
                functionCode = obj.string("function_code").orEmpty(),
                status = obj.string("status").orEmpty(),
                reviewNote = obj.string("review_note"),
                createdAt = obj.string("created_at"),
                organizationId = obj.string("organization_id")
            )
        }
    }

    suspend fun resubmit(id: String, evidenceNote: String?): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_RESUBMIT_LEOVER_VERIFICATION,
            parameters = buildJsonObject {
                put("p_id", id)
                put("p_evidence", buildJsonObject {
                    put("terms_accepted", true)
                    if (!evidenceNote.isNullOrBlank()) put("note", evidenceNote)
                })
            }
        )
        Unit
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
