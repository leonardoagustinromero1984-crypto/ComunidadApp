package com.comunidapp.app.data.model

/** M17 Bloque 3 — bienes, voluntariado y transparencia (local/mock). */

enum class M17InKindCategory {
    FOOD, MEDICATION, HYGIENE, BEDDING, TRANSPORT_SUPPLIES, CONSTRUCTION_MATERIALS, OTHER
}

enum class M17InKindNeedStatus {
    DRAFT, PUBLISHED, FULFILLED, CANCELLED;

    val isTerminal: Boolean get() = this == FULFILLED || this == CANCELLED
    val isPublic: Boolean get() = this == PUBLISHED || this == FULFILLED
}

enum class M17InKindPledgeStatus {
    PLEDGED, ACCEPTED, DELIVERED, CANCELLED, REJECTED
}

enum class M17VolunteerOpportunityType {
    SHELTER_SUPPORT, ANIMAL_CARE, TRANSPORT, EVENTS, FUNDRAISING,
    PHOTOGRAPHY, ADMINISTRATIVE, CONSTRUCTION, PROFESSIONAL_SUPPORT, OTHER
}

enum class M17VolunteerOpportunityStatus {
    DRAFT, PUBLISHED, PAUSED, FILLED, COMPLETED, CANCELLED;

    val isTerminal: Boolean get() = this == FILLED || this == COMPLETED || this == CANCELLED
    val isPublic: Boolean get() = this == PUBLISHED || this == PAUSED || this == FILLED || this == COMPLETED
}

enum class M17VolunteerApplicationStatus {
    SUBMITTED, REVIEWING, ACCEPTED, REJECTED, WITHDRAWN, COMPLETED
}

data class M17InKindDonationNeed(
    val id: String,
    val organizationId: String,
    val organizationDisplayName: String,
    val title: String,
    val description: String,
    val category: M17InKindCategory,
    val status: M17InKindNeedStatus,
    val quantityRequested: Int,
    val quantityUnit: String,
    val campaignId: String? = null,
    val shelterProfileId: String? = null,
    val publicLocationText: String? = null,
    val coverImageRef: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

data class M17PublicInKindNeed(
    val id: String,
    val title: String,
    val description: String,
    val organizationDisplayName: String,
    val category: M17InKindCategory,
    val status: M17InKindNeedStatus,
    val quantityRequested: Int,
    val quantityPledged: Int,
    val quantityDelivered: Int,
    val quantityUnit: String,
    val coveragePercent: Int,
    val publicLocationText: String? = null,
    val shelterPublicName: String? = null
)

data class M17InKindPledge(
    val id: String,
    val needId: String,
    val quantity: Int,
    val status: M17InKindPledgeStatus,
    val donorDisplayName: String? = null,
    val message: String? = null,
    val userId: String,
    val createdAt: Long
)

data class M17InKindSearchFilter(
    val query: String = "",
    val category: M17InKindCategory? = null,
    val organizationId: String? = null,
    val activeOnly: Boolean = true
)

data class M17VolunteerOpportunity(
    val id: String,
    val organizationId: String,
    val organizationDisplayName: String,
    val title: String,
    val description: String,
    val type: M17VolunteerOpportunityType,
    val status: M17VolunteerOpportunityStatus,
    val slotsNeeded: Int,
    val slotsFilled: Int,
    val shelterProfileId: String? = null,
    val publicLocationText: String? = null,
    val scheduleHint: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

data class M17PublicVolunteerOpportunity(
    val id: String,
    val title: String,
    val description: String,
    val organizationDisplayName: String,
    val type: M17VolunteerOpportunityType,
    val status: M17VolunteerOpportunityStatus,
    val slotsNeeded: Int,
    val slotsFilled: Int,
    val publicLocationText: String? = null,
    val scheduleHint: String? = null
)

data class M17VolunteerApplication(
    val id: String,
    val opportunityId: String,
    val userId: String,
    val status: M17VolunteerApplicationStatus,
    val availabilityHint: String? = null,
    val skillsHint: String? = null,
    val message: String? = null,
    val createdAt: Long
)

data class M17VolunteerSearchFilter(
    val query: String = "",
    val type: M17VolunteerOpportunityType? = null,
    val organizationId: String? = null,
    val activeOnly: Boolean = true
)

data class M17FundUsageItem(
    val id: String,
    val label: String,
    val amountMinor: Long,
    val currency: String,
    val receiptRef: String? = null
)

data class M17TransparencyMilestone(
    val id: String,
    val title: String,
    val description: String,
    val achievedAt: Long
)

data class M17CampaignTransparencyReport(
    val campaignId: String,
    val summaryText: String,
    val usageItems: List<M17FundUsageItem>,
    val milestones: List<M17TransparencyMilestone>,
    val finalOutcome: String? = null,
    val updatedAt: Long
)

data class M17PublicReceiptRef(
    val id: String,
    val label: String,
    val imageRef: String,
    val amountMinor: Long? = null,
    val currency: String? = null
)

object M17ExtendedPrivacySanitizer {
    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")

    fun scrub(text: String): String =
        text.replace(emailPattern, "[redactado]").replace(phonePattern, "[redactado]")

    fun toPublicNeed(
        need: M17InKindDonationNeed,
        pledged: Int,
        delivered: Int
    ): M17PublicInKindNeed {
        val requested = need.quantityRequested.coerceAtLeast(1)
        val coverage = ((delivered.coerceAtLeast(pledged) * 100) / requested).coerceIn(0, 999)
        return M17PublicInKindNeed(
            id = need.id,
            title = scrub(need.title),
            description = scrub(need.description),
            organizationDisplayName = scrub(need.organizationDisplayName),
            category = need.category,
            status = need.status,
            quantityRequested = need.quantityRequested,
            quantityPledged = pledged,
            quantityDelivered = delivered,
            quantityUnit = need.quantityUnit,
            coveragePercent = coverage,
            publicLocationText = need.publicLocationText?.let { scrub(it) }
        )
    }

    fun toPublicOpportunity(opp: M17VolunteerOpportunity): M17PublicVolunteerOpportunity =
        M17PublicVolunteerOpportunity(
            id = opp.id,
            title = scrub(opp.title),
            description = scrub(opp.description),
            organizationDisplayName = scrub(opp.organizationDisplayName),
            type = opp.type,
            status = opp.status,
            slotsNeeded = opp.slotsNeeded,
            slotsFilled = opp.slotsFilled,
            publicLocationText = opp.publicLocationText?.let { scrub(it) },
            scheduleHint = opp.scheduleHint?.let { scrub(it) }
        )

    fun sanitizeReceipt(ref: M17PublicReceiptRef): M17PublicReceiptRef =
        ref.copy(label = scrub(ref.label))
}

object M17ExtendedCalculator {
    fun inKindCoverage(requested: Int, delivered: Int): Int {
        val base = requested.coerceAtLeast(1)
        return ((delivered.coerceAtLeast(0) * 100) / base).coerceIn(0, 999)
    }
}
