package com.comunidapp.app.domain.m17

import com.comunidapp.app.data.model.M17Contribution
import com.comunidapp.app.data.model.M17ContributionStatus

/**
 * Confirm/reject is allowed only for an authorized organization member,
 * on a contribution they did not declare, while it is still pending.
 * createdBy of the campaign is not authority.
 */
object M17ContributionModeration {
    fun canConfirmOrReject(
        canManageOrganization: Boolean,
        contribution: M17Contribution,
        sessionUserId: String?
    ): Boolean {
        if (!canManageOrganization) return false
        if (contribution.status != M17ContributionStatus.PENDING) return false
        if (contribution.declaredByViewer) return false
        val contributor = contribution.contributorUserId
        if (!sessionUserId.isNullOrBlank() && contributor != null && contributor == sessionUserId) {
            return false
        }
        return true
    }

    fun statusLabel(status: M17ContributionStatus): String =
        CommunityHelpPresentation.contributionStatus(status)
}
