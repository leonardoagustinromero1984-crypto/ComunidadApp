package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM15FosterHomeInput
import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterHomeProfile
import com.comunidapp.app.data.model.FosterHomePublicListing
import com.comunidapp.app.data.model.FosterHomeRequest
import com.comunidapp.app.data.model.FosterHomeRequestStatus
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.FosterPlacement
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.data.model.M15FosterAvailabilityStatus
import com.comunidapp.app.data.model.M15FosterHome
import com.comunidapp.app.data.model.M15FosterHomePublicListing
import com.comunidapp.app.data.model.M15FosterHomeStatus
import com.comunidapp.app.data.model.M15FosterPlacement
import com.comunidapp.app.data.model.M15FosterPlacementStatus
import com.comunidapp.app.data.model.M15FosterRequest
import com.comunidapp.app.data.model.M15FosterRequestStatus
import com.comunidapp.app.data.model.M15FosterUrgency
import com.comunidapp.app.data.model.SubmitM15FosterRequestInput
import com.comunidapp.app.data.model.UpdateM15FosterHomeInput

/** M15 ↔ M10 domain mappers (Bloque 2 — persistencia M10 autoritativa). */

fun FosterHomeProfile.toM15(): M15FosterHome = M15FosterHome(
    id = id,
    ownerUserId = ownerUserId,
    displayName = displayName,
    description = description,
    status = status.toM15(),
    availabilityStatus = availabilityStatus.toM15(),
    totalCapacity = totalCapacity,
    currentOccupancy = currentOccupancy,
    reservedCount = reservedCount,
    acceptedSpecies = acceptedSpecies,
    acceptedSizes = acceptedSizes,
    acceptsSpecialNeeds = acceptsSpecialNeeds,
    acceptsEmergencies = acceptsEmergencies,
    zoneText = zoneText,
    publicLocationText = publicLocationText,
    privateAddressText = privateAddressText,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun FosterHomePublicListing.toM15(): M15FosterHomePublicListing = M15FosterHomePublicListing(
    id = id,
    displayName = displayName,
    description = description,
    availabilityStatus = availabilityStatus.toM15(),
    totalCapacity = totalCapacity,
    freeSlots = freeSlots,
    acceptedSpecies = acceptedSpecies,
    acceptedSizes = acceptedSizes,
    acceptsSpecialNeeds = acceptsSpecialNeeds,
    acceptsEmergencies = acceptsEmergencies,
    zoneText = zoneText,
    publicLocationText = publicLocationText
)

fun FosterHomeRequest.toM15(): M15FosterRequest = M15FosterRequest(
    id = id,
    fosterHomeId = fosterHomeId,
    petId = petId,
    petName = petName,
    requesterUserId = requesterUserId,
    requesterOrganizationId = requesterOrganizationId,
    message = message,
    urgency = urgency.toM15(),
    requestedStartAt = requestedStartAt,
    estimatedEndAt = estimatedEndAt,
    specialNeeds = specialNeeds,
    status = status.toM15(),
    createdAt = createdAt,
    reviewedAt = reviewedAt,
    reviewedBy = reviewedBy,
    rejectionReason = rejectionReason
)

fun FosterPlacement.toM15(): M15FosterPlacement = M15FosterPlacement(
    id = id,
    fosterRequestId = fosterRequestId,
    fosterHomeId = fosterHomeId,
    petId = petId,
    petName = petName,
    requesterUserId = requesterUserId,
    requesterOrganizationId = requesterOrganizationId,
    fosterUserId = fosterUserId,
    status = status.toM15(),
    startedAt = startedAt,
    estimatedEndAt = estimatedEndAt,
    initialNotes = initialNotes
)

fun CreateM15FosterHomeInput.toFoster(): CreateFosterHomeInput = CreateFosterHomeInput(
    displayName = displayName,
    description = description,
    totalCapacity = totalCapacity,
    acceptedSpecies = acceptedSpecies,
    acceptedSizes = acceptedSizes,
    acceptsSpecialNeeds = acceptsSpecialNeeds,
    acceptsEmergencies = acceptsEmergencies,
    zoneText = zoneText,
    publicLocationText = publicLocationText,
    privateAddressText = privateAddressText,
    activate = activate
)

fun UpdateM15FosterHomeInput.toFoster(): UpdateFosterHomeInput = UpdateFosterHomeInput(
    homeId = homeId,
    displayName = displayName,
    description = description,
    totalCapacity = totalCapacity,
    acceptedSpecies = acceptedSpecies,
    acceptedSizes = acceptedSizes,
    acceptsSpecialNeeds = acceptsSpecialNeeds,
    acceptsEmergencies = acceptsEmergencies,
    zoneText = zoneText,
    publicLocationText = publicLocationText,
    privateAddressText = privateAddressText
)

fun SubmitM15FosterRequestInput.toFoster(): SubmitFosterRequestInput = SubmitFosterRequestInput(
    fosterHomeId = fosterHomeId,
    petId = petId,
    message = message,
    urgency = urgency.toFoster(),
    requestedStartAt = requestedStartAt,
    estimatedEndAt = estimatedEndAt,
    specialNeeds = specialNeeds,
    requesterOrganizationId = requesterOrganizationId
)

private fun FosterHomeStatus.toM15(): M15FosterHomeStatus = when (this) {
    FosterHomeStatus.DRAFT -> M15FosterHomeStatus.DRAFT
    FosterHomeStatus.ACTIVE -> M15FosterHomeStatus.ACTIVE
    FosterHomeStatus.PAUSED -> M15FosterHomeStatus.PAUSED
    FosterHomeStatus.SUSPENDED -> M15FosterHomeStatus.SUSPENDED
    FosterHomeStatus.CLOSED -> M15FosterHomeStatus.CLOSED
    FosterHomeStatus.UNKNOWN -> M15FosterHomeStatus.DRAFT
}

private fun FosterAvailabilityStatus.toM15(): M15FosterAvailabilityStatus = when (this) {
    FosterAvailabilityStatus.AVAILABLE -> M15FosterAvailabilityStatus.AVAILABLE
    FosterAvailabilityStatus.LIMITED -> M15FosterAvailabilityStatus.LIMITED
    FosterAvailabilityStatus.FULL -> M15FosterAvailabilityStatus.FULL
    FosterAvailabilityStatus.UNAVAILABLE -> M15FosterAvailabilityStatus.UNAVAILABLE
    FosterAvailabilityStatus.UNKNOWN -> M15FosterAvailabilityStatus.UNAVAILABLE
}

private fun FosterHomeRequestStatus.toM15(): M15FosterRequestStatus = when (this) {
    FosterHomeRequestStatus.SUBMITTED -> M15FosterRequestStatus.SUBMITTED
    FosterHomeRequestStatus.UNDER_REVIEW -> M15FosterRequestStatus.UNDER_REVIEW
    FosterHomeRequestStatus.ACCEPTED -> M15FosterRequestStatus.ACCEPTED
    FosterHomeRequestStatus.REJECTED -> M15FosterRequestStatus.REJECTED
    FosterHomeRequestStatus.CANCELLED -> M15FosterRequestStatus.CANCELLED
    FosterHomeRequestStatus.EXPIRED -> M15FosterRequestStatus.EXPIRED
    FosterHomeRequestStatus.UNKNOWN -> M15FosterRequestStatus.SUBMITTED
}

private fun FosterPlacementStatus.toM15(): M15FosterPlacementStatus = when (this) {
    FosterPlacementStatus.RESERVED -> M15FosterPlacementStatus.RESERVED
    FosterPlacementStatus.ACTIVE -> M15FosterPlacementStatus.ACTIVE
    FosterPlacementStatus.COMPLETED -> M15FosterPlacementStatus.COMPLETED
    FosterPlacementStatus.CANCELLED -> M15FosterPlacementStatus.CANCELLED
    FosterPlacementStatus.UNKNOWN -> M15FosterPlacementStatus.RESERVED
}

private fun FosterUrgency.toM15(): M15FosterUrgency = when (this) {
    FosterUrgency.NORMAL -> M15FosterUrgency.NORMAL
    FosterUrgency.HIGH -> M15FosterUrgency.HIGH
    FosterUrgency.EMERGENCY -> M15FosterUrgency.EMERGENCY
    FosterUrgency.UNKNOWN -> M15FosterUrgency.NORMAL
}

private fun M15FosterUrgency.toFoster(): FosterUrgency = when (this) {
    M15FosterUrgency.NORMAL -> FosterUrgency.NORMAL
    M15FosterUrgency.HIGH -> FosterUrgency.HIGH
    M15FosterUrgency.EMERGENCY -> FosterUrgency.EMERGENCY
}
