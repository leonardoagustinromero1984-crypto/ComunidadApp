package com.comunidapp.app.domain.notifications

/**
 * Estado de un intento de delivery (push/email/local).
 * DELIVERED en push no implica READ en bandeja.
 */
enum class NotificationDeliveryState {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
    SKIPPED_PREFERENCE,
    SKIPPED_PERMISSION,
    SKIPPED_EXPIRED,
    SKIPPED_QUIET_HOURS,
    CANCELLED,
    DEAD_LETTER;

    companion object {
        fun fromString(raw: String?): NotificationDeliveryState? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

object NotificationDeliveryStateTransitions {

    private val terminal: Set<NotificationDeliveryState> = setOf(
        NotificationDeliveryState.DELIVERED,
        NotificationDeliveryState.FAILED_PERMANENT,
        NotificationDeliveryState.SKIPPED_PREFERENCE,
        NotificationDeliveryState.SKIPPED_PERMISSION,
        NotificationDeliveryState.SKIPPED_EXPIRED,
        NotificationDeliveryState.SKIPPED_QUIET_HOURS,
        NotificationDeliveryState.CANCELLED,
        NotificationDeliveryState.DEAD_LETTER
    )

    private val allowed: Map<NotificationDeliveryState, Set<NotificationDeliveryState>> = mapOf(
        NotificationDeliveryState.PENDING to setOf(
            NotificationDeliveryState.PROCESSING,
            NotificationDeliveryState.SKIPPED_PREFERENCE,
            NotificationDeliveryState.SKIPPED_PERMISSION,
            NotificationDeliveryState.SKIPPED_EXPIRED,
            NotificationDeliveryState.SKIPPED_QUIET_HOURS,
            NotificationDeliveryState.CANCELLED
        ),
        NotificationDeliveryState.PROCESSING to setOf(
            NotificationDeliveryState.DELIVERED,
            NotificationDeliveryState.FAILED_RETRYABLE,
            NotificationDeliveryState.FAILED_PERMANENT,
            NotificationDeliveryState.SKIPPED_EXPIRED,
            NotificationDeliveryState.CANCELLED,
            NotificationDeliveryState.DEAD_LETTER
        ),
        NotificationDeliveryState.FAILED_RETRYABLE to setOf(
            NotificationDeliveryState.PENDING,
            NotificationDeliveryState.PROCESSING,
            NotificationDeliveryState.DEAD_LETTER,
            NotificationDeliveryState.CANCELLED,
            NotificationDeliveryState.FAILED_PERMANENT
        ),
        NotificationDeliveryState.DELIVERED to emptySet(),
        NotificationDeliveryState.FAILED_PERMANENT to emptySet(),
        NotificationDeliveryState.SKIPPED_PREFERENCE to emptySet(),
        NotificationDeliveryState.SKIPPED_PERMISSION to emptySet(),
        NotificationDeliveryState.SKIPPED_EXPIRED to emptySet(),
        NotificationDeliveryState.SKIPPED_QUIET_HOURS to emptySet(),
        NotificationDeliveryState.CANCELLED to emptySet(),
        NotificationDeliveryState.DEAD_LETTER to emptySet()
    )

    fun isTerminal(state: NotificationDeliveryState): Boolean = state in terminal

    fun canTransition(from: NotificationDeliveryState, to: NotificationDeliveryState): Boolean {
        if (from == to) return true
        return to in (allowed[from] ?: emptySet())
    }

    fun validateTransition(from: NotificationDeliveryState, to: NotificationDeliveryState): Result<Unit> =
        if (canTransition(from, to)) Result.success(Unit)
        else Result.failure(IllegalStateException("INVALID_DELIVERY_TRANSITION:$from->$to"))

    /** Push entregado nunca marca el recipient como READ. */
    fun deliveredPushMarksRecipientRead(): Boolean = false
}
