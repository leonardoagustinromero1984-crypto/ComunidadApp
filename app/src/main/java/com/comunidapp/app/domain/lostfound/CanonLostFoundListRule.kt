package com.comunidapp.app.domain.lostfound

import com.comunidapp.app.data.model.LostFoundStatus

/**
 * Same inclusion as canon_list_lost_found: OPEN/CLAIMED/IN_CARE, newest first,
 * with no cutoff against the viewer's account creation and no cursor.
 */
object PublicLostFoundFeed {
    fun include(
        status: LostFoundStatus,
        createdAtEpochMs: Long?,
        viewerAccountCreatedAtEpochMs: Long = 0L
    ): Boolean {
        val raw = when (status) {
            LostFoundStatus.ACTIVE -> "OPEN"
            else -> status.name
        }
        return CanonLostFoundListRule.include(
            status = raw,
            caseCreatedAtEpochMs = createdAtEpochMs ?: 0L,
            viewerAccountCreatedAtEpochMs = viewerAccountCreatedAtEpochMs
        )
    }
}

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
