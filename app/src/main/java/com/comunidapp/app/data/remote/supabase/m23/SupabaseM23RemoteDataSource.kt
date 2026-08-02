package com.comunidapp.app.data.remote.supabase.m23

import com.comunidapp.app.data.model.M23AvailabilityException
import com.comunidapp.app.data.model.M23AvailabilityRule
import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23BookingModality
import com.comunidapp.app.data.model.M23BookingStatus
import com.comunidapp.app.data.model.M23ExceptionType
import com.comunidapp.app.data.model.M23AvailabilityRuleStatus
import com.comunidapp.app.data.model.M23BookableSlot
import com.comunidapp.app.data.model.M23ScheduleDay
import com.comunidapp.app.data.model.M23SlotPage
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.text(key: String): String? = this[key].text()
private fun JsonObject.number(key: String): Int? = (this[key] as? JsonPrimitive)?.longOrNull?.toInt()
private inline fun <reified T : Enum<T>> m23Enum(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback

fun JsonObject.toM23Booking(): M23Booking = M23Booking(
    id = text("id").orEmpty(),
    providerId = text("provider_id").orEmpty(),
    offeringId = text("offering_id").orEmpty(),
    customerUserId = text("customer_user_id").orEmpty(),
    startsAt = text("starts_at")?.let(Instant::parse) ?: Instant.EPOCH,
    endsAt = text("ends_at")?.let(Instant::parse) ?: Instant.EPOCH,
    zoneId = runCatching { ZoneId.of(text("zone_id") ?: "UTC") }.getOrDefault(ZoneId.of("UTC")),
    modality = m23Enum(text("modality"), M23BookingModality.IN_PERSON),
    status = m23Enum(text("status"), M23BookingStatus.REQUESTED),
    customerNote = text("customer_note"),
    createdAt = text("created_at")?.let(Instant::parse) ?: Instant.EPOCH,
    updatedAt = text("updated_at")?.let(Instant::parse) ?: Instant.EPOCH,
    idempotencyKey = text("client_request_id")
)

fun JsonObject.toM23AvailabilityRule(): M23AvailabilityRule = M23AvailabilityRule(
    id = text("id").orEmpty(),
    providerId = text("provider_id").orEmpty(),
    offeringId = text("offering_id").orEmpty(),
    dayOfWeek = DayOfWeek.of(number("day_of_week")?.coerceIn(1, 7) ?: 1),
    startTime = text("start_time")?.let(LocalTime::parse) ?: LocalTime.MIDNIGHT,
    endTime = text("end_time")?.let(LocalTime::parse) ?: LocalTime.MIDNIGHT,
    slotDurationMinutes = number("slot_duration_minutes") ?: 30,
    zoneId = runCatching { ZoneId.of(text("zone_id") ?: "UTC") }.getOrDefault(ZoneId.of("UTC")),
    status = m23Enum(text("status"), M23AvailabilityRuleStatus.ACTIVE)
)

fun JsonObject.toM23AvailabilityException(): M23AvailabilityException = M23AvailabilityException(
    id = text("id").orEmpty(),
    providerId = text("provider_id").orEmpty(),
    offeringId = text("offering_id"),
    date = text("date")?.let(LocalDate::parse) ?: LocalDate.MIN,
    startTime = text("start_time")?.let(LocalTime::parse),
    endTime = text("end_time")?.let(LocalTime::parse),
    type = m23Enum(text("type"), M23ExceptionType.OTHER),
    note = text("note")
)

fun JsonObject.toM23SlotPage(providerId: String, offeringId: String, zoneId: ZoneId): M23SlotPage =
    M23SlotPage((this["days"] as? JsonArray).orEmpty().mapNotNull { element ->
        val day = element as? JsonObject ?: return@mapNotNull null
        val date = day.text("date")?.let(LocalDate::parse) ?: return@mapNotNull null
        val slots = (day["slots"] as? JsonArray).orEmpty().mapNotNull { slotElement ->
            val slot = slotElement as? JsonObject ?: return@mapNotNull null
            val startsAt = slot.text("starts_at")?.let(Instant::parse) ?: return@mapNotNull null
            val endsAt = slot.text("ends_at")?.let(Instant::parse) ?: return@mapNotNull null
            M23BookableSlot(providerId, offeringId, startsAt, endsAt, m23Enum(slot.text("modality"), M23BookingModality.IN_PERSON))
        }
        M23ScheduleDay(date, slots)
    })

/**
 * M23 only invokes allowlisted SECURITY DEFINER RPCs; no scheduling table is
 * accessed directly from the client.
 */
class SupabaseM23RemoteDataSource {
    private suspend inline fun <reified T : Any> one(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function, parameters).decodeSingle()

    private suspend inline fun <reified T : Any> list(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function, parameters).decodeList()

    suspend fun publicSlots(providerId: String, offeringId: String, from: String, to: String): JsonObject =
        one("m23_get_public_available_slots", buildJsonObject {
            put("p_provider_id", providerId); put("p_offering_id", offeringId); put("p_from", from); put("p_to", to)
        })

    suspend fun listRules(providerId: String): List<JsonObject> =
        list("m23_list_availability_rules", buildJsonObject { put("p_provider_id", providerId) })

    suspend fun createRule(rule: M23AvailabilityRule): JsonObject =
        one("m23_create_availability_rule", buildJsonObject {
            put("p_provider_id", rule.providerId); put("p_offering_id", rule.offeringId); put("p_branch_id", null as String?)
            put("p_day_of_week", rule.dayOfWeek.value); put("p_start_time", rule.startTime.toString())
            put("p_end_time", rule.endTime.toString()); put("p_slot_duration_minutes", rule.slotDurationMinutes)
            put("p_zone_id", rule.zoneId.id); put("p_status", rule.status.name)
        })

    suspend fun createException(exception: M23AvailabilityException): JsonObject =
        one("m23_create_availability_exception", buildJsonObject {
            put("p_provider_id", exception.providerId); put("p_offering_id", exception.offeringId); put("p_branch_id", null as String?)
            put("p_exception_date", exception.date.toString()); put("p_start_time", exception.startTime?.toString())
            put("p_end_time", exception.endTime?.toString()); put("p_type", exception.type.name); put("p_note", exception.note)
        })

    suspend fun createBooking(booking: M23Booking): JsonObject =
        one("m23_create_booking_request", buildJsonObject {
            put("p_provider_id", booking.providerId); put("p_offering_id", booking.offeringId); put("p_branch_id", null as String?)
            put("p_starts_at", booking.startsAt.toString()); put("p_ends_at", booking.endsAt.toString())
            put("p_zone_id", booking.zoneId.id); put("p_modality", booking.modality.name)
            put("p_customer_note", booking.customerNote); put("p_client_request_id", booking.idempotencyKey)
        })

    suspend fun listMyBookings(): List<JsonObject> = list("m23_list_my_bookings", buildJsonObject {})
    suspend fun getMyBooking(id: String): JsonObject = one("m23_get_my_booking", buildJsonObject { put("p_booking_id", id) })
    suspend fun cancelOwn(id: String, reason: String?): JsonObject =
        one("m23_cancel_own_booking", buildJsonObject { put("p_booking_id", id); put("p_reason", reason) })
    suspend fun listProviderBookings(providerId: String): List<JsonObject> =
        list("m23_list_provider_bookings", buildJsonObject { put("p_provider_id", providerId) })
    suspend fun confirm(id: String): JsonObject = one("m23_confirm_booking", buildJsonObject { put("p_booking_id", id) })
    suspend fun reject(id: String): JsonObject = one("m23_reject_booking", buildJsonObject { put("p_booking_id", id) })
    suspend fun cancelByProvider(id: String): JsonObject =
        one("m23_cancel_booking_by_provider", buildJsonObject { put("p_booking_id", id) })
    suspend fun complete(id: String): JsonObject = one("m23_complete_booking", buildJsonObject { put("p_booking_id", id) })
    suspend fun noShow(id: String): JsonObject = one("m23_mark_booking_no_show", buildJsonObject { put("p_booking_id", id) })
}
