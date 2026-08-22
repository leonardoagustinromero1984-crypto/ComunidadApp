package com.comunidapp.app.domain.commercial

/**
 * LeoVer Comercial V1. Prices are not hardcoded. Trial terms are snapshotted
 * at activation and never recalculated from a later global offer.
 */
enum class CommercialProductFamily {
    LEOVER_COMMERCIAL
}

enum class CommercialTier {
    LEOVER_COMMERCIAL_PROFESSIONAL,
    LEOVER_COMMERCIAL_ORGANIZATION
}

enum class CommercialEntitlementStatus {
    TRIAL_ACTIVE,
    ACTIVE,
    GRACE_PERIOD,
    EXPIRED,
    NONE
}

data class CommercialOffer(
    val offerCode: String,
    val trialDays: Int,
    val paymentMethodRequiredAtStart: Boolean,
    val communityVisibleDuringTrial: Boolean = true,
    val mapVisibleDuringTrial: Boolean = true,
    val searchVisibleDuringTrial: Boolean = true
)

data class CommercialOfferSnapshot(
    val offerCode: String,
    val trialStartedAtEpochMs: Long,
    val trialEndsAtEpochMs: Long,
    val trialDaysSnapshot: Int,
    val paymentRequiredAtActivationSnapshot: Boolean,
    val commercialTier: CommercialTier,
    val priceSnapshot: String? = null,
    val currency: String? = null,
    val status: CommercialEntitlementStatus
)

object CommercialOfferPolicy {
    val LAUNCH_90_NO_CARD = CommercialOffer(
        offerCode = "LAUNCH_90_NO_CARD",
        trialDays = 90,
        paymentMethodRequiredAtStart = false
    )

    val STANDARD_30_WITH_PAYMENT = CommercialOffer(
        offerCode = "STANDARD_30_WITH_PAYMENT",
        trialDays = 30,
        paymentMethodRequiredAtStart = true
    )

    const val CURRENT_LAUNCH_OFFER_CODE = "LAUNCH_90_NO_CARD"

    const val FOSTER_COMMERCIAL_STATUS = "FREE_FOR_NOW_PENDING_PRODUCT_VALIDATION"
    const val BRAND_STUDIO_REQUIRED_V1 = false
    const val BRAND_USES_ORGANIZATION_TIER = true

    fun currentLaunchOffer(): CommercialOffer = LAUNCH_90_NO_CARD

    fun snapshotAtActivation(
        offer: CommercialOffer,
        tier: CommercialTier,
        startedAtEpochMs: Long
    ): CommercialOfferSnapshot {
        val ends = startedAtEpochMs + offer.trialDays * 86_400_000L
        return CommercialOfferSnapshot(
            offerCode = offer.offerCode,
            trialStartedAtEpochMs = startedAtEpochMs,
            trialEndsAtEpochMs = ends,
            trialDaysSnapshot = offer.trialDays,
            paymentRequiredAtActivationSnapshot = offer.paymentMethodRequiredAtStart,
            commercialTier = tier,
            status = CommercialEntitlementStatus.TRIAL_ACTIVE
        )
    }

    fun remainingTrialDays(snapshot: CommercialOfferSnapshot, nowEpochMs: Long): Long {
        val ms = snapshot.trialEndsAtEpochMs - nowEpochMs
        return (ms / 86_400_000L).coerceAtLeast(0)
    }

    fun futurePolicyDoesNotShortenExisting(
        existing: CommercialOfferSnapshot,
        newGlobalOffer: CommercialOffer
    ): Boolean = existing.trialDaysSnapshot >= newGlobalOffer.trialDays ||
        existing.offerCode != newGlobalOffer.offerCode

    fun publiclyDiscoverable(status: CommercialEntitlementStatus): Boolean =
        status == CommercialEntitlementStatus.TRIAL_ACTIVE ||
            status == CommercialEntitlementStatus.ACTIVE ||
            status == CommercialEntitlementStatus.GRACE_PERIOD

    fun expiredHidesFromCommunity(status: CommercialEntitlementStatus): Boolean =
        status == CommercialEntitlementStatus.EXPIRED

    fun expiredPreservesProfileData(): Boolean = true
}
