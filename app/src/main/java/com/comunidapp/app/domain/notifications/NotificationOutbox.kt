package com.comunidapp.app.domain.notifications

import java.time.Instant

enum class NotificationOutboxState {
    PENDING,
    CLAIMED,
    PROCESSED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
    DEAD,
    CANCELLED;

    companion object {
        fun fromString(raw: String?): NotificationOutboxState? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

data class NotificationOutboxEvent(
    val outboxId: String,
    val event: NotificationEvent,
    val state: NotificationOutboxState,
    val idempotencyKey: String,
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val claimedAt: Instant? = null
)

object NotificationOutboxRules {

    private val allowed: Map<NotificationOutboxState, Set<NotificationOutboxState>> = mapOf(
        NotificationOutboxState.PENDING to setOf(
            NotificationOutboxState.CLAIMED,
            NotificationOutboxState.CANCELLED
        ),
        NotificationOutboxState.CLAIMED to setOf(
            NotificationOutboxState.PROCESSED,
            NotificationOutboxState.FAILED_RETRYABLE,
            NotificationOutboxState.FAILED_PERMANENT,
            NotificationOutboxState.DEAD,
            NotificationOutboxState.CANCELLED
        ),
        NotificationOutboxState.FAILED_RETRYABLE to setOf(
            NotificationOutboxState.PENDING,
            NotificationOutboxState.CLAIMED,
            NotificationOutboxState.DEAD,
            NotificationOutboxState.CANCELLED
        ),
        NotificationOutboxState.PROCESSED to emptySet(),
        NotificationOutboxState.FAILED_PERMANENT to emptySet(),
        NotificationOutboxState.DEAD to emptySet(),
        NotificationOutboxState.CANCELLED to emptySet()
    )

    fun canTransition(from: NotificationOutboxState, to: NotificationOutboxState): Boolean {
        if (from == to) return true
        return to in (allowed[from] ?: emptySet())
    }

    fun validateTransition(from: NotificationOutboxState, to: NotificationOutboxState): Result<Unit> =
        if (canTransition(from, to)) Result.success(Unit)
        else Result.failure(IllegalStateException("INVALID_OUTBOX_TRANSITION:$from->$to"))

    fun validateEnqueue(event: NotificationEvent, idempotencyKey: String): Result<Unit> {
        if (idempotencyKey.isBlank()) {
            return Result.failure(IllegalArgumentException("IDEMPOTENCY_KEY_MANDATORY"))
        }
        if (idempotencyKey != event.idempotencyKey) {
            return Result.failure(IllegalArgumentException("IDEMPOTENCY_KEY_MISMATCH"))
        }
        return NotificationEventRules.validate(event).map { }
    }
}
