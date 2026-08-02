package com.comunidapp.app.data.remote.supabase.m18

import com.comunidapp.app.data.model.M18CommunityEvent
import com.comunidapp.app.data.model.M18EventCapacitySummary
import com.comunidapp.app.data.model.M18EventReference
import com.comunidapp.app.data.model.M18EventRegistration
import com.comunidapp.app.data.model.M18EventReminder
import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18EventType
import com.comunidapp.app.data.model.M18PublicEvent
import com.comunidapp.app.data.model.M18PublicRegistrationStats
import com.comunidapp.app.data.model.M18RegistrationStatus
import com.comunidapp.app.data.model.M18ReminderStatus
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private fun parseTs(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private fun JsonElement?.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonElement?.asLongOrNull(): Long? =
    (this as? JsonPrimitive)?.longOrNull
        ?: (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonElement?.asIntOrNull(default: Int = 0): Int =
    (this as? JsonPrimitive)?.intOrNull ?: default

private fun JsonElement?.asBooleanOrNull(default: Boolean = false): Boolean =
    when (val p = this as? JsonPrimitive) {
        null -> default
        else -> when (p.contentOrNull?.lowercase()) {
            "true", "t", "1" -> true
            "false", "f", "0" -> false
            else -> default
        }
    }

private fun JsonObject.string(key: String): String? = this[key].asStringOrNull()

private fun JsonObject.long(key: String, default: Long = 0L): Long = this[key].asLongOrNull() ?: default

private fun JsonObject.int(key: String, default: Int = 0): Int = this[key].asIntOrNull(default)

private fun JsonObject.boolean(key: String, default: Boolean = false): Boolean =
    this[key].asBooleanOrNull(default)

private fun parseReference(obj: JsonObject?): M18EventReference {
    val ref = obj ?: return M18EventReference()
    return M18EventReference(
        petId = ref.string("pet_id"),
        petPublicName = ref.string("pet_public_name"),
        shelterProfileId = ref.string("shelter_profile_id"),
        shelterPublicName = ref.string("shelter_public_name"),
        publicLocationText = ref.string("public_location_text")
    )
}

private fun safeEnumEventStatus(raw: String?): M18EventStatus =
    runCatching { M18EventStatus.valueOf(raw.orEmpty()) }
        .getOrDefault(M18EventStatus.DRAFT)

private fun safeEnumEventType(raw: String?): M18EventType =
    runCatching { M18EventType.valueOf(raw.orEmpty()) }
        .getOrDefault(M18EventType.COMMUNITY_GATHERING)

private fun safeEnumRegistrationStatus(raw: String?): M18RegistrationStatus =
    runCatching { M18RegistrationStatus.valueOf(raw.orEmpty()) }
        .getOrDefault(M18RegistrationStatus.REGISTERED)

private fun safeEnumReminderStatus(raw: String?): M18ReminderStatus =
    runCatching { M18ReminderStatus.valueOf(raw.orEmpty()) }
        .getOrDefault(M18ReminderStatus.SCHEDULED)

fun JsonObject.toM18CommunityEvent(): M18CommunityEvent {
    val ref = parseReference(this["reference"]?.jsonObject)
    return M18CommunityEvent(
        id = string("id").orEmpty(),
        organizationId = string("organization_id").orEmpty(),
        organizationDisplayName = string("organization_display_name").orEmpty(),
        title = string("title").orEmpty(),
        description = string("description").orEmpty(),
        eventType = safeEnumEventType(string("event_type")),
        status = safeEnumEventStatus(string("status") ?: string("event_status")),
        venueName = string("venue_name"),
        reference = ref,
        coverImageRef = string("cover_image_ref"),
        maxCapacity = int("max_capacity"),
        waitlistEnabled = boolean("waitlist_enabled", default = true),
        startsAt = parseTs(string("starts_at")),
        endsAt = parseTs(string("ends_at")),
        checkInOpensAt = string("check_in_opens_at")?.let { parseTs(it) },
        checkInClosesAt = string("check_in_closes_at")?.let { parseTs(it) },
        internalNotes = string("internal_notes"),
        moderationStatus = string("moderation_status"),
        createdBy = string("created_by").orEmpty(),
        createdAt = parseTs(string("created_at")),
        updatedAt = parseTs(string("updated_at"))
    )
}

fun JsonObject.toM18PublicEvent(): M18PublicEvent {
    val ref = parseReference(this["reference"]?.jsonObject)
    return M18PublicEvent(
        id = string("id").orEmpty(),
        title = string("title").orEmpty(),
        description = string("description").orEmpty(),
        organizationDisplayName = string("organization_display_name").orEmpty(),
        eventType = safeEnumEventType(string("event_type")),
        status = safeEnumEventStatus(string("status")),
        venueName = string("venue_name"),
        reference = ref,
        coverImageRef = string("cover_image_ref"),
        maxCapacity = int("max_capacity"),
        registeredCount = int("registered_count"),
        waitlistCount = int("waitlist_count"),
        availableSpots = int("available_spots"),
        isFull = boolean("is_full"),
        isWaitlistOpen = boolean("is_waitlist_open"),
        isRegistrationOpen = boolean("is_registration_open"),
        startsAt = parseTs(string("starts_at")),
        endsAt = parseTs(string("ends_at"))
    )
}

fun JsonObject.toM18EventCapacitySummary(): M18EventCapacitySummary =
    M18EventCapacitySummary(
        maxCapacity = int("max_capacity"),
        registeredCount = int("registered_count"),
        waitlistCount = int("waitlist_count"),
        availableSpots = int("available_spots"),
        isFull = boolean("is_full"),
        isWaitlistOpen = boolean("is_waitlist_open")
    )

fun JsonObject.toM18PublicRegistrationStats(): M18PublicRegistrationStats =
    M18PublicRegistrationStats(
        registeredCount = int("registered_count"),
        waitlistCount = int("waitlist_count"),
        checkedInCount = int("checked_in_count")
    )

fun JsonObject.toM18EventRegistration(): M18EventRegistration =
    M18EventRegistration(
        id = string("id").orEmpty(),
        eventId = string("event_id").orEmpty(),
        userId = string("user_id").orEmpty(),
        status = safeEnumRegistrationStatus(string("status")),
        attendeeDisplayName = string("attendee_display_name"),
        registeredAt = parseTs(string("registered_at")),
        checkedInAt = string("checked_in_at")?.let { parseTs(it) },
        reminderScheduled = boolean("reminder_scheduled")
    )

fun JsonObject.toM18EventReminder(): M18EventReminder =
    M18EventReminder(
        id = string("id").orEmpty(),
        eventId = string("event_id").orEmpty(),
        userId = string("user_id").orEmpty(),
        scheduledFor = parseTs(string("scheduled_for")),
        status = safeEnumReminderStatus(string("status")),
        sentAt = string("sent_at")?.let { parseTs(it) }
    )

class SupabaseM18RemoteDataSource {

    private suspend inline fun <reified T : Any> decodeOne(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeSingle()

    private suspend inline fun <reified T : Any> decodeList(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeList()

    suspend fun listPublic(params: JsonObject): List<JsonObject> =
        decodeList("m18_list_public_events", params)

    suspend fun getPublic(eventId: String): JsonObject = decodeOne(
        "m18_get_public_event",
        buildJsonObject { put("p_event_id", eventId) }
    )

    suspend fun getPublicRegistrationStats(eventId: String): JsonObject = decodeOne(
        "m18_get_public_registration_stats",
        buildJsonObject { put("p_event_id", eventId) }
    )

    suspend fun getCapacitySummary(eventId: String): JsonObject = decodeOne(
        "m18_get_capacity_summary",
        buildJsonObject { put("p_event_id", eventId) }
    )

    suspend fun getEvent(eventId: String): JsonObject = decodeOne(
        "m18_get_event",
        buildJsonObject { put("p_event_id", eventId) }
    )

    suspend fun listOrgEvents(organizationId: String): List<JsonObject> = decodeList(
        "m18_list_org_events",
        buildJsonObject { put("p_organization_id", organizationId) }
    )

    suspend fun isOrganizationEligible(organizationId: String): Boolean = decodeOne(
        "m18_is_organization_eligible",
        buildJsonObject { put("p_organization_id", organizationId) }
    )

    suspend fun createEvent(params: JsonObject): JsonObject = decodeOne("m18_create_event", params)

    suspend fun updateEventDetails(params: JsonObject): JsonObject =
        decodeOne("m18_update_event_details", params)

    suspend fun updateEventCapacity(params: JsonObject): JsonObject =
        decodeOne("m18_update_event_capacity", params)

    suspend fun transitionEvent(eventId: String, targetStatus: String): JsonObject = decodeOne(
        "m18_transition_event",
        buildJsonObject {
            put("p_event_id", eventId)
            put("p_target_status", targetStatus)
        }
    )

    suspend fun registerForEvent(eventId: String): JsonObject = decodeOne(
        "m18_register_for_event",
        buildJsonObject { put("p_event_id", eventId) }
    )

    suspend fun cancelRegistration(eventId: String): JsonObject = decodeOne(
        "m18_cancel_registration",
        buildJsonObject { put("p_event_id", eventId) }
    )

    suspend fun checkInRegistration(registrationId: String): JsonObject = decodeOne(
        "m18_check_in_registration",
        buildJsonObject { put("p_registration_id", registrationId) }
    )

    suspend fun scheduleReminder(eventId: String): JsonObject = decodeOne(
        "m18_schedule_reminder",
        buildJsonObject { put("p_event_id", eventId) }
    )

    suspend fun getMyRegistration(eventId: String): JsonObject? = runCatching {
        decodeOne<JsonObject>(
            "m18_get_my_registration",
            buildJsonObject { put("p_event_id", eventId) }
        )
    }.getOrNull()

    suspend fun listRegistrationsForManage(eventId: String): List<JsonObject> = decodeList(
        "m18_list_registrations_for_manage",
        buildJsonObject { put("p_event_id", eventId) }
    )

    suspend fun promoteNextWaitlisted(eventId: String): JsonObject? = runCatching {
        decodeOne<JsonObject>(
            "m18_promote_next_waitlisted",
            buildJsonObject { put("p_event_id", eventId) }
        )
    }.getOrNull()

    suspend fun markAttendance(registrationId: String): JsonObject = decodeOne(
        "m18_mark_attendance",
        buildJsonObject { put("p_registration_id", registrationId) }
    )

    suspend fun markNoShow(registrationId: String): JsonObject = decodeOne(
        "m18_mark_no_show",
        buildJsonObject { put("p_registration_id", registrationId) }
    )

    suspend fun rejectRegistration(registrationId: String): JsonObject = decodeOne(
        "m18_reject_registration",
        buildJsonObject { put("p_registration_id", registrationId) }
    )
}
