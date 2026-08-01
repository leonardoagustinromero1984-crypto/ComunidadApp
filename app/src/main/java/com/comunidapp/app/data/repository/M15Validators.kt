package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM15FosterHomeInput
import com.comunidapp.app.data.model.M15FosterAvailabilityStatus
import com.comunidapp.app.data.model.M15FosterHomeStatus
import com.comunidapp.app.data.model.M15FosterPlacementStatus
import com.comunidapp.app.data.model.M15FosterRequestStatus
import com.comunidapp.app.data.model.SubmitM15FosterRequestInput
import com.comunidapp.app.data.model.UpdateM15FosterHomeInput

/**
 * LeoVer M15 — validadores locales (Bloque 1).
 */
object M15Validators {
    private const val MAX_NAME = 80
    private const val MAX_ZONE = 120
    private const val MAX_MESSAGE = 1000
    private const val MAX_DESCRIPTION = 500

    fun validateCreateHome(input: CreateM15FosterHomeInput): String? {
        if (input.displayName.isBlank() || input.displayName.length > MAX_NAME) {
            return "M15_INVALID_FOSTER_INPUT"
        }
        if (input.zoneText.isBlank() || input.zoneText.length > MAX_ZONE) {
            return "M15_INVALID_FOSTER_INPUT"
        }
        if (input.totalCapacity <= 0) return "M15_FOSTER_HOME_CAPACITY_INVALID"
        if (input.description != null && input.description.length > MAX_DESCRIPTION) {
            return "M15_INVALID_FOSTER_INPUT"
        }
        return null
    }

    fun validateUpdateHome(input: UpdateM15FosterHomeInput): String? {
        if (input.homeId.isBlank()) return "M15_FOSTER_HOME_NOT_FOUND"
        if (input.displayName.isBlank() || input.displayName.length > MAX_NAME) {
            return "M15_INVALID_FOSTER_INPUT"
        }
        if (input.zoneText.isBlank() || input.zoneText.length > MAX_ZONE) {
            return "M15_INVALID_FOSTER_INPUT"
        }
        if (input.totalCapacity <= 0) return "M15_FOSTER_HOME_CAPACITY_INVALID"
        return null
    }

    fun validateSubmitRequest(input: SubmitM15FosterRequestInput): String? {
        if (input.fosterHomeId.isBlank() || input.petId.isBlank()) {
            return "M15_FOSTER_REQUEST_NOT_FOUND"
        }
        if (input.message.isBlank() || input.message.length > MAX_MESSAGE) {
            return "M15_INVALID_FOSTER_INPUT"
        }
        return null
    }

    fun recomputeAvailability(
        status: M15FosterHomeStatus,
        capacity: Int,
        occupancy: Int,
        reserved: Int
    ): M15FosterAvailabilityStatus {
        if (status != M15FosterHomeStatus.ACTIVE) return M15FosterAvailabilityStatus.UNAVAILABLE
        val used = occupancy.coerceAtLeast(0) + reserved.coerceAtLeast(0)
        return when {
            used >= capacity.coerceAtLeast(0) -> M15FosterAvailabilityStatus.FULL
            used > 0 -> M15FosterAvailabilityStatus.LIMITED
            else -> M15FosterAvailabilityStatus.AVAILABLE
        }
    }

    fun canTransitionRequest(from: M15FosterRequestStatus, to: M15FosterRequestStatus): Boolean =
        when (from) {
            M15FosterRequestStatus.SUBMITTED ->
                to == M15FosterRequestStatus.UNDER_REVIEW ||
                    to == M15FosterRequestStatus.ACCEPTED ||
                    to == M15FosterRequestStatus.REJECTED ||
                    to == M15FosterRequestStatus.CANCELLED ||
                    to == M15FosterRequestStatus.EXPIRED
            M15FosterRequestStatus.UNDER_REVIEW ->
                to == M15FosterRequestStatus.ACCEPTED ||
                    to == M15FosterRequestStatus.REJECTED ||
                    to == M15FosterRequestStatus.CANCELLED
            M15FosterRequestStatus.ACCEPTED,
            M15FosterRequestStatus.REJECTED,
            M15FosterRequestStatus.CANCELLED,
            M15FosterRequestStatus.EXPIRED -> false
        }

    fun canTransitionPlacement(from: M15FosterPlacementStatus, to: M15FosterPlacementStatus): Boolean =
        when (from) {
            M15FosterPlacementStatus.RESERVED ->
                to == M15FosterPlacementStatus.ACTIVE ||
                    to == M15FosterPlacementStatus.CANCELLED
            M15FosterPlacementStatus.ACTIVE -> to == M15FosterPlacementStatus.COMPLETED
            M15FosterPlacementStatus.COMPLETED,
            M15FosterPlacementStatus.CANCELLED -> false
        }

    fun isTerminalRequest(status: M15FosterRequestStatus): Boolean =
        status == M15FosterRequestStatus.REJECTED ||
            status == M15FosterRequestStatus.CANCELLED ||
            status == M15FosterRequestStatus.EXPIRED

    fun isTerminalPlacement(status: M15FosterPlacementStatus): Boolean =
        status == M15FosterPlacementStatus.COMPLETED ||
            status == M15FosterPlacementStatus.CANCELLED

    fun wouldExceedCapacity(capacity: Int, occupancy: Int, reserved: Int, delta: Int): Boolean {
        val used = occupancy.coerceAtLeast(0) + reserved.coerceAtLeast(0) + delta
        return used > capacity.coerceAtLeast(0)
    }
}
