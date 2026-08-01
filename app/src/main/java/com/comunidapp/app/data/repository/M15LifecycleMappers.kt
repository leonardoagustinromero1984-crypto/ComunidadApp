package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AddM15EvolutionInput
import com.comunidapp.app.data.model.FosterEvolutionEntry
import com.comunidapp.app.data.model.FosterEvolutionVisibility
import com.comunidapp.app.data.model.FosterExpense
import com.comunidapp.app.data.model.FosterExpenseCategory
import com.comunidapp.app.data.model.FosterHealthStatus
import com.comunidapp.app.data.model.FosterHelpRequest
import com.comunidapp.app.data.model.FosterHelpStatus
import com.comunidapp.app.data.model.FosterHelpType
import com.comunidapp.app.data.model.FosterPlacement
import com.comunidapp.app.data.model.FosterPlacementEndReason
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.data.model.M15DischargeOutcome
import com.comunidapp.app.data.model.M15DischargeReason
import com.comunidapp.app.data.model.M15EvolutionEventType
import com.comunidapp.app.data.model.M15ExpenseCategory
import com.comunidapp.app.data.model.M15ExpenseStatus
import com.comunidapp.app.data.model.M15HelpPriority
import com.comunidapp.app.data.model.M15HelpRequestStatus
import com.comunidapp.app.data.model.M15HelpRequestType
import com.comunidapp.app.data.model.M15PlacementEvolution
import com.comunidapp.app.data.model.M15PlacementExpense
import com.comunidapp.app.data.model.M15PlacementHelpRequest
import com.comunidapp.app.data.model.AddM15ExpenseInput
import com.comunidapp.app.data.model.AddM15HelpRequestInput

/** M15 Bloque 3 ↔ M10 Foster mappers. */

private const val EVENT_PREFIX = "[M15:"

fun FosterEvolutionEntry.toM15Evolution(): M15PlacementEvolution {
    val (eventType, summary) = parseEventTitle(title, description)
    val privateNote = if (visibility == FosterEvolutionVisibility.PRIVATE_HOME) description else null
    return M15PlacementEvolution(
        id = id,
        placementId = placementId,
        eventType = eventType,
        summary = summary,
        privateNote = privateNote,
        healthAlert = healthStatus == FosterHealthStatus.NEEDS_ATTENTION ||
            healthStatus == FosterHealthStatus.CRITICAL ||
            eventType == M15EvolutionEventType.HEALTH_ALERT,
        mediaRefs = mediaRefs,
        createdBy = createdBy,
        createdAt = createdAt,
        occurredAt = occurredAt
    )
}

fun AddM15EvolutionInput.toFosterEvolution(): FosterEvolutionTriple = FosterEvolutionTriple(
    title = formatEventTitle(eventType, summary),
    description = privateNote?.trim()?.takeIf { it.isNotEmpty() } ?: summary.trim(),
    healthStatus = when {
        healthAlert || eventType == M15EvolutionEventType.HEALTH_ALERT ->
            FosterHealthStatus.NEEDS_ATTENTION
        else -> FosterHealthStatus.GOOD
    },
    visibility = if (!privateNote.isNullOrBlank()) {
        FosterEvolutionVisibility.PRIVATE_HOME
    } else {
        FosterEvolutionVisibility.PARTICIPANTS
    },
    mediaRefs = mediaRefs,
    occurredAt = occurredAt
)

data class FosterEvolutionTriple(
    val title: String,
    val description: String,
    val healthStatus: FosterHealthStatus,
    val visibility: FosterEvolutionVisibility,
    val mediaRefs: List<String>,
    val occurredAt: Long
)

fun FosterExpense.toM15Expense(): M15PlacementExpense = M15PlacementExpense(
    id = id,
    placementId = placementId,
    category = category.toM15ExpenseCategory(),
    amountMinor = amountMinor,
    currency = currency,
    occurredAt = occurredAt,
    description = description,
    receiptMediaRef = receiptRef,
    status = M15ExpenseStatus.RECORDED,
    createdBy = createdBy,
    createdAt = createdAt
)

fun AddM15ExpenseInput.toFosterExpenseCategory(): FosterExpenseCategory = when (category) {
    M15ExpenseCategory.FOOD -> FosterExpenseCategory.FOOD
    M15ExpenseCategory.VETERINARY -> FosterExpenseCategory.VETERINARY
    M15ExpenseCategory.MEDICATION -> FosterExpenseCategory.MEDICATION
    M15ExpenseCategory.TRANSPORT -> FosterExpenseCategory.TRANSPORT
    M15ExpenseCategory.HYGIENE -> FosterExpenseCategory.HYGIENE
    M15ExpenseCategory.ACCESSORIES -> FosterExpenseCategory.SUPPLIES
    M15ExpenseCategory.OTHER -> FosterExpenseCategory.OTHER
}

fun FosterHelpRequest.toM15HelpRequest(): M15PlacementHelpRequest = M15PlacementHelpRequest(
    id = id,
    placementId = placementId,
    type = type.toM15HelpType(),
    title = title,
    description = description,
    priority = urgency.toM15Priority(),
    status = status.toM15HelpStatus(),
    createdBy = createdBy,
    createdAt = createdAt,
    resolvedAt = closedAt
)

fun AddM15HelpRequestInput.toFosterHelpType(): FosterHelpType = when (type) {
    M15HelpRequestType.FOOD -> FosterHelpType.FOOD
    M15HelpRequestType.VETERINARY -> FosterHelpType.VETERINARY
    M15HelpRequestType.TRANSPORT -> FosterHelpType.TRANSPORT
    M15HelpRequestType.SUPPLIES -> FosterHelpType.SUPPLIES
    M15HelpRequestType.TEMPORARY_REPLACEMENT -> FosterHelpType.VOLUNTEER
    M15HelpRequestType.EMERGENCY -> FosterHelpType.OTHER
    M15HelpRequestType.OTHER -> FosterHelpType.OTHER
}

fun M15HelpRequestStatus.toFosterHelpStatus(): FosterHelpStatus = when (this) {
    M15HelpRequestStatus.OPEN -> FosterHelpStatus.OPEN
    M15HelpRequestStatus.IN_PROGRESS -> FosterHelpStatus.PAUSED
    M15HelpRequestStatus.RESOLVED -> FosterHelpStatus.FULFILLED
    M15HelpRequestStatus.CANCELLED -> FosterHelpStatus.CANCELLED
    M15HelpRequestStatus.EXPIRED -> FosterHelpStatus.CANCELLED
}

fun M15DischargeReason.toFosterEndReason(): FosterPlacementEndReason = when (this) {
    M15DischargeReason.RETURNED_TO_RESPONSIBLE -> FosterPlacementEndReason.RETURNED_TO_OWNER
    M15DischargeReason.ADOPTED -> FosterPlacementEndReason.ADOPTED
    M15DischargeReason.TRANSFERRED_TO_ANOTHER_FOSTER ->
        FosterPlacementEndReason.MOVED_TO_ANOTHER_FOSTER_HOME
    M15DischargeReason.TRANSFERRED_TO_SHELTER ->
        FosterPlacementEndReason.TRANSFERRED_TO_ORGANIZATION
    M15DischargeReason.VETERINARY_CARE -> FosterPlacementEndReason.HOSPITALIZED
    M15DischargeReason.INCOMPATIBILITY -> FosterPlacementEndReason.OTHER
    M15DischargeReason.EMERGENCY -> FosterPlacementEndReason.OTHER
    M15DischargeReason.OTHER -> FosterPlacementEndReason.OTHER
}

fun FosterPlacement.toM15DischargeFields(): Triple<M15DischargeReason?, M15DischargeOutcome?, String?> {
    if (endReason.isNullOrBlank()) return Triple(null, null, endNotes)
    val fosterReason = FosterPlacementEndReason.fromString(endReason)
    val m15Reason = fosterReason.toM15DischargeReason()
    val outcome = when (status) {
        FosterPlacementStatus.COMPLETED ->
            if (m15Reason == M15DischargeReason.EMERGENCY ||
                m15Reason == M15DischargeReason.INCOMPATIBILITY
            ) {
                M15DischargeOutcome.INTERRUPTED
            } else {
                M15DischargeOutcome.COMPLETED
            }
        FosterPlacementStatus.CANCELLED -> M15DischargeOutcome.CANCELLED
        else -> null
    }
    return Triple(m15Reason, outcome, endNotes)
}

private fun FosterPlacementEndReason.toM15DischargeReason(): M15DischargeReason = when (this) {
    FosterPlacementEndReason.RETURNED_TO_OWNER -> M15DischargeReason.RETURNED_TO_RESPONSIBLE
    FosterPlacementEndReason.ADOPTED -> M15DischargeReason.ADOPTED
    FosterPlacementEndReason.MOVED_TO_ANOTHER_FOSTER_HOME ->
        M15DischargeReason.TRANSFERRED_TO_ANOTHER_FOSTER
    FosterPlacementEndReason.TRANSFERRED_TO_ORGANIZATION ->
        M15DischargeReason.TRANSFERRED_TO_SHELTER
    FosterPlacementEndReason.HOSPITALIZED -> M15DischargeReason.VETERINARY_CARE
    FosterPlacementEndReason.CANCELLED_BEFORE_START -> M15DischargeReason.OTHER
    FosterPlacementEndReason.OTHER -> M15DischargeReason.OTHER
    FosterPlacementEndReason.UNKNOWN -> M15DischargeReason.OTHER
}

private fun FosterExpenseCategory.toM15ExpenseCategory(): M15ExpenseCategory = when (this) {
    FosterExpenseCategory.FOOD -> M15ExpenseCategory.FOOD
    FosterExpenseCategory.VETERINARY -> M15ExpenseCategory.VETERINARY
    FosterExpenseCategory.MEDICATION -> M15ExpenseCategory.MEDICATION
    FosterExpenseCategory.TRANSPORT -> M15ExpenseCategory.TRANSPORT
    FosterExpenseCategory.HYGIENE -> M15ExpenseCategory.HYGIENE
    FosterExpenseCategory.SUPPLIES -> M15ExpenseCategory.ACCESSORIES
    FosterExpenseCategory.OTHER -> M15ExpenseCategory.OTHER
    FosterExpenseCategory.UNKNOWN -> M15ExpenseCategory.OTHER
}

private fun FosterHelpType.toM15HelpType(): M15HelpRequestType = when (this) {
    FosterHelpType.FOOD -> M15HelpRequestType.FOOD
    FosterHelpType.VETERINARY -> M15HelpRequestType.VETERINARY
    FosterHelpType.TRANSPORT -> M15HelpRequestType.TRANSPORT
    FosterHelpType.SUPPLIES -> M15HelpRequestType.SUPPLIES
    FosterHelpType.VOLUNTEER -> M15HelpRequestType.TEMPORARY_REPLACEMENT
    FosterHelpType.MONEY -> M15HelpRequestType.OTHER
    FosterHelpType.MEDICATION -> M15HelpRequestType.SUPPLIES
    FosterHelpType.OTHER -> M15HelpRequestType.OTHER
    FosterHelpType.UNKNOWN -> M15HelpRequestType.OTHER
}

private fun FosterHelpStatus.toM15HelpStatus(): M15HelpRequestStatus = when (this) {
    FosterHelpStatus.OPEN -> M15HelpRequestStatus.OPEN
    FosterHelpStatus.PAUSED -> M15HelpRequestStatus.IN_PROGRESS
    FosterHelpStatus.FULFILLED -> M15HelpRequestStatus.RESOLVED
    FosterHelpStatus.CANCELLED -> M15HelpRequestStatus.CANCELLED
    FosterHelpStatus.UNKNOWN -> M15HelpRequestStatus.OPEN
}

private fun FosterUrgency.toM15Priority(): M15HelpPriority = when (this) {
    FosterUrgency.NORMAL -> M15HelpPriority.NORMAL
    FosterUrgency.HIGH -> M15HelpPriority.HIGH
    FosterUrgency.EMERGENCY -> M15HelpPriority.URGENT
    FosterUrgency.UNKNOWN -> M15HelpPriority.NORMAL
}

private fun formatEventTitle(type: M15EvolutionEventType, summary: String): String =
    "$EVENT_PREFIX${type.name}] ${summary.trim()}"

private fun parseEventTitle(title: String, fallbackDescription: String): Pair<M15EvolutionEventType, String> {
    if (title.startsWith(EVENT_PREFIX)) {
        val end = title.indexOf(']')
        if (end > EVENT_PREFIX.length) {
            val typeRaw = title.substring(EVENT_PREFIX.length, end)
            val eventType = runCatching {
                M15EvolutionEventType.valueOf(typeRaw)
            }.getOrDefault(M15EvolutionEventType.OTHER)
            val summary = title.substring(end + 1).trim().ifBlank { fallbackDescription }
            return eventType to summary
        }
    }
    return M15EvolutionEventType.GENERAL_UPDATE to title.ifBlank { fallbackDescription }
}

fun M15HelpPriority.toFosterUrgency(): FosterUrgency = when (this) {
    M15HelpPriority.NORMAL -> FosterUrgency.NORMAL
    M15HelpPriority.HIGH -> FosterUrgency.HIGH
    M15HelpPriority.URGENT -> FosterUrgency.EMERGENCY
}
