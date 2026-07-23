package com.comunidapp.app.data.remote.supabase.m10

import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterContributionStatus
import com.comunidapp.app.data.model.FosterEvolutionEntry
import com.comunidapp.app.data.model.FosterEvolutionVisibility
import com.comunidapp.app.data.model.FosterExpense
import com.comunidapp.app.data.model.FosterExpenseCategory
import com.comunidapp.app.data.model.FosterHealthStatus
import com.comunidapp.app.data.model.FosterHelpContribution
import com.comunidapp.app.data.model.FosterHelpRequest
import com.comunidapp.app.data.model.FosterHelpStatus
import com.comunidapp.app.data.model.FosterHelpType
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
    @SerialName("end_notes") val endNotes: String? = null,
    @SerialName("ended_by") val endedBy: String? = null,
    @SerialName("temporary_responsibility_id") val temporaryResponsibilityId: String? = null
)

@Serializable
data class FosterExpenseRow(
    val id: String,
    @SerialName("placement_id") val placementId: String,
    val category: String,
    val description: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String = "ARS",
    @SerialName("occurred_at") val occurredAt: String? = null,
    @SerialName("receipt_ref") val receiptRef: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FosterEvolutionRow(
    val id: String,
    @SerialName("placement_id") val placementId: String,
    val title: String,
    val description: String,
    @SerialName("health_status") val healthStatus: String = "UNKNOWN",
    @SerialName("weight_grams") val weightGrams: Int? = null,
    @SerialName("occurred_at") val occurredAt: String? = null,
    @SerialName("media_refs") val mediaRefs: List<String> = emptyList(),
    val visibility: String = "PARTICIPANTS",
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FosterHelpRequestRow(
    val id: String,
    @SerialName("placement_id") val placementId: String,
    @SerialName("help_type") val helpType: String,
    val title: String,
    val description: String,
    @SerialName("target_amount_minor") val targetAmountMinor: Long? = null,
    val currency: String? = null,
    @SerialName("quantity_needed") val quantityNeeded: Int? = null,
    val status: String = "OPEN",
    val urgency: String = "NORMAL",
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("received_amount_minor") val receivedAmountMinor: Long = 0,
    @SerialName("received_quantity") val receivedQuantity: Int = 0
)

@Serializable
data class FosterHelpContributionRow(
    val id: String,
    @SerialName("help_request_id") val helpRequestId: String,
    @SerialName("contributor_user_id") val contributorUserId: String? = null,
    val description: String,
    @SerialName("amount_minor") val amountMinor: Long? = null,
    val quantity: Int? = null,
    val status: String = "RECEIVED",
    @SerialName("recorded_at") val recordedAt: String? = null
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
    endNotes = endNotes,
    endedBy = endedBy,
    temporaryResponsibilityId = temporaryResponsibilityId
)

fun FosterExpenseRow.toExpenseDomain(): FosterExpense = FosterExpense(
    id = id,
    placementId = placementId,
    category = FosterExpenseCategory.fromString(category),
    description = description,
    amountMinor = amountMinor,
    currency = currency,
    occurredAt = parseTs(occurredAt),
    receiptRef = receiptRef,
    createdBy = createdBy,
    createdAt = parseTs(createdAt)
)

fun FosterEvolutionRow.toEvolutionDomain(): FosterEvolutionEntry = FosterEvolutionEntry(
    id = id,
    placementId = placementId,
    title = title,
    description = description,
    healthStatus = FosterHealthStatus.fromString(healthStatus),
    weightGrams = weightGrams,
    occurredAt = parseTs(occurredAt),
    mediaRefs = mediaRefs,
    visibility = FosterEvolutionVisibility.fromString(visibility),
    createdBy = createdBy,
    createdAt = parseTs(createdAt)
)

fun FosterHelpRequestRow.toHelpDomain(): FosterHelpRequest = FosterHelpRequest(
    id = id,
    placementId = placementId,
    type = FosterHelpType.fromString(helpType),
    title = title,
    description = description,
    targetAmountMinor = targetAmountMinor,
    currency = currency,
    quantityNeeded = quantityNeeded,
    status = FosterHelpStatus.fromString(status),
    urgency = FosterUrgency.fromString(urgency),
    createdBy = createdBy,
    createdAt = parseTs(createdAt),
    closedAt = parseTsOrNull(closedAt),
    receivedAmountMinor = receivedAmountMinor,
    receivedQuantity = receivedQuantity
)

fun FosterHelpContributionRow.toContributionDomain(): FosterHelpContribution = FosterHelpContribution(
    id = id,
    helpRequestId = helpRequestId,
    contributorUserId = contributorUserId,
    description = description,
    amountMinor = amountMinor,
    quantity = quantity,
    status = FosterContributionStatus.fromString(status),
    recordedAt = parseTs(recordedAt)
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

    suspend fun listHistory(): List<FosterPlacementRow> =
        decodeList("m10_list_foster_history")

    suspend fun completePlacement(
        placementId: String,
        reason: String,
        notes: String?
    ): FosterPlacementRow =
        decodeOne(
            "m10_complete_foster_placement",
            buildJsonObject {
                put("p_placement_id", placementId)
                put("p_end_reason", reason)
                put("p_end_notes", notes)
            }
        )

    suspend fun cancelPlacement(placementId: String, reason: String?): FosterPlacementRow =
        decodeOne(
            "m10_cancel_foster_placement",
            buildJsonObject {
                put("p_placement_id", placementId)
                put("p_reason", reason)
            }
        )

    suspend fun listExpenses(placementId: String): List<FosterExpenseRow> =
        decodeList(
            "m10_list_foster_expenses",
            buildJsonObject { put("p_placement_id", placementId) }
        )

    suspend fun addExpense(params: JsonObject): FosterExpenseRow =
        decodeOne("m10_add_foster_expense", params)

    suspend fun listEvolution(placementId: String): List<FosterEvolutionRow> =
        decodeList(
            "m10_list_foster_evolution",
            buildJsonObject { put("p_placement_id", placementId) }
        )

    suspend fun addEvolution(params: JsonObject): FosterEvolutionRow =
        decodeOne("m10_add_foster_evolution", params)

    suspend fun listHelp(placementId: String): List<FosterHelpRequestRow> =
        decodeList(
            "m10_list_help_requests",
            buildJsonObject { put("p_placement_id", placementId) }
        )

    suspend fun getHelp(helpRequestId: String): FosterHelpRequestRow =
        decodeOne(
            "m10_get_help_request",
            buildJsonObject { put("p_help_request_id", helpRequestId) }
        )

    suspend fun createHelp(params: JsonObject): FosterHelpRequestRow =
        decodeOne("m10_create_help_request", params)

    suspend fun updateHelpStatus(helpRequestId: String, status: String): FosterHelpRequestRow =
        decodeOne(
            "m10_update_help_request_status",
            buildJsonObject {
                put("p_help_request_id", helpRequestId)
                put("p_status", status)
            }
        )

    suspend fun recordContribution(params: JsonObject): FosterHelpContributionRow =
        decodeOne("m10_record_help_contribution", params)
}
