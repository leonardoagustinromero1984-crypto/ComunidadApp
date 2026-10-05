package com.comunidapp.app.domain.lostfound

/**
 * Same inclusion as canon_list_lost_found: OPEN/CLAIMED/IN_CARE, newest first,
 * with no cutoff against the viewer's account creation and no cursor.
 */
object CanonLostFoundListRule {
    const val usesCursor = false

    private val visible = setOf("OPEN", "ACTIVE", "CLAIMED", "IN_CARE")

    fun include(
        status: String,
        caseCreatedAtEpochMs: Long,
        viewerAccountCreatedAtEpochMs: Long
    ): Boolean {
        val olderThanAccount = caseCreatedAtEpochMs < viewerAccountCreatedAtEpochMs
        val newerOrSame = caseCreatedAtEpochMs >= viewerAccountCreatedAtEpochMs
        return (olderThanAccount || newerOrSame) && status.trim().uppercase() in visible
    }
}
