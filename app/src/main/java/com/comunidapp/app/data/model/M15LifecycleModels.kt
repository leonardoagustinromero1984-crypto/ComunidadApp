package com.comunidapp.app.data.model

/**
 * LeoVer M15 Bloque 3 — evolución, egreso, gastos y ayuda (capa producto sobre M10).
 */

enum class M15EvolutionEventType {
    GENERAL_UPDATE,
    ADAPTATION,
    BEHAVIOR,
    FEEDING,
    HEALTH_ALERT,
    VISIT,
    INCIDENT,
    OTHER
}

enum class M15DischargeReason {
    RETURNED_TO_RESPONSIBLE,
    ADOPTED,
    TRANSFERRED_TO_ANOTHER_FOSTER,
    TRANSFERRED_TO_SHELTER,
    VETERINARY_CARE,
    INCOMPATIBILITY,
    EMERGENCY,
    OTHER
}

enum class M15DischargeOutcome {
    COMPLETED,
    INTERRUPTED,
    CANCELLED
}

enum class M15ExpenseCategory {
    FOOD,
    VETERINARY,
    MEDICATION,
    TRANSPORT,
    HYGIENE,
    ACCESSORIES,
    OTHER
}

enum class M15ExpenseStatus {
    RECORDED,
    SUBMITTED_FOR_REVIEW,
    APPROVED,
    REJECTED,
    CANCELLED
}

enum class M15HelpRequestType {
    FOOD,
    VETERINARY,
    TRANSPORT,
    SUPPLIES,
    TEMPORARY_REPLACEMENT,
    EMERGENCY,
    OTHER
}

enum class M15HelpRequestStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CANCELLED,
    EXPIRED
}

enum class M15HelpPriority {
    NORMAL,
    HIGH,
    URGENT
}

data class M15PlacementEvolution(
    val id: String,
    val placementId: String,
    val eventType: M15EvolutionEventType,
    val summary: String,
    val privateNote: String? = null,
    val healthAlert: Boolean = false,
    val mediaRefs: List<String> = emptyList(),
    val createdBy: String,
    val createdAt: Long,
    val occurredAt: Long
)

data class M15PlacementExpense(
    val id: String,
    val placementId: String,
    val category: M15ExpenseCategory,
    val amountMinor: Long,
    val currency: String,
    val occurredAt: Long,
    val description: String,
    val receiptMediaRef: String? = null,
    val status: M15ExpenseStatus = M15ExpenseStatus.RECORDED,
    val createdBy: String,
    val createdAt: Long
)

data class M15PlacementHelpRequest(
    val id: String,
    val placementId: String,
    val type: M15HelpRequestType,
    val title: String,
    val description: String,
    val priority: M15HelpPriority = M15HelpPriority.NORMAL,
    val status: M15HelpRequestStatus = M15HelpRequestStatus.OPEN,
    val createdBy: String,
    val createdAt: Long,
    val resolvedAt: Long? = null
)

data class M15DischargeInput(
    val placementId: String,
    val reason: M15DischargeReason,
    val outcome: M15DischargeOutcome,
    val privateNote: String? = null
)

data class AddM15EvolutionInput(
    val placementId: String,
    val eventType: M15EvolutionEventType,
    val summary: String,
    val privateNote: String? = null,
    val healthAlert: Boolean = false,
    val mediaRefs: List<String> = emptyList(),
    val occurredAt: Long = System.currentTimeMillis()
)

data class AddM15ExpenseInput(
    val placementId: String,
    val category: M15ExpenseCategory,
    val amountMinor: Long,
    val currency: String,
    val occurredAt: Long,
    val description: String,
    val receiptMediaRef: String? = null
)

data class AddM15HelpRequestInput(
    val placementId: String,
    val type: M15HelpRequestType,
    val title: String,
    val description: String,
    val priority: M15HelpPriority = M15HelpPriority.NORMAL
)
