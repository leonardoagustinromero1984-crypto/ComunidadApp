package com.comunidapp.app.domain.verification

/**
 * LeoVer verification is server-side (`actor_verifications` / admin RPCs).
 * There is no user-facing operational verification flow yet, so badges/filters
 * that imply "Verificado por LeoVer" stay hidden to avoid self-declaration UX.
 */
object VerificationDisplayPolicy {
    const val SOURCE_OF_TRUTH = "actor_verifications"
    const val SELF_DECLARED_ALLOWED = false
    const val FILTERS_VISIBLE = false
    const val BADGE_VISIBLE = false
    const val HELP_COPY = "Verificado por LeoVer"
}
