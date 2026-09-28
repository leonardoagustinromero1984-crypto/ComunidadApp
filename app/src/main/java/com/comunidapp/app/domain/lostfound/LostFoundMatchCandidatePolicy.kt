package com.comunidapp.app.domain.lostfound

import com.comunidapp.app.data.repository.LostFoundMatchCandidate

/**
 * Visibility of FOUND↔LOST candidates is separate from who may confirm or reject.
 *
 * Automatic rows from `_canon_match_found_to_lost` keep `asserted_by` null.
 * That null is not replaced with the viewer. Confirm and reject call
 * `canon_confirm_found_owner_match` and `canon_reject_found_owner_match`, which
 * allow only the current custodian and do not require a person assertion.
 * Owner assertion stays on `canon_assert_found_might_be_mine`.
 */
object LostFoundMatchCandidatePolicy {

    data class Presented(
        val candidate: LostFoundMatchCandidate,
        val automatic: Boolean,
        val headline: String,
        val showCustodianActions: Boolean
    )

    fun canLoad(
        isFound: Boolean,
        viewerId: String?,
        authorId: String,
        claimedBy: String?,
        isCustodian: Boolean
    ): Boolean {
        if (!isFound || viewerId.isNullOrBlank()) return false
        return isCustodian || authorId == viewerId || claimedBy == viewerId
    }

    fun present(
        candidates: List<LostFoundMatchCandidate>,
        viewerIsCustodian: Boolean
    ): List<Presented> = candidates
        .filter { it.status.equals("PENDING", ignoreCase = true) }
        .map { candidate ->
            val automatic = candidate.assertedBy.isNullOrBlank()
            Presented(
                candidate = candidate,
                automatic = automatic,
                headline = if (automatic) {
                    "Coincidencia automática"
                } else {
                    "Una persona indicó que podría ser su mascota"
                },
                showCustodianActions = viewerIsCustodian
            )
        }
}
