package com.comunidapp.app.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.DayOfWeek

/** LeoVer M23 — Agenda y reservas. IDs internos never cross the public boundary. */
enum class M23BookingStatus { REQUESTED, CONFIRMED, REJECTED, CANCELLED_BY_CUSTOMER, CANCELLED_BY_PROVIDER, COMPLETED, NO_SHOW, EXPIRED }
enum class M23ExceptionType { BLOCKED, SPECIAL_OPENING, HOLIDAY, PERSONAL_LEAVE, ORGANIZATION_CLOSURE, OTHER }
enum class M23AvailabilityRuleStatus { ACTIVE, INACTIVE, ARCHIVED }
enum class M23BookingModality { IN_PERSON, REMOTE, AT_CUSTOMER_LOCATION }

data class M23AvailabilityRule(
    val id: String,
    val providerId: String,
    val offeringId: String,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val slotDurationMinutes: Int,
    val zoneId: ZoneId,
    val status: M23AvailabilityRuleStatus = M23AvailabilityRuleStatus.ACTIVE
)
data class M23AvailabilityException(
    val id: String,
    val providerId: String,
    val offeringId: String? = null,
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val type: M23ExceptionType,
    val note: String? = null
)
data class M23AvailabilityWindow(val date: LocalDate, val startTime: LocalTime, val endTime: LocalTime, val zoneId: ZoneId)
data class M23BookableSlot(
    val providerId: String,
    val offeringId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val modality: M23BookingModality
)
data class M23ScheduleDay(val date: LocalDate, val slots: List<M23BookableSlot>)
data class M23SlotQuery(val providerId: String, val offeringId: String, val from: LocalDate, val to: LocalDate, val zoneId: ZoneId)
data class M23SlotPage(val days: List<M23ScheduleDay>, val nextDate: LocalDate? = null)

data class M23Booking(
    val id: String,
    val providerId: String,
    val offeringId: String,
    val customerUserId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val zoneId: ZoneId,
    val modality: M23BookingModality,
    val status: M23BookingStatus = M23BookingStatus.REQUESTED,
    val customerNote: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val idempotencyKey: String? = null
)
data class M23BookingSummary(val booking: M23Booking, val providerDisplayName: String, val offeringName: String)
data class M23PublicBookingContext(
    val providerDisplayName: String,
    val offeringName: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val zoneId: ZoneId,
    val modality: M23BookingModality,
    val status: M23BookingStatus
)
data class M23BookingCancellation(val bookingId: String, val reason: String? = null)
data class M23BookingRescheduleRequest(val bookingId: String, val startsAt: Instant, val endsAt: Instant, val zoneId: ZoneId)
data class M23BookingHistoryEntry(val at: Instant, val from: M23BookingStatus?, val to: M23BookingStatus, val reason: String? = null)
data class M23BookingPolicy(
    val providerId: String,
    val cancellation: M23CancellationPolicySnapshot = M23CancellationPolicySnapshot(),
    val reschedule: M23ReschedulePolicySnapshot = M23ReschedulePolicySnapshot(),
    val advance: M23AdvancePolicySnapshot = M23AdvancePolicySnapshot(),
    val noShow: M23NoShowPolicySnapshot = M23NoShowPolicySnapshot()
)
data class M23CancellationPolicySnapshot(val minimumNoticeMinutes: Int = 60)
data class M23ReschedulePolicySnapshot(val minimumNoticeMinutes: Int = 120)
data class M23AdvancePolicySnapshot(val minimumAdvanceMinutes: Int = 30, val maximumAdvanceDays: Int = 30)
data class M23NoShowPolicySnapshot(val graceMinutes: Int = 15)

data class M23PublicSlot(val providerDisplayName: String, val offeringName: String, val startsAt: Instant, val endsAt: Instant, val modality: M23BookingModality)
data class M23PublicBooking(val startsAt: Instant, val endsAt: Instant, val zoneId: ZoneId, val modality: M23BookingModality, val status: M23BookingStatus)

object M23MockUsers {
    const val CUSTOMER = "mock_user_customer"
    const val PROVIDER = M22MockUsers.PROVIDER
    const val OTHER_PROVIDER = M22MockUsers.OTHER_PROVIDER
    const val UNAUTHORIZED = M22MockUsers.UNAUTHORIZED
}
object M23MockProviderRefs {
    const val ACTIVE_MULTI_BRANCH = M22MockProviderIds.ACTIVE_MULTI_BRANCH
    const val DRAFT = M22MockProviderIds.DRAFT
    const val SUSPENDED = M22MockProviderIds.SUSPENDED
}
object M23MockOfferingIds {
    const val BATH = "m22_offer_fixed"
    const val GROOMING = "m22_offer_from"
    const val WALK = "m22_offer_walk"
}
object M23MockBookingIds {
    const val REQUESTED = "m23_booking_requested"
    const val CONFIRMED = "m23_booking_confirmed"
    const val COMPLETED = "m23_booking_completed"
    const val CANCELLED = "m23_booking_cancelled"
}
