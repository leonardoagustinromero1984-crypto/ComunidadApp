package com.comunidapp.app.data.remote.supabase.m10

import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterHomeProfile
import com.comunidapp.app.data.model.FosterHomeRequest
import com.comunidapp.app.data.model.FosterHomeRequestStatus
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.FosterPlacement
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class FosterHomeProfileRow(
    val id: String,
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("display_name") val displayName: String,
    val description: String? = null,
    val status: String = "DRAFT",
    @SerialName("availability_status") val availabilityStatus: String = "UNAVAILABLE",
    @SerialName("total_capacity") val totalCapacity: Int = 1,
    @SerialName("current_occupancy") val currentOccupancy: Int = 0,
    @SerialName("reserved_count") val reservedCount: Int = 0,
    @SerialName("accepted_species") val acceptedSpecies: List<String> = emptyList(),
    @SerialName("accepted_sizes") val acceptedSizes: List<String> = emptyList(),
    @SerialName("accepts_special_needs") val acceptsSpecialNeeds: Boolean = false,
    @SerialName("accepts_emergencies") val acceptsEmergencies: Boolean = false,
    @SerialName("has_other_animals") val hasOtherAnimals: Boolean? = null,
    val observations: String? = null,
    @SerialName("zone_text") val zoneText: String = "",
    @SerialName("public_location_text") val publicLocationText: String? = null,
    @SerialName("private_address_text") val privateAddressText: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class FosterCareRequestRow(
    val id: String,
    @SerialName("foster_home_id") val fosterHomeId: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("requester_user_id") val requesterUserId: String? = null,
    @SerialName("requester_organization_id") val requesterOrganizationId: String? = null,
    val message: String = "",
    val urgency: String = "NORMAL",
    @SerialName("requested_start_at") val requestedStartAt: String? = null,
    @SerialName("estimated_end_at") val estimatedEndAt: String? = null,
    @SerialName("special_needs") val specialNeeds: String? = null,
    val status: String = "SUBMITTED",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null
)

@Serializable
data class FosterPlacementRow(
    val id: String,
    @SerialName("foster_request_id") val fosterRequestId: String,
    @SerialName("foster_home_id") val fosterHomeId: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("requester_user_id") val requesterUserId: String? = null,
    @SerialName("requester_organization_id") val requesterOrganizationId: String? = null,
    @SerialName("foster_user_id") val fosterUserId: String,
    val status: String = "RESERVED",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("estimated_end_at") val estimatedEndAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("initial_notes") val initialNotes: String? = null,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("temporary_responsibility_id") val temporaryResponsibilityId: String? = null
)

fun FosterHomeProfileRow.toDomain(): FosterHomeProfile = FosterHomeProfile(
    id = id,
    ownerUserId = ownerUserId,
    displayName = displayName,
    description = description,
    status = FosterHomeStatus.fromString(status),
    availabilityStatus = FosterAvailabilityStatus.fromString(availabilityStatus),
    totalCapacity = totalCapacity,
    currentOccupancy = currentOccupancy,
    reservedCount = reservedCount,
    acceptedSpecies = acceptedSpecies.map { it.uppercase() }.toSet(),
    acceptedSizes = acceptedSizes.map { it.uppercase() }.toSet(),
    acceptsSpecialNeeds = acceptsSpecialNeeds,
    acceptsEmergencies = acceptsEmergencies,
    hasOtherAnimals = hasOtherAnimals,
    observations = observations,
    zoneText = zoneText,
    publicLocationText = publicLocationText,
    privateAddressText = privateAddressText,
    createdAt = parseTs(createdAt),
    updatedAt = parseTs(updatedAt)
)

fun FosterCareRequestRow.toDomain(): FosterHomeRequest = FosterHomeRequest(
    id = id,
    fosterHomeId = fosterHomeId,
    petId = petId,
    requesterUserId = requesterUserId,
    requesterOrganizationId = requesterOrganizationId,
    message = message,
    urgency = FosterUrgency.fromString(urgency),
    requestedStartAt = parseTsOrNull(requestedStartAt),
    estimatedEndAt = parseTsOrNull(estimatedEndAt),
    specialNeeds = specialNeeds,
    status = FosterHomeRequestStatus.fromString(status),
    createdAt = parseTs(createdAt),
    reviewedAt = parseTsOrNull(reviewedAt),
    reviewedBy = reviewedBy,
    rejectionReason = rejectionReason
)

fun FosterPlacementRow.toDomain(): FosterPlacement = FosterPlacement(
    id = id,
    fosterRequestId = fosterRequestId,
    fosterHomeId = fosterHomeId,
    petId = petId,
    requesterUserId = requesterUserId,
    requesterOrganizationId = requesterOrganizationId,
    fosterUserId = fosterUserId,
    status = FosterPlacementStatus.fromString(status),
    startedAt = parseTs(startedAt),
    estimatedEndAt = parseTsOrNull(estimatedEndAt),
    endedAt = parseTsOrNull(endedAt),
    initialNotes = initialNotes,
    endReason = endReason,
    temporaryResponsibilityId = temporaryResponsibilityId
)

private fun parseTs(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private fun parseTsOrNull(value: String?): Long? =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

class SupabaseFosterM10RemoteDataSource {

    private suspend inline fun <reified T : Any> decodeOne(
        function: String,
        parameters: JsonObject
    ): T = supabase.postgrest.rpc(function = function, parameters = parameters).decodeSingle()

    private suspend inline fun <reified T : Any> decodeList(
        function: String,
        parameters: JsonObject = buildJsonObject { }
    ): List<T> = supabase.postgrest.rpc(function = function, parameters = parameters).decodeList()

    suspend fun listAvailableHomes(): List<FosterHomeProfileRow> =
        decodeList("m10_list_available_foster_homes")

    suspend fun getHome(homeId: String): FosterHomeProfileRow =
        decodeOne("m10_get_foster_home", buildJsonObject { put("p_home_id", homeId) })

    suspend fun getMyHome(): FosterHomeProfileRow =
        decodeOne("m10_get_my_foster_home", buildJsonObject { })

    suspend fun createHome(params: JsonObject): FosterHomeProfileRow =
        decodeOne("m10_create_foster_home", params)

    suspend fun updateHome(params: JsonObject): FosterHomeProfileRow =
        decodeOne("m10_update_foster_home", params)

    suspend fun changeAvailability(homeId: String, availability: String): FosterHomeProfileRow =
        decodeOne(
            "m10_change_foster_availability",
            buildJsonObject {
                put("p_home_id", homeId)
                put("p_availability", availability)
            }
        )

    suspend fun setHomeStatus(homeId: String, status: String): FosterHomeProfileRow =
        decodeOne(
            "m10_set_foster_home_status",
            buildJsonObject {
                put("p_home_id", homeId)
                put("p_status", status)
            }
        )

    suspend fun submitRequest(params: JsonObject): FosterCareRequestRow =
        decodeOne("m10_submit_foster_request", params)

    suspend fun cancelRequest(id: String): FosterCareRequestRow =
        decodeOne("m10_cancel_foster_request", buildJsonObject { put("p_request_id", id) })

    suspend fun markUnderReview(id: String): FosterCareRequestRow =
        decodeOne(
            "m10_mark_foster_request_under_review",
            buildJsonObject { put("p_request_id", id) }
        )

    suspend fun acceptRequest(id: String): FosterCareRequestRow =
        decodeOne("m10_accept_foster_request", buildJsonObject { put("p_request_id", id) })

    suspend fun rejectRequest(id: String, reason: String?): FosterCareRequestRow =
        decodeOne(
            "m10_reject_foster_request",
            buildJsonObject {
                put("p_request_id", id)
                put("p_rejection_reason", reason)
            }
        )

    suspend fun listSent(): List<FosterCareRequestRow> =
        decodeList("m10_list_sent_foster_requests")

    suspend fun listReceived(): List<FosterCareRequestRow> =
        decodeList("m10_list_received_foster_requests")

    suspend fun getRequest(id: String): FosterCareRequestRow =
        decodeOne("m10_get_foster_request", buildJsonObject { put("p_request_id", id) })

    suspend fun startPlacement(requestId: String, notes: String?): FosterPlacementRow =
        decodeOne(
            "m10_start_foster_placement",
            buildJsonObject {
                put("p_request_id", requestId)
                put("p_initial_notes", notes)
            }
        )

    suspend fun listActivePlacements(homeId: String?): List<FosterPlacementRow> =
        decodeList(
            "m10_list_active_foster_placements",
            buildJsonObject {
                if (homeId != null) put("p_home_id", homeId)
                else put("p_home_id", JsonNull)
            }
        )

    suspend fun getPlacement(id: String): FosterPlacementRow =
        decodeOne("m10_get_foster_placement", buildJsonObject { put("p_placement_id", id) })
}
