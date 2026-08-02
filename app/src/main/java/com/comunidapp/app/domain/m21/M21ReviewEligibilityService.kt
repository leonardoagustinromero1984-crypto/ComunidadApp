package com.comunidapp.app.domain.m21

import com.comunidapp.app.data.model.M21Review
import com.comunidapp.app.data.model.M21ReviewContextReference
import com.comunidapp.app.data.model.M21ReviewEligibility
import com.comunidapp.app.data.model.M21ReviewEligibilityReason
import com.comunidapp.app.data.model.M21ReviewStatus
import com.comunidapp.app.data.model.M21ReviewSubjectReference
import com.comunidapp.app.data.repository.M21EligibilityAdapter
import com.comunidapp.app.data.repository.M21EligibilityRecord

object M21ReviewEligibilityService {

    fun evaluate(
        reviewerUserId: String,
        subject: M21ReviewSubjectReference,
        contextReference: M21ReviewContextReference?,
        existingReviews: List<M21Review>,
        additionalEligibilityRecords: List<M21EligibilityRecord> = emptyList(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): M21ReviewEligibility {
        if (isSelfReview(reviewerUserId, subject)) {
            return ineligible(subject, contextReference, M21ReviewEligibilityReason.SELF_REVIEW)
        }

        val contextId = contextReference?.contextId
        if (contextId != null) {
            val duplicate = existingReviews.any {
                it.reviewerUserId == reviewerUserId &&
                    it.contextReference?.contextId == contextId &&
                    it.status !in terminalReviewStatuses
            }
            if (duplicate) {
                return ineligible(subject, contextReference, M21ReviewEligibilityReason.ALREADY_REVIEWED)
            }
        }

        val record = M21EligibilityAdapter.findCompletedInteraction(
            reviewerUserId = reviewerUserId,
            subject = subject,
            contextId = contextId,
            additionalRecords = additionalEligibilityRecords
        )

        if (record == null) {
            return if (contextReference == null) {
                ineligible(subject, null, M21ReviewEligibilityReason.ELIGIBILITY_UNAVAILABLE)
            } else {
                ineligible(subject, contextReference, M21ReviewEligibilityReason.NOT_ELIGIBLE)
            }
        }

        if (record.cancelled) {
            return ineligible(subject, contextReference, M21ReviewEligibilityReason.CONTEXT_CANCELLED)
        }

        if (record.rejected) {
            return ineligible(subject, contextReference, M21ReviewEligibilityReason.CONTEXT_REJECTED)
        }

        record.expiresAt?.let { expiresAt ->
            if (nowEpochMs > expiresAt) {
                return ineligible(subject, contextReference, M21ReviewEligibilityReason.EXPIRED)
            }
        }

        return M21ReviewEligibility(
            eligible = true,
            reason = M21ReviewEligibilityReason.COMPLETED_INTERACTION,
            subject = subject,
            contextReference = contextReference ?: record.context
        )
    }

    fun isSelfReview(reviewerUserId: String, subject: M21ReviewSubjectReference): Boolean =
        subject.targetType == com.comunidapp.app.data.model.M21ReviewTargetType.USER &&
            subject.targetId == reviewerUserId

    private fun ineligible(
        subject: M21ReviewSubjectReference,
        contextReference: M21ReviewContextReference?,
        reason: M21ReviewEligibilityReason
    ) = M21ReviewEligibility(
        eligible = false,
        reason = reason,
        subject = subject,
        contextReference = contextReference
    )

    private val terminalReviewStatuses = setOf(
        M21ReviewStatus.REMOVED,
        M21ReviewStatus.REMOVED_BY_MODERATION,
        M21ReviewStatus.ARCHIVED
    )
}
