package com.comunidapp.app.data.remote.supabase.m11

import com.comunidapp.app.data.model.ShelterIntakeType
import com.comunidapp.app.data.model.ShelterPetEndReason
import com.comunidapp.app.data.model.ShelterPetPlacement
import com.comunidapp.app.data.model.ShelterPetPlacementStatus
import com.comunidapp.app.data.model.ShelterProfile
import com.comunidapp.app.data.model.ShelterStatus
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
}
