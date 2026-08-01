package com.comunidapp.app.domain.m17

import com.comunidapp.app.data.model.M17CampaignGoal
import com.comunidapp.app.data.model.M17CampaignStatus
import com.comunidapp.app.data.model.M17Contribution
import com.comunidapp.app.data.model.M17ContributionStatus
import com.comunidapp.app.data.model.M17DonorVisibility
import com.comunidapp.app.data.model.M17FinancialCalculator
import com.comunidapp.app.data.model.M17PrivacySanitizer
import com.comunidapp.app.data.repository.M17DonationValidators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M17DonationFoundationTest {

    @Test
    fun onlyConfirmedCountsTowardTotal() {
        val goal = M17CampaignGoal(100_00, "ARS")
        val contributions = listOf(
            contrib(50_00, M17ContributionStatus.CONFIRMED),
            contrib(30_00, M17ContributionStatus.PENDING),
            contrib(20_00, M17ContributionStatus.FAILED),
            contrib(10_00, M17ContributionStatus.REFUNDED)
        )
        val summary = M17FinancialCalculator.summarize(goal, contributions)
        assertEquals(50_00, summary.confirmedAmountMinor)
        assertEquals(1, summary.confirmedContributionCount)
    }

    @Test
    fun invalidGoalRejected() {
        assertEquals("M17_INVALID_GOAL", M17DonationValidators.validateGoal(0, "ARS"))
        assertEquals("M17_INVALID_GOAL", M17DonationValidators.validateGoal(-1, "ARS"))
    }

    @Test
    fun negativeContributionRejected() {
        assertEquals("M17_INVALID_CONTRIBUTION_AMOUNT", M17DonationValidators.validateContributionAmount(-100))
    }

    @Test
    fun terminalCampaignCannotReopen() {
        assertEquals(
            "M17_STATE_ALREADY_FINAL",
            M17DonationValidators.validateStateTransition(M17CampaignStatus.COMPLETED, M17CampaignStatus.PUBLISHED)
        )
    }

    @Test
    fun privateContributionHiddenFromPublic() {
        val hidden = M17PrivacySanitizer.toPublicContribution(
            contrib(10_00, M17ContributionStatus.CONFIRMED, M17DonorVisibility.PRIVATE)
        )
        assertNull(hidden)
    }

    @Test
    fun anonymousContributionUsesGenericLabel() {
        val public = M17PrivacySanitizer.toPublicContribution(
            contrib(10_00, M17ContributionStatus.CONFIRMED, M17DonorVisibility.ANONYMOUS)
        )
        assertTrue(public?.donorLabel?.contains("anónimo", ignoreCase = true) == true)
    }

    private fun contrib(
        amount: Long,
        status: M17ContributionStatus,
        visibility: M17DonorVisibility = M17DonorVisibility.PUBLIC
    ) = M17Contribution(
        id = "c1",
        campaignId = "camp1",
        amountMinor = amount,
        currency = "ARS",
        status = status,
        visibility = visibility,
        donorDisplayName = if (visibility == M17DonorVisibility.PUBLIC) "Ana" else null,
        createdAt = 0L
    )
}
