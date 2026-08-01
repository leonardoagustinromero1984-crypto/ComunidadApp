package com.comunidapp.app.data.model

import com.comunidapp.app.domain.organization.OrganizationType

/** LeoVer M17 — Donaciones y campañas solidarias (Bloque 1 local/mock). */

enum class M17CampaignStatus {
    DRAFT,
    PUBLISHED,
    PAUSED,
    COMPLETED,
    CANCELLED;

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED
    val isPublic: Boolean get() = this == PUBLISHED || this == PAUSED || this == COMPLETED
}

enum class M17CampaignType {
    MEDICAL,
    FOOD_AND_SUPPLIES,
    RESCUE,
    SHELTER_INFRASTRUCTURE,
    TRANSPORT,
    EMERGENCY,
    GENERAL_SUPPORT
}

enum class M17ContributionStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED,
    REFUNDED
}

enum class M17DonorVisibility {
    PUBLIC,
    ANONYMOUS,
    PRIVATE
}

data class M17CampaignGoal(
    val amountMinor: Long,
    val currency: String
)

data class M17CampaignFinancialSummary(
    val confirmedAmountMinor: Long,
    val currency: String,
    val goalAmountMinor: Long,
    val confirmedContributionCount: Int,
    val pendingContributionCount: Int,
    val progressPercent: Int
)

data class M17CampaignReference(
    val petId: String? = null,
    val petPublicName: String? = null,
    val shelterProfileId: String? = null,
    val shelterPublicName: String? = null,
    val needDescription: String? = null,
    val publicLocationText: String? = null
)

data class M17CampaignUpdate(
    val id: String,
    val message: String,
    val createdAt: Long
)

data class M17DonationCampaign(
    val id: String,
    val organizationId: String,
    val organizationDisplayName: String,
    val title: String,
    val description: String,
    val campaignType: M17CampaignType,
    val status: M17CampaignStatus,
    val goal: M17CampaignGoal,
    val reference: M17CampaignReference = M17CampaignReference(),
    val coverImageRef: String? = null,
    val galleryImageRefs: List<String> = emptyList(),
    val publicUpdates: List<M17CampaignUpdate> = emptyList(),
    val internalNotes: String? = null,
    val moderationStatus: String? = null,
    val startsAt: Long,
    val endsAt: Long? = null,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublicCampaign(summary: M17CampaignFinancialSummary): M17PublicCampaign =
        M17PrivacySanitizer.toPublicCampaign(this, summary)
}

data class M17PublicCampaign(
    val id: String,
    val title: String,
    val description: String,
    val organizationDisplayName: String,
    val campaignType: M17CampaignType,
    val status: M17CampaignStatus,
    val goalAmountMinor: Long,
    val currency: String,
    val confirmedAmountMinor: Long,
    val progressPercent: Int,
    val reference: M17CampaignReference,
    val coverImageRef: String? = null,
    val publicUpdates: List<M17CampaignUpdate> = emptyList(),
    val startsAt: Long,
    val endsAt: Long? = null,
    val confirmedContributionCount: Int = 0
)

data class M17CampaignSummary(
    val id: String,
    val title: String,
    val organizationDisplayName: String,
    val campaignType: M17CampaignType,
    val status: M17CampaignStatus,
    val goalAmountMinor: Long,
    val currency: String,
    val confirmedAmountMinor: Long,
    val progressPercent: Int,
    val coverImageRef: String? = null,
    val publicLocationText: String? = null,
    val petPublicName: String? = null,
    val shelterPublicName: String? = null
)

data class M17Contribution(
    val id: String,
    val campaignId: String,
    val amountMinor: Long,
    val currency: String,
    val status: M17ContributionStatus,
    val visibility: M17DonorVisibility,
    val donorDisplayName: String? = null,
    val message: String? = null,
    val providerReference: String? = null,
    val createdAt: Long
)

data class M17PublicContribution(
    val id: String,
    val amountMinor: Long,
    val currency: String,
    val donorLabel: String,
    val message: String? = null,
    val createdAt: Long
)

data class M17CampaignSearchFilter(
    val query: String = "",
    val type: M17CampaignType? = null,
    val organizationId: String? = null,
    val shelterProfileId: String? = null,
    val withPetOnly: Boolean = false,
    val activeOnly: Boolean = true,
    val completedOnly: Boolean = false,
    val nearGoalOnly: Boolean = false
)

object M17PermissionCodes {
    const val DONATION_VIEW = "donation.view"
    const val DONATION_MANAGE = "donation.manage"
}

object M17M06Hooks {
    const val CAMPAIGN_CREATED = "M17_CAMPAIGN_CREATED"
    const val CAMPAIGN_PUBLISHED = "M17_CAMPAIGN_PUBLISHED"
    const val CONTRIBUTION_MOCK = "M17_CONTRIBUTION_MOCK_REGISTERED"
    const val INFRASTRUCTURE = "M17_NOTIFICATION_INFRASTRUCTURE"
}

object M17MockOrganizations {
    const val ORG_NORTE = M16MockOrganizations.ORG_NORTE
    const val ORG_SUR = M16MockOrganizations.ORG_SUR
    const val ORG_OESTE = M16MockOrganizations.ORG_OESTE

    val MANAGE_ORGANIZATION_IDS = M16MockOrganizations.MANAGE_ORGANIZATION_IDS
}

val M17_ELIGIBLE_ORGANIZATION_TYPES: Set<OrganizationType> = setOf(
    OrganizationType.SHELTER,
    OrganizationType.RESCUE_GROUP,
    OrganizationType.NGO
)

data class CreateM17CampaignInput(
    val organizationId: String,
    val title: String,
    val description: String,
    val campaignType: M17CampaignType,
    val goalAmountMinor: Long,
    val currency: String,
    val reference: M17CampaignReference = M17CampaignReference(),
    val coverImageRef: String? = null,
    val startsAt: Long = System.currentTimeMillis(),
    val endsAt: Long? = null
)

data class UpdateM17CampaignDetailsInput(
    val campaignId: String,
    val title: String,
    val description: String,
    val campaignType: M17CampaignType,
    val reference: M17CampaignReference = M17CampaignReference()
)

data class UpdateM17CampaignGoalInput(
    val campaignId: String,
    val goalAmountMinor: Long,
    val currency: String
)

data class RegisterM17MockContributionInput(
    val campaignId: String,
    val amountMinor: Long,
    val currency: String,
    val visibility: M17DonorVisibility = M17DonorVisibility.PUBLIC,
    val donorDisplayName: String? = null,
    val message: String? = null,
    val status: M17ContributionStatus = M17ContributionStatus.CONFIRMED
)

object M17FinancialCalculator {
    fun summarize(
        goal: M17CampaignGoal,
        contributions: List<M17Contribution>
    ): M17CampaignFinancialSummary {
        val confirmed = contributions.filter { it.status == M17ContributionStatus.CONFIRMED }
        val pending = contributions.count { it.status == M17ContributionStatus.PENDING }
        val confirmedMinor = confirmed.sumOf { it.amountMinor }
        val goalMinor = goal.amountMinor.coerceAtLeast(1)
        val percent = ((confirmedMinor * 100) / goalMinor).toInt().coerceIn(0, 999)
        return M17CampaignFinancialSummary(
            confirmedAmountMinor = confirmedMinor,
            currency = goal.currency,
            goalAmountMinor = goal.amountMinor,
            confirmedContributionCount = confirmed.size,
            pendingContributionCount = pending,
            progressPercent = percent
        )
    }
}

object M17PrivacySanitizer {
    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")

    fun scrubPublicText(text: String): String =
        text.replace(emailPattern, "[redactado]").replace(phonePattern, "[redactado]")

    fun toPublicCampaign(
        campaign: M17DonationCampaign,
        summary: M17CampaignFinancialSummary
    ): M17PublicCampaign = M17PublicCampaign(
        id = campaign.id,
        title = scrubPublicText(campaign.title),
        description = scrubPublicText(campaign.description),
        organizationDisplayName = scrubPublicText(campaign.organizationDisplayName),
        campaignType = campaign.campaignType,
        status = campaign.status,
        goalAmountMinor = summary.goalAmountMinor,
        currency = summary.currency,
        confirmedAmountMinor = summary.confirmedAmountMinor,
        progressPercent = summary.progressPercent,
        reference = campaign.reference.copy(
            needDescription = campaign.reference.needDescription?.let { scrubPublicText(it) },
            publicLocationText = campaign.reference.publicLocationText?.let { scrubPublicText(it) }
        ),
        coverImageRef = campaign.coverImageRef,
        publicUpdates = campaign.publicUpdates.map {
            it.copy(message = scrubPublicText(it.message))
        },
        startsAt = campaign.startsAt,
        endsAt = campaign.endsAt,
        confirmedContributionCount = summary.confirmedContributionCount
    )

    fun toPublicContribution(contribution: M17Contribution): M17PublicContribution? {
        if (contribution.status != M17ContributionStatus.CONFIRMED) return null
        return when (contribution.visibility) {
            M17DonorVisibility.PRIVATE -> null
            M17DonorVisibility.ANONYMOUS -> M17PublicContribution(
                id = contribution.id,
                amountMinor = contribution.amountMinor,
                currency = contribution.currency,
                donorLabel = "Donante anónimo",
                message = contribution.message?.let { scrubPublicText(it) },
                createdAt = contribution.createdAt
            )
            M17DonorVisibility.PUBLIC -> M17PublicContribution(
                id = contribution.id,
                amountMinor = contribution.amountMinor,
                currency = contribution.currency,
                donorLabel = scrubPublicText(contribution.donorDisplayName ?: "Donante"),
                message = contribution.message?.let { scrubPublicText(it) },
                createdAt = contribution.createdAt
            )
        }
    }

    fun toSummary(
        campaign: M17DonationCampaign,
        summary: M17CampaignFinancialSummary
    ): M17CampaignSummary = M17CampaignSummary(
        id = campaign.id,
        title = scrubPublicText(campaign.title),
        organizationDisplayName = scrubPublicText(campaign.organizationDisplayName),
        campaignType = campaign.campaignType,
        status = campaign.status,
        goalAmountMinor = summary.goalAmountMinor,
        currency = summary.currency,
        confirmedAmountMinor = summary.confirmedAmountMinor,
        progressPercent = summary.progressPercent,
        coverImageRef = campaign.coverImageRef,
        publicLocationText = campaign.reference.publicLocationText,
        petPublicName = campaign.reference.petPublicName,
        shelterPublicName = campaign.reference.shelterPublicName
    )
}
