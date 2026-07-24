package com.comunidapp.app.data.remote.supabase.m11

import com.comunidapp.app.data.model.ShelterCampaign
import com.comunidapp.app.data.model.ShelterCampaignCategory
import com.comunidapp.app.data.model.ShelterCampaignMetrics
import com.comunidapp.app.data.model.ShelterCampaignStatus
import com.comunidapp.app.data.model.ShelterCampaignUpdate
import com.comunidapp.app.data.model.ShelterCampaignVisibility
import com.comunidapp.app.data.model.ShelterCapacityMetrics
import com.comunidapp.app.data.model.ShelterEmergency
import com.comunidapp.app.data.model.ShelterEmergencyCategory
import com.comunidapp.app.data.model.ShelterEmergencyMetrics
import com.comunidapp.app.data.model.ShelterEmergencySeverity
import com.comunidapp.app.data.model.ShelterEmergencyStatus
import com.comunidapp.app.data.model.ShelterEmergencyVisibility
import com.comunidapp.app.data.model.ShelterEvent
import com.comunidapp.app.data.model.ShelterEventMetrics
import com.comunidapp.app.data.model.ShelterEventRegistration
import com.comunidapp.app.data.model.ShelterEventRegistrationStatus
import com.comunidapp.app.data.model.ShelterEventStatus
import com.comunidapp.app.data.model.ShelterEventType
import com.comunidapp.app.data.model.ShelterEventVisibility
import com.comunidapp.app.data.model.ShelterOperationalSummary
import com.comunidapp.app.data.model.ShelterPetMetrics
import com.comunidapp.app.data.model.ShelterSupplyMetrics
import com.comunidapp.app.data.model.ShelterVolunteerMetrics
import com.comunidapp.app.data.model.ShelterIntakeType
import com.comunidapp.app.data.model.ShelterPetEndReason
import com.comunidapp.app.data.model.ShelterPetPlacement
import com.comunidapp.app.data.model.ShelterPetPlacementStatus
import com.comunidapp.app.data.model.ShelterProfile
import com.comunidapp.app.data.model.ShelterStatus
import com.comunidapp.app.data.model.ShelterSupplyCategory
import com.comunidapp.app.data.model.ShelterSupplyContribution
import com.comunidapp.app.data.model.ShelterSupplyContributionStatus
import com.comunidapp.app.data.model.ShelterSupplyPriority
import com.comunidapp.app.data.model.ShelterSupplyRequest
import com.comunidapp.app.data.model.ShelterSupplyRequestStatus
import com.comunidapp.app.data.model.ShelterVolunteerAssignment
import com.comunidapp.app.data.model.ShelterVolunteerRole
import com.comunidapp.app.data.model.ShelterVolunteerStatus
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ShelterProfileRow(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("branch_id") val branchId: String? = null,
    @SerialName("display_name") val displayName: String,
    val description: String? = null,
    val status: String = "DRAFT",
    @SerialName("total_capacity") val totalCapacity: Int = 1,
    @SerialName("current_occupancy") val currentOccupancy: Int = 0,
    @SerialName("reserved_capacity") val reservedCapacity: Int = 0,
    @SerialName("accepted_species") val acceptedSpecies: List<String> = emptyList(),
    @SerialName("accepts_emergencies") val acceptsEmergencies: Boolean = false,
    @SerialName("public_zone_text") val publicZoneText: String? = null,
    @SerialName("internal_address_ref") val internalAddressRef: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ShelterPetPlacementRow(
    val id: String,
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("intake_type") val intakeType: String,
    val status: String,
    @SerialName("admitted_at") val admittedAt: String? = null,
    @SerialName("admitted_by") val admittedBy: String,
    @SerialName("source_organization_id") val sourceOrganizationId: String? = null,
    @SerialName("source_user_id") val sourceUserId: String? = null,
    val notes: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("organizational_responsibility_id") val organizationalResponsibilityId: String? = null,
    @SerialName("related_foster_placement_id") val relatedFosterPlacementId: String? = null
)

@Serializable
data class ShelterVolunteerRow(
    val id: String,
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    val status: String,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    val notes: String? = null,
    @SerialName("invited_by") val invitedBy: String? = null
)

private fun parseTs(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private fun parseTsOrNull(value: String?): Long? =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

fun ShelterProfileRow.toDomain(): ShelterProfile = ShelterProfile(
    id = id,
    organizationId = organizationId,
    branchId = branchId,
    displayName = displayName,
    description = description,
    status = ShelterStatus.fromString(status),
    totalCapacity = totalCapacity,
    currentOccupancy = currentOccupancy,
    reservedCapacity = reservedCapacity,
    acceptedSpecies = acceptedSpecies.map { it.uppercase() }.toSet(),
    acceptsEmergencies = acceptsEmergencies,
    publicZoneText = publicZoneText,
    internalAddressRef = internalAddressRef,
    createdAt = parseTs(createdAt),
    updatedAt = parseTs(updatedAt)
)

fun ShelterPetPlacementRow.toDomain(): ShelterPetPlacement = ShelterPetPlacement(
    id = id,
    shelterProfileId = shelterProfileId,
    petId = petId,
    intakeType = ShelterIntakeType.fromString(intakeType),
    status = ShelterPetPlacementStatus.fromString(status),
    admittedAt = parseTs(admittedAt),
    admittedBy = admittedBy,
    sourceOrganizationId = sourceOrganizationId,
    sourceUserId = sourceUserId,
    notes = notes,
    endedAt = parseTsOrNull(endedAt),
    endReason = endReason?.let { ShelterPetEndReason.fromString(it) },
    organizationalResponsibilityId = organizationalResponsibilityId,
    relatedFosterPlacementId = relatedFosterPlacementId
)

fun ShelterVolunteerRow.toDomain(): ShelterVolunteerAssignment = ShelterVolunteerAssignment(
    id = id,
    shelterProfileId = shelterProfileId,
    userId = userId,
    role = ShelterVolunteerRole.fromString(role),
    status = ShelterVolunteerStatus.fromString(status),
    startsAt = parseTs(startsAt),
    endsAt = parseTsOrNull(endsAt),
    notes = notes,
    invitedBy = invitedBy
)

@Serializable
data class ShelterCampaignRow(
    val id: String,
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    val title: String,
    val description: String,
    val category: String,
    val visibility: String,
    val status: String,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("cover_asset_ref") val coverAssetRef: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("shelter_display_name") val shelterDisplayName: String? = null
)

@Serializable
data class ShelterCampaignUpdateRow(
    val id: String,
    @SerialName("campaign_id") val campaignId: String,
    @SerialName("author_user_id") val authorUserId: String,
    val visibility: String,
    val message: String,
    @SerialName("evidence_ref") val evidenceRef: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ShelterSupplyRequestRow(
    val id: String,
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    @SerialName("campaign_id") val campaignId: String? = null,
    val category: String,
    @SerialName("item_name") val itemName: String,
    val description: String? = null,
    @SerialName("quantity_requested") val quantityRequested: Int,
    @SerialName("quantity_committed") val quantityCommitted: Int = 0,
    @SerialName("quantity_received") val quantityReceived: Int = 0,
    @SerialName("unit_text") val unitText: String,
    val priority: String,
    val status: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("public_notes") val publicNotes: String? = null,
    @SerialName("internal_notes") val internalNotes: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("shelter_display_name") val shelterDisplayName: String? = null
)

@Serializable
data class ShelterSupplyContributionRow(
    val id: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("contributor_user_id") val contributorUserId: String,
    @SerialName("quantity_committed") val quantityCommitted: Int,
    @SerialName("quantity_received") val quantityReceived: Int = 0,
    val status: String,
    @SerialName("contributor_notes") val contributorNotes: String? = null,
    @SerialName("internal_receipt_notes") val internalReceiptNotes: String? = null,
    @SerialName("evidence_ref") val evidenceRef: String? = null,
    @SerialName("committed_at") val committedAt: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("cancelled_at") val cancelledAt: String? = null
)

fun ShelterCampaignRow.toDomain(): ShelterCampaign = ShelterCampaign(
    id = id,
    shelterProfileId = shelterProfileId,
    title = title,
    description = description,
    category = ShelterCampaignCategory.fromString(category),
    visibility = ShelterCampaignVisibility.fromString(visibility),
    status = ShelterCampaignStatus.fromString(status),
    startsAt = parseTsOrNull(startsAt),
    endsAt = parseTsOrNull(endsAt),
    coverAssetRef = coverAssetRef,
    createdBy = createdBy,
    createdAt = parseTs(createdAt),
    updatedAt = parseTs(updatedAt)
)

fun ShelterCampaignUpdateRow.toDomain(): ShelterCampaignUpdate = ShelterCampaignUpdate(
    id = id,
    campaignId = campaignId,
    authorUserId = authorUserId,
    visibility = ShelterCampaignVisibility.fromString(visibility),
    message = message,
    evidenceRef = evidenceRef,
    createdAt = parseTs(createdAt)
)

fun ShelterSupplyRequestRow.toDomain(): ShelterSupplyRequest = ShelterSupplyRequest(
    id = id,
    shelterProfileId = shelterProfileId,
    campaignId = campaignId,
    category = ShelterSupplyCategory.fromString(category),
    itemName = itemName,
    description = description,
    quantityRequested = quantityRequested,
    quantityCommitted = quantityCommitted,
    quantityReceived = quantityReceived,
    unitText = unitText,
    priority = ShelterSupplyPriority.fromString(priority),
    status = ShelterSupplyRequestStatus.fromString(status),
    expiresAt = parseTsOrNull(expiresAt),
    publicNotes = publicNotes,
    internalNotes = internalNotes,
    createdBy = createdBy,
    createdAt = parseTs(createdAt),
    updatedAt = parseTs(updatedAt)
)

fun ShelterSupplyContributionRow.toDomain(): ShelterSupplyContribution = ShelterSupplyContribution(
    id = id,
    requestId = requestId,
    contributorUserId = contributorUserId,
    quantityCommitted = quantityCommitted,
    quantityReceived = quantityReceived,
    status = ShelterSupplyContributionStatus.fromString(status),
    contributorNotes = contributorNotes,
    internalReceiptNotes = internalReceiptNotes,
    evidenceRef = evidenceRef,
    committedAt = parseTs(committedAt),
    receivedAt = parseTsOrNull(receivedAt),
    cancelledAt = parseTsOrNull(cancelledAt)
)

@Serializable
data class ShelterEmergencyRow(
    val id: String,
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    @SerialName("pet_id") val petId: String? = null,
    val title: String,
    val description: String,
    val category: String,
    val severity: String,
    val visibility: String,
    val status: String,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("resolution_notes") val resolutionNotes: String? = null,
    @SerialName("evidence_ref") val evidenceRef: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("shelter_display_name") val shelterDisplayName: String? = null
)

@Serializable
data class ShelterEventRow(
    val id: String,
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    val title: String,
    val description: String,
    @SerialName("event_type") val eventType: String,
    val visibility: String,
    val status: String,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    val capacity: Int? = null,
    @SerialName("registered_count") val registeredCount: Int = 0,
    @SerialName("public_location_text") val publicLocationText: String? = null,
    @SerialName("private_location_text") val privateLocationText: String? = null,
    @SerialName("cover_asset_ref") val coverAssetRef: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("shelter_display_name") val shelterDisplayName: String? = null
)

@Serializable
data class ShelterEventRegistrationRow(
    val id: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("user_id") val userId: String,
    val status: String,
    val notes: String? = null,
    @SerialName("registered_at") val registeredAt: String? = null,
    @SerialName("cancelled_at") val cancelledAt: String? = null
)

@Serializable
data class ShelterCapacityMetricsRow(
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    val from: Long,
    val to: Long,
    @SerialName("total_capacity") val totalCapacity: Int = 0,
    @SerialName("current_occupancy") val currentOccupancy: Int = 0,
    @SerialName("reserved_capacity") val reservedCapacity: Int = 0,
    @SerialName("free_slots") val freeSlots: Int = 0
)

@Serializable
data class ShelterPetMetricsRow(
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    val from: Long,
    val to: Long,
    @SerialName("active_count") val activeCount: Int = 0,
    @SerialName("quarantine_count") val quarantineCount: Int = 0,
    @SerialName("medical_care_count") val medicalCareCount: Int = 0,
    @SerialName("releases_count") val releasesCount: Int = 0,
    @SerialName("adoptions_count") val adoptionsCount: Int = 0
)

@Serializable
data class ShelterVolunteerMetricsRow(
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    val from: Long,
    val to: Long,
    @SerialName("active_count") val activeCount: Int = 0,
    @SerialName("paused_count") val pausedCount: Int = 0,
    @SerialName("ended_count") val endedCount: Int = 0
)

@Serializable
data class ShelterCampaignMetricsRow(
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    val from: Long,
    val to: Long,
    @SerialName("active_count") val activeCount: Int = 0,
    @SerialName("completed_count") val completedCount: Int = 0
)

@Serializable
data class ShelterSupplyMetricsRow(
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    val from: Long,
    val to: Long,
    @SerialName("open_requests_count") val openRequestsCount: Int = 0,
    @SerialName("fulfilled_requests_count") val fulfilledRequestsCount: Int = 0,
    @SerialName("quantity_received_total") val quantityReceivedTotal: Int = 0
)

@Serializable
data class ShelterEmergencyMetricsRow(
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    val from: Long,
    val to: Long,
    @SerialName("active_count") val activeCount: Int = 0,
    @SerialName("critical_count") val criticalCount: Int = 0,
    @SerialName("resolved_count") val resolvedCount: Int = 0
)

@Serializable
data class ShelterEventMetricsRow(
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    val from: Long,
    val to: Long,
    @SerialName("upcoming_count") val upcomingCount: Int = 0,
    @SerialName("completed_count") val completedCount: Int = 0,
    @SerialName("registrations_count") val registrationsCount: Int = 0
)

@Serializable
data class ShelterOperationalSummaryRow(
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    val from: Long,
    val to: Long,
    val capacity: ShelterCapacityMetricsRow,
    val pets: ShelterPetMetricsRow,
    val volunteers: ShelterVolunteerMetricsRow,
    val campaigns: ShelterCampaignMetricsRow,
    val supplies: ShelterSupplyMetricsRow,
    val emergencies: ShelterEmergencyMetricsRow,
    val events: ShelterEventMetricsRow,
    @SerialName("generated_at") val generatedAt: String? = null
)

fun ShelterEmergencyRow.toDomain(): ShelterEmergency = ShelterEmergency(
    id = id,
    shelterProfileId = shelterProfileId,
    petId = petId,
    title = title,
    description = description,
    category = ShelterEmergencyCategory.fromString(category),
    severity = ShelterEmergencySeverity.fromString(severity),
    visibility = ShelterEmergencyVisibility.fromString(visibility),
    status = ShelterEmergencyStatus.fromString(status),
    startsAt = parseTs(startsAt),
    expiresAt = parseTsOrNull(expiresAt),
    resolvedAt = parseTsOrNull(resolvedAt),
    resolutionNotes = resolutionNotes,
    evidenceRef = evidenceRef,
    createdBy = createdBy,
    createdAt = parseTs(createdAt),
    updatedAt = parseTs(updatedAt)
)

fun ShelterEventRow.toDomain(): ShelterEvent = ShelterEvent(
    id = id,
    shelterProfileId = shelterProfileId,
    title = title,
    description = description,
    eventType = ShelterEventType.fromString(eventType),
    visibility = ShelterEventVisibility.fromString(visibility),
    status = ShelterEventStatus.fromString(status),
    startsAt = parseTs(startsAt),
    endsAt = parseTs(endsAt),
    capacity = capacity,
    registeredCount = registeredCount,
    publicLocationText = publicLocationText,
    privateLocationText = privateLocationText,
    coverAssetRef = coverAssetRef,
    createdBy = createdBy,
    createdAt = parseTs(createdAt),
    updatedAt = parseTs(updatedAt)
)

fun ShelterEventRegistrationRow.toDomain(): ShelterEventRegistration = ShelterEventRegistration(
    id = id,
    eventId = eventId,
    userId = userId,
    status = ShelterEventRegistrationStatus.fromString(status),
    notes = notes,
    registeredAt = parseTs(registeredAt),
    cancelledAt = parseTsOrNull(cancelledAt)
)

fun ShelterCapacityMetricsRow.toDomain(): ShelterCapacityMetrics = ShelterCapacityMetrics(
    shelterProfileId = shelterProfileId,
    from = from,
    to = to,
    totalCapacity = totalCapacity,
    currentOccupancy = currentOccupancy,
    reservedCapacity = reservedCapacity,
    freeSlots = freeSlots
)

fun ShelterPetMetricsRow.toDomain(): ShelterPetMetrics = ShelterPetMetrics(
    shelterProfileId = shelterProfileId,
    from = from,
    to = to,
    activeCount = activeCount,
    quarantineCount = quarantineCount,
    medicalCareCount = medicalCareCount,
    releasesCount = releasesCount,
    adoptionsCount = adoptionsCount
)

fun ShelterVolunteerMetricsRow.toDomain(): ShelterVolunteerMetrics = ShelterVolunteerMetrics(
    shelterProfileId = shelterProfileId,
    from = from,
    to = to,
    activeCount = activeCount,
    pausedCount = pausedCount,
    endedCount = endedCount
)

fun ShelterCampaignMetricsRow.toDomain(): ShelterCampaignMetrics = ShelterCampaignMetrics(
    shelterProfileId = shelterProfileId,
    from = from,
    to = to,
    activeCount = activeCount,
    completedCount = completedCount
)

fun ShelterSupplyMetricsRow.toDomain(): ShelterSupplyMetrics = ShelterSupplyMetrics(
    shelterProfileId = shelterProfileId,
    from = from,
    to = to,
    openRequestsCount = openRequestsCount,
    fulfilledRequestsCount = fulfilledRequestsCount,
    quantityReceivedTotal = quantityReceivedTotal
)

fun ShelterEmergencyMetricsRow.toDomain(): ShelterEmergencyMetrics = ShelterEmergencyMetrics(
    shelterProfileId = shelterProfileId,
    from = from,
    to = to,
    activeCount = activeCount,
    criticalCount = criticalCount,
    resolvedCount = resolvedCount
)

fun ShelterEventMetricsRow.toDomain(): ShelterEventMetrics = ShelterEventMetrics(
    shelterProfileId = shelterProfileId,
    from = from,
    to = to,
    upcomingCount = upcomingCount,
    completedCount = completedCount,
    registrationsCount = registrationsCount
)

fun ShelterOperationalSummaryRow.toDomain(): ShelterOperationalSummary = ShelterOperationalSummary(
    shelterProfileId = shelterProfileId,
    from = from,
    to = to,
    capacity = capacity.toDomain(),
    pets = pets.toDomain(),
    volunteers = volunteers.toDomain(),
    campaigns = campaigns.toDomain(),
    supplies = supplies.toDomain(),
    emergencies = emergencies.toDomain(),
    events = events.toDomain(),
    generatedAt = parseTs(generatedAt)
)

class SupabaseShelterM11RemoteDataSource {
    private suspend inline fun <reified T : Any> decodeOne(
        function: String,
        parameters: JsonObject
    ): T = supabase.postgrest.rpc(function = function, parameters = parameters).decodeSingle()

    private suspend inline fun <reified T : Any> decodeList(
        function: String,
        parameters: JsonObject = buildJsonObject { }
    ): List<T> = supabase.postgrest.rpc(function = function, parameters = parameters).decodeList()

    suspend fun listPublic(): List<ShelterProfileRow> = decodeList("m11_list_public_shelters")
    suspend fun listMine(): List<ShelterProfileRow> = decodeList("m11_get_my_shelter_profiles")
    suspend fun get(id: String): ShelterProfileRow =
        decodeOne("m11_get_shelter_profile", buildJsonObject { put("p_shelter_id", id) })
    suspend fun create(params: JsonObject): ShelterProfileRow =
        decodeOne("m11_create_shelter_profile", params)
    suspend fun update(params: JsonObject): ShelterProfileRow =
        decodeOne("m11_update_shelter_profile", params)
    suspend fun changeStatus(id: String, status: String): ShelterProfileRow =
        decodeOne(
            "m11_change_shelter_status",
            buildJsonObject {
                put("p_shelter_id", id)
                put("p_status", status)
            }
        )

    suspend fun listPets(shelterId: String): List<ShelterPetPlacementRow> =
        decodeList("m11_list_shelter_pets", buildJsonObject { put("p_shelter_id", shelterId) })
    suspend fun getPet(id: String): ShelterPetPlacementRow =
        decodeOne("m11_get_shelter_pet_placement", buildJsonObject { put("p_placement_id", id) })
    suspend fun reserve(params: JsonObject): ShelterPetPlacementRow =
        decodeOne("m11_reserve_shelter_capacity", params)
    suspend fun admit(params: JsonObject): ShelterPetPlacementRow =
        decodeOne("m11_admit_pet", params)
    suspend fun changePetStatus(id: String, status: String): ShelterPetPlacementRow =
        decodeOne(
            "m11_change_pet_placement_status",
            buildJsonObject {
                put("p_placement_id", id)
                put("p_status", status)
            }
        )
    suspend fun release(id: String, reason: String, notes: String?): ShelterPetPlacementRow =
        decodeOne(
            "m11_release_pet",
            buildJsonObject {
                put("p_placement_id", id)
                put("p_end_reason", reason)
                put("p_notes", notes)
            }
        )

    suspend fun listVolunteers(shelterId: String): List<ShelterVolunteerRow> =
        decodeList("m11_list_shelter_volunteers", buildJsonObject { put("p_shelter_id", shelterId) })
    suspend fun invite(params: JsonObject): ShelterVolunteerRow =
        decodeOne("m11_invite_volunteer", params)
    suspend fun acceptVol(id: String): ShelterVolunteerRow =
        decodeOne("m11_accept_volunteer_assignment", buildJsonObject { put("p_assignment_id", id) })
    suspend fun pauseVol(id: String): ShelterVolunteerRow =
        decodeOne("m11_pause_volunteer_assignment", buildJsonObject { put("p_assignment_id", id) })
    suspend fun endVol(id: String): ShelterVolunteerRow =
        decodeOne("m11_end_volunteer_assignment", buildJsonObject { put("p_assignment_id", id) })

    // --- M11 Bloque 2: campañas ---
    suspend fun listPublicCampaigns(): List<ShelterCampaignRow> =
        decodeList("m11_list_public_shelter_campaigns")
    suspend fun listShelterCampaigns(shelterId: String): List<ShelterCampaignRow> =
        decodeList("m11_list_shelter_campaigns", buildJsonObject { put("p_shelter_id", shelterId) })
    suspend fun getCampaign(id: String): ShelterCampaignRow =
        decodeOne("m11_get_shelter_campaign", buildJsonObject { put("p_campaign_id", id) })
    suspend fun createCampaign(params: JsonObject): ShelterCampaignRow =
        decodeOne("m11_create_shelter_campaign", params)
    suspend fun updateCampaign(params: JsonObject): ShelterCampaignRow =
        decodeOne("m11_update_shelter_campaign", params)
    suspend fun changeCampaignStatus(id: String, status: String): ShelterCampaignRow =
        decodeOne(
            "m11_change_shelter_campaign_status",
            buildJsonObject {
                put("p_campaign_id", id)
                put("p_status", status)
            }
        )
    suspend fun addCampaignUpdate(params: JsonObject): ShelterCampaignUpdateRow =
        decodeOne("m11_add_shelter_campaign_update", params)
    suspend fun listCampaignUpdates(campaignId: String): List<ShelterCampaignUpdateRow> =
        decodeList(
            "m11_list_shelter_campaign_updates",
            buildJsonObject { put("p_campaign_id", campaignId) }
        )

    // --- M11 Bloque 2: pedidos de insumos ---
    suspend fun listPublicSupplyRequests(): List<ShelterSupplyRequestRow> =
        decodeList("m11_list_public_supply_requests")
    suspend fun listShelterSupplyRequests(shelterId: String): List<ShelterSupplyRequestRow> =
        decodeList("m11_list_shelter_supply_requests", buildJsonObject { put("p_shelter_id", shelterId) })
    suspend fun getSupplyRequest(id: String): ShelterSupplyRequestRow =
        decodeOne("m11_get_supply_request", buildJsonObject { put("p_request_id", id) })
    suspend fun createSupplyRequest(params: JsonObject): ShelterSupplyRequestRow =
        decodeOne("m11_create_supply_request", params)
    suspend fun updateSupplyRequest(params: JsonObject): ShelterSupplyRequestRow =
        decodeOne("m11_update_supply_request", params)
    suspend fun cancelSupplyRequest(id: String): ShelterSupplyRequestRow =
        decodeOne("m11_cancel_supply_request", buildJsonObject { put("p_request_id", id) })

    // --- M11 Bloque 2: aportes ---
    suspend fun pledgeContribution(params: JsonObject): ShelterSupplyContributionRow =
        decodeOne("m11_pledge_supply_contribution", params)
    suspend fun cancelContribution(id: String): ShelterSupplyContributionRow =
        decodeOne("m11_cancel_supply_contribution", buildJsonObject { put("p_contribution_id", id) })
    suspend fun confirmContribution(id: String): ShelterSupplyContributionRow =
        decodeOne(
            "m11_confirm_supply_contribution",
            buildJsonObject { put("p_contribution_id", id) }
        )
    suspend fun recordSupplyReceipt(params: JsonObject): ShelterSupplyContributionRow =
        decodeOne("m11_record_supply_receipt", params)
    suspend fun getSupplyContribution(id: String): ShelterSupplyContributionRow =
        decodeOne("m11_get_supply_contribution", buildJsonObject { put("p_contribution_id", id) })
    suspend fun listSupplyContributions(requestId: String): List<ShelterSupplyContributionRow> =
        decodeList(
            "m11_list_supply_contributions",
            buildJsonObject { put("p_request_id", requestId) }
        )

    // --- M11 Bloque 3: urgencias ---
    suspend fun listPublicEmergencies(): List<ShelterEmergencyRow> =
        decodeList("m11_list_public_shelter_emergencies")
    suspend fun listShelterEmergencies(shelterId: String): List<ShelterEmergencyRow> =
        decodeList("m11_list_shelter_emergencies", buildJsonObject { put("p_shelter_id", shelterId) })
    suspend fun getEmergency(id: String): ShelterEmergencyRow =
        decodeOne("m11_get_shelter_emergency", buildJsonObject { put("p_emergency_id", id) })
    suspend fun createEmergency(params: JsonObject): ShelterEmergencyRow =
        decodeOne("m11_create_shelter_emergency", params)
    suspend fun updateEmergency(params: JsonObject): ShelterEmergencyRow =
        decodeOne("m11_update_shelter_emergency", params)
    suspend fun changeEmergencyStatus(id: String, status: String): ShelterEmergencyRow =
        decodeOne(
            "m11_change_shelter_emergency_status",
            buildJsonObject {
                put("p_emergency_id", id)
                put("p_status", status)
            }
        )
    suspend fun resolveEmergency(id: String, notes: String): ShelterEmergencyRow =
        decodeOne(
            "m11_resolve_shelter_emergency",
            buildJsonObject {
                put("p_emergency_id", id)
                put("p_resolution_notes", notes)
            }
        )

    // --- M11 Bloque 3: eventos ---
    suspend fun listPublicEvents(): List<ShelterEventRow> =
        decodeList("m11_list_public_shelter_events")
    suspend fun listShelterEvents(shelterId: String): List<ShelterEventRow> =
        decodeList("m11_list_shelter_events", buildJsonObject { put("p_shelter_id", shelterId) })
    suspend fun getEvent(id: String): ShelterEventRow =
        decodeOne("m11_get_shelter_event", buildJsonObject { put("p_event_id", id) })
    suspend fun createEvent(params: JsonObject): ShelterEventRow =
        decodeOne("m11_create_shelter_event", params)
    suspend fun updateEvent(params: JsonObject): ShelterEventRow =
        decodeOne("m11_update_shelter_event", params)
    suspend fun changeEventStatus(id: String, status: String): ShelterEventRow =
        decodeOne(
            "m11_change_shelter_event_status",
            buildJsonObject {
                put("p_event_id", id)
                put("p_status", status)
            }
        )
    suspend fun registerEvent(params: JsonObject): ShelterEventRegistrationRow =
        decodeOne("m11_register_shelter_event", params)
    suspend fun cancelEventRegistration(id: String): ShelterEventRegistrationRow =
        decodeOne(
            "m11_cancel_shelter_event_registration",
            buildJsonObject { put("p_registration_id", id) }
        )
    suspend fun markEventAttendance(id: String, attended: Boolean): ShelterEventRegistrationRow =
        decodeOne(
            "m11_mark_shelter_event_attendance",
            buildJsonObject {
                put("p_registration_id", id)
                put("p_attended", attended)
            }
        )
    suspend fun listEventRegistrations(eventId: String): List<ShelterEventRegistrationRow> =
        decodeList(
            "m11_list_shelter_event_registrations",
            buildJsonObject { put("p_event_id", eventId) }
        )

    // --- M11 Bloque 3: reportes ---
    suspend fun getOperationalSummary(shelterId: String, from: Long, to: Long): ShelterOperationalSummaryRow =
        decodeOne(
            "m11_get_shelter_operational_summary",
            buildJsonObject {
                put("p_shelter_id", shelterId)
                put("p_from", from)
                put("p_to", to)
            }
        )
    suspend fun getCapacityMetrics(shelterId: String, from: Long, to: Long): ShelterCapacityMetricsRow =
        decodeOne(
            "m11_get_shelter_capacity_metrics",
            buildJsonObject {
                put("p_shelter_id", shelterId)
                put("p_from", from)
                put("p_to", to)
            }
        )
    suspend fun getPetMetrics(shelterId: String, from: Long, to: Long): ShelterPetMetricsRow =
        decodeOne(
            "m11_get_shelter_pet_metrics",
            buildJsonObject {
                put("p_shelter_id", shelterId)
                put("p_from", from)
                put("p_to", to)
            }
        )
    suspend fun getVolunteerMetrics(shelterId: String, from: Long, to: Long): ShelterVolunteerMetricsRow =
        decodeOne(
            "m11_get_shelter_volunteer_metrics",
            buildJsonObject {
                put("p_shelter_id", shelterId)
                put("p_from", from)
                put("p_to", to)
            }
        )
    suspend fun getCampaignMetrics(shelterId: String, from: Long, to: Long): ShelterCampaignMetricsRow =
        decodeOne(
            "m11_get_shelter_campaign_metrics",
            buildJsonObject {
                put("p_shelter_id", shelterId)
                put("p_from", from)
                put("p_to", to)
            }
        )
    suspend fun getSupplyMetrics(shelterId: String, from: Long, to: Long): ShelterSupplyMetricsRow =
        decodeOne(
            "m11_get_shelter_supply_metrics",
            buildJsonObject {
                put("p_shelter_id", shelterId)
                put("p_from", from)
                put("p_to", to)
            }
        )
    suspend fun getEmergencyMetrics(shelterId: String, from: Long, to: Long): ShelterEmergencyMetricsRow =
        decodeOne(
            "m11_get_shelter_emergency_metrics",
            buildJsonObject {
                put("p_shelter_id", shelterId)
                put("p_from", from)
                put("p_to", to)
            }
        )
    suspend fun getEventMetrics(shelterId: String, from: Long, to: Long): ShelterEventMetricsRow =
        decodeOne(
            "m11_get_shelter_event_metrics",
            buildJsonObject {
                put("p_shelter_id", shelterId)
                put("p_from", from)
                put("p_to", to)
            }
        )
}
