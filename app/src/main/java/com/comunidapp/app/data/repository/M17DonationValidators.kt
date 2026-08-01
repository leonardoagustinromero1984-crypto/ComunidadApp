package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M17CampaignGoal
import com.comunidapp.app.data.model.M17CampaignStatus
import com.comunidapp.app.data.model.M17ContributionStatus
import com.comunidapp.app.data.model.M17DonationCampaign
import com.comunidapp.app.data.model.RegisterM17MockContributionInput

object M17DonationValidators {
    private val allowedCurrencies = setOf("ARS", "USD", "EUR", "UYU", "BRL", "CLP")

    fun validateTitle(title: String): String? = when {
        title.trim().isEmpty() -> "M17_INVALID_TITLE"
        title.trim().length > 120 -> "M17_INVALID_TITLE"
        else -> null
    }

    fun validateDescription(description: String): String? = when {
        description.trim().length < 10 -> "M17_INVALID_DESCRIPTION"
        description.length > 5000 -> "M17_INVALID_DESCRIPTION"
        else -> null
    }

    fun validateGoal(goalAmountMinor: Long, currency: String): String? = when {
        goalAmountMinor <= 0 -> "M17_INVALID_GOAL"
        currency.uppercase() !in allowedCurrencies -> "M17_INVALID_CURRENCY"
        else -> null
    }

    fun validateDateRange(startsAt: Long, endsAt: Long?): String? =
        if (endsAt != null && endsAt <= startsAt) "M17_INVALID_DATE_RANGE" else null

    fun validateContributionAmount(amountMinor: Long): String? =
        if (amountMinor <= 0) "M17_INVALID_CONTRIBUTION_AMOUNT" else null

    fun validateDonorDisplayName(name: String?): String? =
        if (name != null && name.trim().length > 80) "M17_INVALID_REFERENCE" else null

    fun validateStateTransition(
        current: M17CampaignStatus,
        target: M17CampaignStatus
    ): String? {
        if (current == target) return null
        if (current.isTerminal) return "M17_STATE_ALREADY_FINAL"
        return when (target) {
            M17CampaignStatus.DRAFT -> null
            M17CampaignStatus.PUBLISHED ->
                if (current == M17CampaignStatus.DRAFT ||
                    current == M17CampaignStatus.PAUSED
                ) null else "M17_INVALID_STATE_TRANSITION"
            M17CampaignStatus.PAUSED ->
                if (current == M17CampaignStatus.PUBLISHED) null else "M17_INVALID_STATE_TRANSITION"
            M17CampaignStatus.COMPLETED, M17CampaignStatus.CANCELLED ->
                if (current == M17CampaignStatus.PUBLISHED ||
                    current == M17CampaignStatus.PAUSED
                ) null else "M17_INVALID_STATE_TRANSITION"
        }
    }

    fun validateCurrencyChange(
        campaign: M17DonationCampaign,
        newCurrency: String,
        hasConfirmedContributions: Boolean
    ): String? {
        if (hasConfirmedContributions &&
            newCurrency.uppercase() != campaign.goal.currency.uppercase()
        ) {
            return "M17_INVALID_CURRENCY"
        }
        return validateGoal(campaign.goal.amountMinor, newCurrency)
    }

    fun validateMockContribution(input: RegisterM17MockContributionInput): String? {
        validateContributionAmount(input.amountMinor)?.let { return it }
        if (input.visibility == com.comunidapp.app.data.model.M17DonorVisibility.PUBLIC &&
            input.donorDisplayName.isNullOrBlank()
        ) {
            return "M17_INVALID_REFERENCE"
        }
        validateDonorDisplayName(input.donorDisplayName)?.let { return it }
        return null
    }

    fun countsTowardConfirmed(status: M17ContributionStatus): Boolean =
        status == M17ContributionStatus.CONFIRMED

    fun formatMoneyMinor(amountMinor: Long, currency: String): String {
        val major = amountMinor / 100
        val minor = (amountMinor % 100).toInt()
        return "$currency ${major}.${minor.toString().padStart(2, '0')}"
    }
}
