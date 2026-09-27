package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.ux.CanonicalUiErrorMapper
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

/**
 * Read models for migration 1102.
 * Status strings are the database check values, not a second vocabulary.
 */
@Serializable
data class CanonicalFosterTransitRequest(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("pet_name") val petName: String? = null,
    val species: String? = null,
    val sex: String? = null,
    val status: String,
    val needs: String? = null,
    val notes: String? = null,
    @SerialName("selected_application_id") val selectedApplicationId: String? = null,
    @SerialName("placement_id") val placementId: String? = null,
    @SerialName("placement_status") val placementStatus: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CanonicalFosterTransitApplication(
    val id: String,
    @SerialName("request_id") val requestId: String,
    val status: String,
    @SerialName("request_status") val requestStatus: String? = null,
    @SerialName("pet_id") val petId: String,
    @SerialName("pet_name") val petName: String? = null,
    val species: String? = null,
    val sex: String? = null,
    val needs: String? = null,
    val notes: String? = null,
    @SerialName("selected_application_id") val selectedApplicationId: String? = null,
    @SerialName("placement_id") val placementId: String? = null,
    @SerialName("placement_status") val placementStatus: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CanonicalActiveFosterTransit(
    @SerialName("request_id") val requestId: String,
    @SerialName("request_status") val requestStatus: String,
    @SerialName("selected_application_id") val selectedApplicationId: String? = null,
    @SerialName("application_status") val applicationStatus: String? = null,
    @SerialName("placement_id") val placementId: String? = null,
    @SerialName("placement_status") val placementStatus: String? = null,
    @SerialName("pet_id") val petId: String,
    @SerialName("pet_name") val petName: String? = null,
    val species: String? = null,
    @SerialName("foster_user_id") val fosterUserId: String? = null,
    @SerialName("temporary_holder_kind") val temporaryHolderKind: String? = null,
    @SerialName("temporary_holder_role") val temporaryHolderRole: String? = null,
    @SerialName("temporary_link_status") val temporaryLinkStatus: String? = null,
    @SerialName("requested_by") val requestedBy: String? = null,
    @SerialName("responsible_organization_id") val responsibleOrganizationId: String? = null,
    @SerialName("responsible_role") val responsibleRole: String? = null,
    val needs: String? = null,
    val notes: String? = null
)

/**
 * Decodes 1102 jsonb without the shared row normalizer.
 * That normalizer treats a text field named "needs" as a jsonb array.
 */
internal object CanonicalFosterTransitDecoding {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun requests(element: JsonElement): List<CanonicalFosterTransitRequest> = decodeList(element)

    fun applications(element: JsonElement): List<CanonicalFosterTransitApplication> = decodeList(element)

    fun transit(element: JsonElement): CanonicalActiveFosterTransit =
        json.decodeFromJsonElement(unwrapObject(element))

    private inline fun <reified T> decodeList(element: JsonElement): List<T> {
        return when (val value = unwrap(element)) {
            is JsonArray -> value.map { json.decodeFromJsonElement(it) }
            JsonNull -> emptyList()
            is JsonObject -> listOf(json.decodeFromJsonElement(value))
            else -> emptyList()
        }
    }

    private fun unwrapObject(element: JsonElement): JsonElement {
        val value = unwrap(element)
        if (value is JsonArray) {
            return value.firstOrNull() ?: throw IllegalStateException("FOSTER_TRANSIT_NOT_FOUND")
        }
        return value
    }

    private fun unwrap(element: JsonElement): JsonElement {
        if (element is JsonPrimitive && element.isString) {
            val parsed = runCatching { json.parseToJsonElement(element.content) }.getOrNull()
            if (parsed != null && parsed !is JsonPrimitive) return unwrap(parsed)
        }
        if (element is JsonObject && element.size == 1) {
            val (key, value) = element.entries.first()
            if (key.lowercase().startsWith("canon_")) return unwrap(value)
        }
        return element
    }
}

/**
 * Canonical transit reads introduced by migration 1102.
 * Not bound from [com.comunidapp.app.data.provider.DataProvider] until that
 * migration is applied. Failures stay failures; there is no local stand-in.
 */
interface CanonicalFosterTransitRepository {
    suspend fun listMyFosterRequests(): Result<List<CanonicalFosterTransitRequest>>
    suspend fun listMyFosterApplications(): Result<List<CanonicalFosterTransitApplication>>
    suspend fun getActiveFosterTransit(petId: String): Result<CanonicalActiveFosterTransit>
}

class RpcCanonicalFosterTransitRepository : CanonicalFosterTransitRepository {
    override suspend fun listMyFosterRequests(): Result<List<CanonicalFosterTransitRequest>> =
        call(CanonicalBackend.RPC_LIST_MY_FOSTER_REQUESTS) {
            CanonicalFosterTransitDecoding.requests(it)
        }

    override suspend fun listMyFosterApplications(): Result<List<CanonicalFosterTransitApplication>> =
        call(CanonicalBackend.RPC_LIST_MY_FOSTER_APPLICATIONS) {
            CanonicalFosterTransitDecoding.applications(it)
        }

    override suspend fun getActiveFosterTransit(petId: String): Result<CanonicalActiveFosterTransit> =
        call(
            CanonicalBackend.RPC_GET_ACTIVE_FOSTER_TRANSIT,
            buildJsonObject { put("p_pet_id", petId) }
        ) { CanonicalFosterTransitDecoding.transit(it) }

    private suspend fun <T> call(
        function: String,
        parameters: JsonObject? = null,
        decode: (JsonElement) -> T
    ): Result<T> = runCatching {
        val element: JsonElement = if (parameters == null) {
            supabase.postgrest.rpc(function).decodeAs()
        } else {
            supabase.postgrest.rpc(function, parameters).decodeAs()
        }
        decode(element)
    }.recoverCatching { error ->
        throw IllegalStateException(
            CanonicalUiErrorMapper.userMessage(error, "No se pudo leer el tránsito.")
        )
    }
}
