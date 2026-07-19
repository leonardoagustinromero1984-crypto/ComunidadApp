package com.comunidapp.app.domain.support

enum class SupportCategory {
    ACCOUNT_ACCESS,
    PROFILE,
    ORGANIZATION,
    TECHNICAL,
    PRIVACY,
    SAFETY,
    CONTENT,
    OTHER
}

enum class SupportPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

enum class SupportTicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_USER,
    WAITING_INTERNAL,
    RESOLVED,
    CLOSED
}

enum class SupportMessageVisibility {
    REQUESTER_VISIBLE,
    INTERNAL
}

data class SupportTicket(
    val id: String,
    val requesterUserId: String,
    val category: SupportCategory,
    val subject: String,
    val description: String,
    val priority: SupportPriority = SupportPriority.NORMAL,
    val status: SupportTicketStatus = SupportTicketStatus.OPEN,
    val assignedToUserId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val resolvedAtEpochMs: Long? = null,
    val closedAtEpochMs: Long? = null,
    val closeReasonCode: String? = null,
    /** Vínculo explícito opcional a caso de moderación. */
    val linkedModerationCaseId: String? = null
)

data class SupportMessage(
    val id: String,
    val ticketId: String,
    val authorUserId: String,
    val visibility: SupportMessageVisibility,
    val body: String,
    val createdAtEpochMs: Long,
    val evidenceRefId: String? = null
)

object SupportValidators {

    const val SUBJECT_MIN = 3
    const val SUBJECT_MAX = 160
    const val DESCRIPTION_MAX = 4000
    const val MESSAGE_MAX = 4000

    fun isSensitiveCategory(category: SupportCategory): Boolean =
        category == SupportCategory.PRIVACY || category == SupportCategory.SAFETY

    fun validateCreate(
        requesterUserId: String,
        category: SupportCategory,
        subject: String,
        description: String,
        nowEpochMs: Long
    ): Result<SupportTicket> {
        if (requesterUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("REQUESTER_REQUIRED"))
        }
        val sub = subject.trim()
        if (sub.length < SUBJECT_MIN || sub.length > SUBJECT_MAX) {
            return Result.failure(IllegalArgumentException("SUBJECT_INVALID"))
        }
        val desc = description.trim()
        if (desc.isEmpty() || desc.length > DESCRIPTION_MAX) {
            return Result.failure(IllegalArgumentException("DESCRIPTION_INVALID"))
        }
        return Result.success(
            SupportTicket(
                id = "",
                requesterUserId = requesterUserId,
                category = category,
                subject = sub,
                description = desc,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        )
    }

    fun canClose(
        ticket: SupportTicket,
        closeReasonCode: String?
    ): Result<Unit> {
        if (ticket.status == SupportTicketStatus.CLOSED) {
            return Result.failure(IllegalArgumentException("ALREADY_CLOSED"))
        }
        val resolved = ticket.status == SupportTicketStatus.RESOLVED
        if (!resolved && closeReasonCode.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("CLOSE_REASON_REQUIRED"))
        }
        return Result.success(Unit)
    }

    fun canReopen(ticket: SupportTicket): Boolean =
        ticket.status == SupportTicketStatus.RESOLVED

    fun validateMessage(
        body: String,
        visibility: SupportMessageVisibility
    ): Result<String> {
        val text = body.trim()
        if (text.isEmpty() || text.length > MESSAGE_MAX) {
            return Result.failure(IllegalArgumentException("MESSAGE_INVALID"))
        }
        return Result.success(text)
    }

    /** Mensaje INTERNAL nunca se expone al solicitante. */
    fun isVisibleToRequester(visibility: SupportMessageVisibility): Boolean =
        visibility == SupportMessageVisibility.REQUESTER_VISIBLE

    /** Soporte asincrónico: sin chat realtime en este contrato. */
    fun usesRealtimeChat(): Boolean = false
}
