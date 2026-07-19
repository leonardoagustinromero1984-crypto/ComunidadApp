package com.comunidapp.app.domain.notifications

/**
 * Estado del ítem de bandeja (recipient / inbox).
 * Entrega push no marca READ.
 */
enum class NotificationState {
    UNREAD,
    READ,
    ARCHIVED,
    DELETED,
    EXPIRED;

    companion object {
        fun fromString(raw: String?): NotificationState? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

object NotificationStateTransitions {

    private val allowed: Map<NotificationState, Set<NotificationState>> = mapOf(
        NotificationState.UNREAD to setOf(
            NotificationState.READ,
            NotificationState.ARCHIVED,
            NotificationState.DELETED,
            NotificationState.EXPIRED
        ),
        NotificationState.READ to setOf(
            NotificationState.UNREAD,
            NotificationState.ARCHIVED,
            NotificationState.DELETED,
            NotificationState.EXPIRED
        ),
        NotificationState.ARCHIVED to setOf(
            NotificationState.READ,
            NotificationState.UNREAD,
            NotificationState.DELETED,
            NotificationState.EXPIRED
        ),
        NotificationState.DELETED to emptySet(),
        NotificationState.EXPIRED to setOf(NotificationState.DELETED)
    )

    fun canTransition(from: NotificationState, to: NotificationState): Boolean {
        if (from == to) return true
        return to in (allowed[from] ?: emptySet())
    }

    fun validateTransition(from: NotificationState, to: NotificationState): Result<Unit> =
        if (canTransition(from, to)) Result.success(Unit)
        else Result.failure(IllegalStateException("INVALID_STATE_TRANSITION:$from->$to"))
}
