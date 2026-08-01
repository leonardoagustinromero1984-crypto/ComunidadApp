package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM16ShelterProfileInput
import com.comunidapp.app.data.model.M16OpeningHours
import com.comunidapp.app.data.model.M16OpeningPeriod
import com.comunidapp.app.data.model.M16PublicContactChannel
import com.comunidapp.app.data.model.M16PublicContactChannelType
import com.comunidapp.app.data.model.M16ShelterCapacity
import com.comunidapp.app.data.model.M16ShelterOperationalStatus
import com.comunidapp.app.data.model.M16ShelterPublicationStatus
import com.comunidapp.app.data.model.UpdateM16ShelterPublicInput
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object M16ShelterValidators {
    private const val MAX_NAME = 120
    private const val MAX_ZONE = 120
    private const val MAX_DESCRIPTION = 2000
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun validateCreate(input: CreateM16ShelterProfileInput): String? {
        if (input.organizationId.isBlank()) return "M16_ORGANIZATION_NOT_ELIGIBLE"
        if (input.displayName.isBlank() || input.displayName.length > MAX_NAME) {
            return "M16_INVALID_SHELTER_INPUT"
        }
        if (input.publicZoneText.isBlank() || input.publicZoneText.length > MAX_ZONE) {
            return "M16_INVALID_SHELTER_INPUT"
        }
        validateCapacity(input.totalCapacity, 0, 0)?.let { return it }
        return null
    }

    fun validateUpdatePublic(input: UpdateM16ShelterPublicInput): String? {
        if (input.shelterId.isBlank()) return "M16_SHELTER_NOT_FOUND"
        if (input.displayName.isBlank() || input.displayName.length > MAX_NAME) {
            return "M16_INVALID_SHELTER_INPUT"
        }
        if (input.publicZoneText.isBlank() || input.publicZoneText.length > MAX_ZONE) {
            return "M16_INVALID_SHELTER_INPUT"
        }
        if (input.description != null && input.description.length > MAX_DESCRIPTION) {
            return "M16_INVALID_SHELTER_INPUT"
        }
        return null
    }

    fun validateCapacity(total: Int, occupancy: Int, reserved: Int): String? {
        if (total < 0) return "M16_INVALID_CAPACITY"
        if (occupancy < 0 || reserved < 0) return "M16_INVALID_CAPACITY"
        if (occupancy + reserved > total) return "M16_OCCUPANCY_EXCEEDS_CAPACITY"
        return null
    }

    fun validateCapacityModel(capacity: M16ShelterCapacity): String? =
        validateCapacity(capacity.totalCapacity, capacity.currentOccupancy, capacity.reservedCount)

    fun validateOpeningHours(hours: M16OpeningHours): String? {
        try {
            java.time.ZoneId.of(hours.zoneIdName)
        } catch (_: Exception) {
            return "M16_INVALID_OPENING_HOURS"
        }
        val byDay = hours.periods.groupBy { it.dayOfWeek }
        for ((day, periods) in byDay) {
            if (day !in 1..7) return "M16_INVALID_OPENING_HOURS"
            val openRanges = mutableListOf<Pair<LocalTime, LocalTime>>()
            for (period in periods) {
                if (period.closed) continue
                val open = parseTime(period.openTime) ?: return "M16_INVALID_OPENING_HOURS"
                val close = parseTime(period.closeTime) ?: return "M16_INVALID_OPENING_HOURS"
                if (!open.isBefore(close)) return "M16_INVALID_OPENING_HOURS"
                openRanges += open to close
            }
            val sorted = openRanges.sortedBy { it.first }
            for (i in 0 until sorted.lastIndex) {
                if (sorted[i].second > sorted[i + 1].first) return "M16_INVALID_OPENING_HOURS"
            }
        }
        return null
    }

    fun validatePublicContacts(contacts: List<M16PublicContactChannel>): String? {
        for (contact in contacts) {
            if (contact.value.isBlank()) return "M16_INVALID_PUBLIC_CONTACT"
            when (contact.type) {
                M16PublicContactChannelType.INSTITUTIONAL_EMAIL ->
                    if (!contact.value.contains('@')) return "M16_INVALID_PUBLIC_CONTACT"
                M16PublicContactChannelType.WEBSITE ->
                    if (!contact.value.startsWith("http", ignoreCase = true)) {
                        return "M16_INVALID_PUBLIC_CONTACT"
                    }
                else -> Unit
            }
        }
        return null
    }

    fun canTransitionOperational(
        from: M16ShelterOperationalStatus,
        to: M16ShelterOperationalStatus
    ): Boolean = when (from) {
        M16ShelterOperationalStatus.PERMANENTLY_CLOSED -> false
        M16ShelterOperationalStatus.ACTIVE -> to != M16ShelterOperationalStatus.ACTIVE
        M16ShelterOperationalStatus.PAUSED,
        M16ShelterOperationalStatus.TEMPORARILY_CLOSED -> to != from
    }

    fun canTransitionPublication(
        from: M16ShelterPublicationStatus,
        to: M16ShelterPublicationStatus
    ): Boolean = from != to

    private fun parseTime(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalTime.parse(raw.trim(), timeFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
