package com.comunidapp.app.domain.verification

/**
 * LeoVer verification is server-side (`leover_verification_requests`).
 * Badges are visible: VERIFIED → "Verificado por LeoVer", otherwise "Aún no verificado".
 */
object VerificationDisplayPolicy {
    const val SOURCE_OF_TRUTH = "leover_verification_requests"
    const val SELF_DECLARED_ALLOWED = false
    const val FILTERS_VISIBLE = true
    const val BADGE_VISIBLE = true
    const val HELP_COPY = "Verificado por LeoVer"
    const val UNVERIFIED_COPY = "Aún no verificado"
    const val NOT_REQUESTED_COPY = "Aún no solicitaste la verificación"
    const val PENDING_COPY = "Verificación pendiente"
    const val REQUIRES_CORRECTION_COPY = "Necesitamos que corrijas información"
    const val REJECTED_COPY = "Solicitud de verificación rechazada"
    const val SUSPENDED_COPY = "Verificación suspendida"

    fun badgeLabel(verified: Boolean): String =
        if (verified) HELP_COPY else UNVERIFIED_COPY

    fun statusLabel(raw: String?): String = when (raw?.trim()?.uppercase()) {
        "VERIFIED" -> HELP_COPY
        "PENDING" -> PENDING_COPY
        "REQUIRES_CORRECTION" -> REQUIRES_CORRECTION_COPY
        "REJECTED" -> REJECTED_COPY
        "SUSPENDED" -> SUSPENDED_COPY
        "NOT_REQUESTED", "", null -> NOT_REQUESTED_COPY
        else -> UNVERIFIED_COPY
    }
}
