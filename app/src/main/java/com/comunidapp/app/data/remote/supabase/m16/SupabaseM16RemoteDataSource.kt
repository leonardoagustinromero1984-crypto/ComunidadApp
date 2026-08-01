package com.comunidapp.app.data.remote.supabase.m16

import com.comunidapp.app.data.model.M16OpeningHours
import com.comunidapp.app.data.model.M16OpeningPeriod
import com.comunidapp.app.data.model.M16PublicContactChannel
import com.comunidapp.app.data.model.M16PublicContactChannelType
import com.comunidapp.app.data.model.M16PublicShelter
import com.comunidapp.app.data.model.M16ShelterAvailabilityStatus
import com.comunidapp.app.data.model.M16ShelterCapacity
import com.comunidapp.app.data.model.M16ShelterNeed
import com.comunidapp.app.data.model.M16ShelterOperationalStatus
import com.comunidapp.app.data.model.M16ShelterProfile
import com.comunidapp.app.data.model.M16ShelterPublicationStatus
import com.comunidapp.app.data.model.M16ShelterService
import com.comunidapp.app.data.model.M16ShelterVerificationStatus
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private fun parseTs(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private fun JsonElement?.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonElement?.asIntOrNull(default: Int = 0): Int =
    (this as? JsonPrimitive)?.intOrNull ?: default

private fun JsonObject.string(key: String): String? = this[key].asStringOrNull()

private fun JsonObject.int(key: String, default: Int = 0): Int = this[key].asIntOrNull(default)

private fun parseOpeningHours(obj: JsonObject?): M16OpeningHours {
    if (obj == null) return M16OpeningHours()
    val periods = obj["periods"]?.jsonArray?.map { elem ->
        val p = elem.jsonObject
        M16OpeningPeriod(
            dayOfWeek = p.int("day_of_week", 1),
            closed = p["closed"]?.jsonPrimitive?.content == "true",
            openTime = p.string("open_time"),
            closeTime = p.string("close_time")
        )
    }.orEmpty()
    return M16OpeningHours(
        zoneIdName = obj.string("zone_id_name") ?: M16OpeningHours.DEFAULT_ZONE,
        periods = periods
    )
}

private fun parseContacts(arr: JsonArray?): List<M16PublicContactChannel> =
    arr?.mapNotNull { elem ->
        val c = elem.jsonObject
        val typeRaw = c.string("type") ?: return@mapNotNull null
        val value = c.string("value") ?: return@mapNotNull null
        val type = runCatching { M16PublicContactChannelType.valueOf(typeRaw) }.getOrNull()
            ?: return@mapNotNull null
        M16PublicContactChannel(type = type, value = value, label = c.string("label"))
    }.orEmpty()

private fun parseNeeds(arr: JsonArray?): List<M16ShelterNeed> =
    arr?.mapNotNull { elem ->
        val n = elem.jsonObject
        val cat = n.string("category") ?: return@mapNotNull null
        val desc = n.string("description") ?: return@mapNotNull null
        M16ShelterNeed(category = cat, description = desc)
    }.orEmpty()

private fun parseServices(arr: JsonArray?): Set<M16ShelterService> =
    arr?.mapNotNull { elem ->
        runCatching { M16ShelterService.valueOf(elem.jsonPrimitive.content) }.getOrNull()
    }?.toSet().orEmpty()

private fun parseSpecies(arr: JsonArray?): Set<String> =
    arr?.mapNotNull { it.jsonPrimitive.contentOrNull?.uppercase() }?.toSet().orEmpty()

private fun parseCoverage(arr: JsonArray?): Set<String> =
    arr?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet().orEmpty()

fun JsonObject.toM16ShelterProfile(): M16ShelterProfile {
    val capObj = this["capacity"]?.jsonObject
    val totalCap = capObj?.int("total_capacity") ?: int("total_capacity")
    val occupancy = capObj?.int("current_occupancy") ?: 0
    val reserved = capObj?.int("reserved_count") ?: 0
    val orgId = string("organization_id") ?: ""
    return M16ShelterProfile(
        id = string("id").orEmpty(),
        organizationId = orgId,
        displayName = string("display_name").orEmpty(),
        description = string("description"),
        operationalStatus = runCatching {
            M16ShelterOperationalStatus.valueOf(string("operational_status").orEmpty())
        }.getOrDefault(M16ShelterOperationalStatus.ACTIVE),
        publicationStatus = runCatching {
            M16ShelterPublicationStatus.valueOf(string("publication_status").orEmpty())
        }.getOrDefault(M16ShelterPublicationStatus.DRAFT),
        verificationStatus = runCatching {
            M16ShelterVerificationStatus.valueOf(string("verification_status").orEmpty())
        }.getOrDefault(M16ShelterVerificationStatus.UNVERIFIED),
        publicZoneText = string("public_zone_text").orEmpty(),
        coverageAreas = parseCoverage(this["coverage_areas"]?.jsonArray),
        openingHours = parseOpeningHours(this["opening_hours"]?.jsonObject),
        acceptedSpecies = parseSpecies(this["accepted_species"]?.jsonArray),
        services = parseServices(this["services"]?.jsonArray),
        publicContacts = parseContacts(this["public_contacts"]?.jsonArray),
        capacity = M16ShelterCapacity(
            totalCapacity = totalCap,
            currentOccupancy = occupancy,
            reservedCount = reserved
        ),
        needs = parseNeeds(this["needs"]?.jsonArray),
        publicImageRef = string("public_image_ref"),
        internalNotes = string("internal_notes"),
        createdAt = parseTs(string("created_at")),
        updatedAt = parseTs(string("updated_at"))
    )
}

fun JsonObject.toM16PublicShelter(): M16PublicShelter = M16PublicShelter(
    id = string("id").orEmpty(),
    displayName = string("display_name").orEmpty(),
    description = string("description"),
    operationalStatus = runCatching {
        M16ShelterOperationalStatus.valueOf(string("operational_status").orEmpty())
    }.getOrDefault(M16ShelterOperationalStatus.ACTIVE),
    publicationStatus = runCatching {
        M16ShelterPublicationStatus.valueOf(string("publication_status").orEmpty())
    }.getOrDefault(M16ShelterPublicationStatus.PUBLISHED),
    verificationStatus = runCatching {
        M16ShelterVerificationStatus.valueOf(string("verification_status").orEmpty())
    }.getOrDefault(M16ShelterVerificationStatus.UNVERIFIED),
    publicZoneText = string("public_zone_text").orEmpty(),
    coverageAreas = parseCoverage(this["coverage_areas"]?.jsonArray),
    openingHours = parseOpeningHours(this["opening_hours"]?.jsonObject),
    acceptedSpecies = parseSpecies(this["accepted_species"]?.jsonArray),
    services = parseServices(this["services"]?.jsonArray),
    publicContacts = parseContacts(this["public_contacts"]?.jsonArray),
    totalCapacity = int("total_capacity"),
    freeSlotsApproximate = int("free_slots_approximate"),
    availability = runCatching {
        M16ShelterAvailabilityStatus.valueOf(string("availability").orEmpty())
    }.getOrDefault(M16ShelterAvailabilityStatus.UNAVAILABLE),
    needs = parseNeeds(this["needs"]?.jsonArray),
    publicImageRef = string("public_image_ref")
)

fun M16OpeningHours.toJsonPeriods(): JsonArray = buildJsonArray {
    periods.forEach { p ->
        add(
            buildJsonObject {
                put("day_of_week", p.dayOfWeek)
                put("closed", p.closed)
                if (!p.closed) {
                    put("open_time", p.openTime)
                    put("close_time", p.closeTime)
                }
            }
        )
    }
}

fun List<M16PublicContactChannel>.toJsonContacts(): JsonArray = buildJsonArray {
    forEach { c ->
        add(
            buildJsonObject {
                put("type", c.type.name)
                put("value", c.value)
                put("label", c.label)
                put("is_public", true)
            }
        )
    }
}

fun List<M16ShelterNeed>.toJsonNeeds(): JsonArray = buildJsonArray {
    forEach { n ->
        add(
            buildJsonObject {
                put("category", n.category)
                put("description", n.description)
            }
        )
    }
}

class SupabaseM16RemoteDataSource {

    private suspend inline fun <reified T : Any> decodeOne(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeSingle()

    private suspend inline fun <reified T : Any> decodeList(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeList()

    suspend fun listPublic(
        query: String? = null,
        species: String? = null,
        service: String? = null,
        operationalStatus: String? = null,
        verifiedOnly: Boolean = false,
        unverifiedOrPending: Boolean = false
    ): List<JsonObject> = decodeList(
        "m16_list_public_shelters",
        buildJsonObject {
            put("p_query", query)
            put("p_species", species)
            put("p_service", service)
            put("p_operational_status", operationalStatus)
            put("p_verified_only", verifiedOnly)
            put("p_unverified_or_pending", unverifiedOrPending)
        }
    )

    suspend fun getPublic(shelterId: String): JsonObject = decodeOne(
        "m16_get_public_shelter",
        buildJsonObject { put("p_shelter_id", shelterId) }
    )

    suspend fun getProfile(shelterId: String): JsonObject = decodeOne(
        "m16_get_shelter_profile",
        buildJsonObject { put("p_shelter_id", shelterId) }
    )

    suspend fun getByOrganization(organizationId: String): JsonObject? = try {
        decodeOne<JsonObject>(
            "m16_get_shelter_by_organization",
            buildJsonObject { put("p_organization_id", organizationId) }
        )
    } catch (_: Exception) {
        null
    }

    suspend fun isOrganizationEligible(organizationId: String): Boolean = decodeOne(
        "m16_is_organization_eligible",
        buildJsonObject { put("p_organization_id", organizationId) }
    )

    suspend fun createProfile(params: JsonObject): JsonObject = decodeOne("m16_create_shelter_profile", params)

    suspend fun updatePublicData(params: JsonObject): JsonObject =
        decodeOne("m16_update_shelter_public_data", params)

    suspend fun updateOperationalStatus(shelterId: String, status: String): JsonObject = decodeOne(
        "m16_update_operational_status",
        buildJsonObject {
            put("p_shelter_id", shelterId)
            put("p_status", status)
        }
    )

    suspend fun updatePublicationStatus(shelterId: String, status: String): JsonObject = decodeOne(
        "m16_update_publication_status",
        buildJsonObject {
            put("p_shelter_id", shelterId)
            put("p_status", status)
        }
    )

    suspend fun requestVerification(shelterId: String): JsonObject = decodeOne(
        "m16_request_verification",
        buildJsonObject { put("p_shelter_id", shelterId) }
    )

    suspend fun updateOpeningHours(shelterId: String, zoneId: String, periods: JsonArray): JsonObject =
        decodeOne(
            "m16_update_opening_hours",
            buildJsonObject {
                put("p_shelter_id", shelterId)
                put("p_zone_id_name", zoneId)
                put("p_periods", periods)
            }
        )

    suspend fun updateServices(shelterId: String, services: List<String>): JsonObject = decodeOne(
        "m16_update_services",
        buildJsonObject {
            put("p_shelter_id", shelterId)
            putJsonArray("p_services") { services.forEach { add(JsonPrimitive(it)) } }
        }
    )

    suspend fun updateNeeds(shelterId: String, needs: JsonArray): JsonObject = decodeOne(
        "m16_update_needs",
        buildJsonObject {
            put("p_shelter_id", shelterId)
            put("p_needs", needs)
        }
    )

    suspend fun updateCapacity(
        shelterId: String,
        total: Int,
        occupancy: Int?,
        reserved: Int?
    ): JsonObject = decodeOne(
        "m16_update_capacity",
        buildJsonObject {
            put("p_shelter_id", shelterId)
            put("p_total_capacity", total)
            occupancy?.let { put("p_current_occupancy", it) }
            reserved?.let { put("p_reserved_count", it) }
        }
    )

    suspend fun updatePublicContacts(shelterId: String, contacts: JsonArray): JsonObject = decodeOne(
        "m16_update_public_contacts",
        buildJsonObject {
            put("p_shelter_id", shelterId)
            put("p_contacts", contacts)
        }
    )
}
