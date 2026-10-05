package com.comunidapp.app.domain.lostfound

/** An active public case stays visible even when it predates the viewer's account. */
object PublicAlertVisibility {
    fun visible(
        active: Boolean,
        caseCreatedAtEpochMs: Long?,
        viewerAccountCreatedAtEpochMs: Long?
    ): Boolean {
        val ignoredCase = caseCreatedAtEpochMs
        val ignoredViewer = viewerAccountCreatedAtEpochMs
        return active
    }
}
