package com.comunidapp.shared.domain.lostfound

/**
 * Dominio canónico lost/found para KMP.
 * Los data models Android (`LostFoundPost`) siguen en app hasta cerrar WIP M09/decoding;
 * estas reglas son la fuente compartida de transiciones.
 */
enum class LostFoundCaseType {
    LOST,
    FOUND
}

enum class LostFoundCaseStatus {
    ACTIVE,
    RESOLVED,
    CLOSED
}

object LostFoundStatusRules {
    fun canResolve(from: LostFoundCaseStatus): Boolean =
        from == LostFoundCaseStatus.ACTIVE

    fun canClose(from: LostFoundCaseStatus): Boolean =
        from == LostFoundCaseStatus.ACTIVE || from == LostFoundCaseStatus.RESOLVED

    fun canReopen(from: LostFoundCaseStatus): Boolean =
        from == LostFoundCaseStatus.RESOLVED || from == LostFoundCaseStatus.CLOSED

    fun transition(from: LostFoundCaseStatus, to: LostFoundCaseStatus): Result<LostFoundCaseStatus> {
        val allowed = when (to) {
            LostFoundCaseStatus.ACTIVE -> canReopen(from) || from == LostFoundCaseStatus.ACTIVE
            LostFoundCaseStatus.RESOLVED -> canResolve(from)
            LostFoundCaseStatus.CLOSED -> canClose(from)
        }
        return if (allowed || from == to) Result.success(to)
        else Result.failure(IllegalArgumentException("LOST_FOUND_INVALID_TRANSITION"))
    }
}
