package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M17InKindNeedStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityStatus

object M17ExtendedValidators {
    fun validateQuantity(quantity: Int): String? =
        if (quantity <= 0) "M17_INVALID_QUANTITY" else null

    fun validateInKindTransition(current: M17InKindNeedStatus, target: M17InKindNeedStatus): String? {
        if (current == target) return null
        if (current.isTerminal) return "M17_NEED_TERMINAL"
        return when (target) {
            M17InKindNeedStatus.DRAFT -> null
            M17InKindNeedStatus.PUBLISHED -> if (current == M17InKindNeedStatus.DRAFT) null else "M17_INVALID_STATE"
            M17InKindNeedStatus.FULFILLED, M17InKindNeedStatus.CANCELLED ->
                if (current == M17InKindNeedStatus.PUBLISHED) null else "M17_INVALID_STATE"
        }
    }

    fun validateVolunteerTransition(
        current: M17VolunteerOpportunityStatus,
        target: M17VolunteerOpportunityStatus
    ): String? {
        if (current == target) return null
        if (current.isTerminal) return "M17_OPPORTUNITY_TERMINAL"
        return when (target) {
            M17VolunteerOpportunityStatus.DRAFT -> null
            M17VolunteerOpportunityStatus.PUBLISHED ->
                if (current == M17VolunteerOpportunityStatus.DRAFT ||
                    current == M17VolunteerOpportunityStatus.PAUSED
                ) null else "M17_INVALID_STATE"
            M17VolunteerOpportunityStatus.PAUSED ->
                if (current == M17VolunteerOpportunityStatus.PUBLISHED) null else "M17_INVALID_STATE"
            M17VolunteerOpportunityStatus.FILLED,
            M17VolunteerOpportunityStatus.COMPLETED,
            M17VolunteerOpportunityStatus.CANCELLED ->
                if (current == M17VolunteerOpportunityStatus.PUBLISHED ||
                    current == M17VolunteerOpportunityStatus.PAUSED
                ) null else "M17_INVALID_STATE"
        }
    }

    fun validateTransparencyAmount(amountMinor: Long): String? =
        if (amountMinor < 0) "M17_INVALID_AMOUNT" else null
}
