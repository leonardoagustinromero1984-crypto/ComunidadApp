package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FosterHomeRequest
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.ux.CanonicalUiErrorMapper
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

@Serializable
data class CanonicalFosterTransitCompletion(
    @SerialName("request_id") val requestId: String,
    @SerialName("request_status") val requestStatus: String,
    @SerialName("application_id") val applicationId: String? = null,
    @SerialName("application_status") val applicationStatus: String? = null,
    @SerialName("placement_id") val placementId: String? = null,
    @SerialName("placement_status") val placementStatus: String? = null,
    @SerialName("pet_id") val petId: String,
    @SerialName("foster_user_id") val fosterUserId: String? = null,
    @SerialName("temporary_link_id") val temporaryLinkId: String? = null,
    @SerialName("temporary_holder_kind") val temporaryHolderKind: String? = null,
    @SerialName("temporary_holder_role") val temporaryHolderRole: String? = null,
    @SerialName("temporary_link_status") val temporaryLinkStatus: String? = null,
    @SerialName("responsible_organization_id") val responsibleOrganizationId: String? = null,
    @SerialName("vitacora_pet_id") val vitacoraPetId: String? = null,
    @SerialName("public_vitacora_number") val publicVitacoraNumber: Long? = null,
    val idempotent: Boolean = false
)

@Serializable
data class CanonicalOpenFosterRequest(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("pet_name") val petName: String? = null,
    val species: String? = null,
    val sex: String? = null,
    val needs: String? = null,
    val notes: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CanonicalFosterApplicantRow(
    val id: String,
    @SerialName("foster_user_id") val fosterUserId: String? = null,
    @SerialName("foster_name") val fosterName: String? = null,
    val status: String,
    @SerialName("locality_id") val localityId: String? = null,
    @SerialName("created_at") val createdAt: String? = null
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

    fun completion(element: JsonElement): CanonicalFosterTransitCompletion =
        json.decodeFromJsonElement(unwrapObject(element))

    fun openRequests(element: JsonElement): List<CanonicalOpenFosterRequest> = decodeList(element)

    fun applicants(element: JsonElement): List<CanonicalFosterApplicantRow> = decodeList(element)

    fun id(element: JsonElement): String {
        val value = unwrap(element)
        val text = (value as? JsonPrimitive)?.content?.trim().orEmpty()
        if (text.length < 32) throw IllegalStateException("FOSTER_TRANSIT_ID_MISSING")
        return text
    }

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
 * Canonical transit contract from migration 1102.
 * Bound from [com.comunidapp.app.data.provider.DataProvider] on canonical staging.
 * Failures stay failures; there is no local stand-in.
 */
interface CanonicalFosterTransitRepository {
    suspend fun listMyFosterRequests(): Result<List<CanonicalFosterTransitRequest>>
    suspend fun listMyFosterApplications(): Result<List<CanonicalFosterTransitApplication>>
    suspend fun getActiveFosterTransit(petId: String): Result<CanonicalActiveFosterTransit>
    suspend fun listOpenFosterRequests(): Result<List<CanonicalOpenFosterRequest>>
    suspend fun listFosterRequestApplications(requestId: String): Result<List<CanonicalFosterApplicantRow>>
    suspend fun requestFosterForPet(petId: String, needs: String?, notes: String?): Result<String>
    suspend fun applyToFosterRequest(requestId: String): Result<String>
    suspend fun selectFosterApplicant(applicationId: String): Result<String>
    suspend fun completeFosterTransit(requestId: String): Result<CanonicalFosterTransitCompletion>
}

/**
 * Rebuilds the request, application, and active transit from backend lists.
 * Navigation arguments are not the source of those ids.
 */
internal object CanonicalFosterTransitRecovery {
    val nonTerminalRequest = setOf("REQUESTED", "MATCHED", "ACTIVE")

    fun requestForPet(
        rows: List<CanonicalFosterTransitRequest>,
        petId: String
    ): CanonicalFosterTransitRequest? {
        val open = rows.filter { it.petId == petId && it.status in nonTerminalRequest }
        if (open.size > 1) throw IllegalStateException("FOSTER_REQUEST_DUPLICATES_PRESENT")
        return open.firstOrNull()
    }

    fun applicationForRequest(
        rows: List<CanonicalFosterTransitApplication>,
        requestId: String
    ): CanonicalFosterTransitApplication? {
        val mine = rows.filter { it.requestId == requestId }
        if (mine.size > 1) throw IllegalStateException("FOSTER_APPLICATION_DUPLICATES_PRESENT")
        return mine.firstOrNull()
    }

    fun completedRequestForPet(
        rows: List<CanonicalFosterTransitRequest>,
        petId: String
    ): CanonicalFosterTransitRequest? {
        return rows.firstOrNull { it.petId == petId && it.status == "COMPLETED" }
    }

    /**
     * SELECTED is historical once the request or placement is terminal.
     * There is no application COMPLETED status.
     */
    fun isHistoricalSelection(application: CanonicalFosterTransitApplication): Boolean {
        if (application.status != "SELECTED") return false
        val requestDone = application.requestStatus == "COMPLETED" || application.requestStatus == "CANCELLED"
        val placementDone = application.placementStatus == "CLOSED" || application.placementStatus == "CANCELLED"
        return requestDone || placementDone
    }

    fun showsActiveTemporaryCare(application: CanonicalFosterTransitApplication): Boolean {
        return application.status == "SELECTED" &&
            application.requestStatus == "ACTIVE" &&
            application.placementStatus == "OPEN" &&
            !isHistoricalSelection(application)
    }
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

    override suspend fun listOpenFosterRequests(): Result<List<CanonicalOpenFosterRequest>> =
        call(CanonicalBackend.RPC_LIST_OPEN_FOSTER_REQUESTS) {
            CanonicalFosterTransitDecoding.openRequests(it)
        }

    override suspend fun listFosterRequestApplications(
        requestId: String
    ): Result<List<CanonicalFosterApplicantRow>> =
        call(
            CanonicalBackend.RPC_LIST_FOSTER_REQUEST_APPS,
            buildJsonObject { put("p_request_id", requestId) }
        ) { CanonicalFosterTransitDecoding.applicants(it) }

    override suspend fun requestFosterForPet(
        petId: String,
        needs: String?,
        notes: String?
    ): Result<String> = call(
        CanonicalBackend.RPC_REQUEST_FOSTER_FOR_PET,
        buildJsonObject {
            put("p_pet_id", petId)
            put("p_needs", needs.orEmpty())
            put("p_notes", notes.orEmpty())
        }
    ) { CanonicalFosterTransitDecoding.id(it) }

    override suspend fun applyToFosterRequest(requestId: String): Result<String> = call(
        CanonicalBackend.RPC_APPLY_TO_FOSTER_REQUEST,
        buildJsonObject { put("p_request_id", requestId) }
    ) { CanonicalFosterTransitDecoding.id(it) }

    override suspend fun selectFosterApplicant(applicationId: String): Result<String> = call(
        CanonicalBackend.RPC_SELECT_FOSTER_APPLICANT,
        buildJsonObject { put("p_application_id", applicationId) }
    ) { CanonicalFosterTransitDecoding.id(it) }

    override suspend fun completeFosterTransit(
        requestId: String
    ): Result<CanonicalFosterTransitCompletion> = call(
        CanonicalBackend.RPC_COMPLETE_FOSTER_TRANSIT,
        buildJsonObject { put("p_request_id", requestId) },
        "No se pudo finalizar el tránsito."
    ) { CanonicalFosterTransitDecoding.completion(it) }

    private suspend fun <T> call(
        function: String,
        parameters: JsonObject? = null,
        failure: String = "No se pudo leer el tránsito.",
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
            CanonicalUiErrorMapper.userMessage(error, failure)
        )
    }
}

/**
 * Canonical staging must not pretend an in-memory foster request succeeded.
 * The transit screens use [RpcCanonicalFosterTransitRepository].
 */
class CanonicalFosterRequestRejectedRepository : FosterRequestRepository {
    private fun fail(): Result<FosterHomeRequest> =
        Result.failure(IllegalStateException("CANONICAL_TRANSIT_USES_RPC"))

    private fun observe(): Flow<List<FosterHomeRequest>> = flow {
        throw IllegalStateException("CANONICAL_TRANSIT_USES_RPC")
    }

    override fun observeSentRequests(userId: String): Flow<List<FosterHomeRequest>> = observe()

    override fun observeReceivedRequests(ownerUserId: String): Flow<List<FosterHomeRequest>> = observe()

    override suspend fun getRequestById(id: String): Result<FosterHomeRequest> = fail()

    override suspend fun submitRequest(input: SubmitFosterRequestInput): Result<FosterHomeRequest> = fail()

    override suspend fun cancelRequest(requestId: String): Result<FosterHomeRequest> = fail()

    override suspend fun markUnderReview(requestId: String): Result<FosterHomeRequest> = fail()

    override suspend fun acceptRequest(requestId: String): Result<FosterHomeRequest> = fail()

    override suspend fun rejectRequest(requestId: String, reason: String?): Result<FosterHomeRequest> = fail()
}

class UnavailableCanonicalFosterTransitRepository : CanonicalFosterTransitRepository {
    private fun <T> fail(): Result<T> =
        Result.failure(IllegalStateException("CANONICAL_TRANSIT_UNAVAILABLE"))

    override suspend fun listMyFosterRequests(): Result<List<CanonicalFosterTransitRequest>> = fail()

    override suspend fun listMyFosterApplications(): Result<List<CanonicalFosterTransitApplication>> = fail()

    override suspend fun getActiveFosterTransit(petId: String): Result<CanonicalActiveFosterTransit> = fail()

    override suspend fun listOpenFosterRequests(): Result<List<CanonicalOpenFosterRequest>> = fail()

    override suspend fun listFosterRequestApplications(
        requestId: String
    ): Result<List<CanonicalFosterApplicantRow>> = fail()

    override suspend fun requestFosterForPet(
        petId: String,
        needs: String?,
        notes: String?
    ): Result<String> = fail()

    override suspend fun applyToFosterRequest(requestId: String): Result<String> = fail()

    override suspend fun selectFosterApplicant(applicationId: String): Result<String> = fail()

    override suspend fun completeFosterTransit(
        requestId: String
    ): Result<CanonicalFosterTransitCompletion> = fail()
}
