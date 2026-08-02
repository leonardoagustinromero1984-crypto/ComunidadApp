package com.comunidapp.app.data.model

import com.comunidapp.app.domain.m22.M22PrivacySanitizer

/** LeoVer M22 — Prestadores y catálogo de servicios (Bloque 1 local). */
enum class M22ProviderCategory { VET, GROOMING, TRAINING, WALKING, BOARDING, TRANSPORT, OTHER }
enum class M22ProviderStatus { DRAFT, ACTIVE, SUSPENDED, ARCHIVED }
enum class M22BranchStatus { ACTIVE, INACTIVE, ARCHIVED }
enum class M22CoverageType { CITY, NEIGHBORHOOD, RADIUS }
enum class M22PriceType { FIXED, FROM, QUOTE }

data class M22CatalogFilter(
    val category: M22ProviderCategory? = null,
    val city: String? = null
)

/** Stub M06 — delivery infrastructure is not coupled to M22 operations. */
data class M22NotificationHookState(
    val available: Boolean = false,
    val providerPublished: Boolean = false,
    val providerSuspended: Boolean = false,
    val providerReactivated: Boolean = false,
    val message: String = "M22_NOTIFICATIONS_UNAVAILABLE"
)

data class M22ProviderProfile(
    val id: String,
    val ownerUserId: String,
    val organizationId: String? = null,
    val displayName: String,
    val category: M22ProviderCategory,
    val description: String,
    val city: String,
    val status: M22ProviderStatus,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublicListing(branches: List<M22ProviderBranch>, offerings: List<M22ServiceOffering>): M22PublicProviderListing =
        M22PrivacySanitizer.toPublicListing(this, branches, offerings)

    fun toPublicDetail(branches: List<M22ProviderBranch>, offerings: List<M22ServiceOffering>): M22PublicProviderDetail =
        M22PrivacySanitizer.toPublicDetail(this, branches, offerings)
}

data class M22ProviderBranch(
    val id: String,
    val providerId: String,
    val name: String,
    val city: String,
    val neighborhood: String? = null,
    val coverage: M22CoverageArea,
    val status: M22BranchStatus = M22BranchStatus.ACTIVE
)

data class M22CoverageArea(
    val type: M22CoverageType,
    val city: String,
    val neighborhood: String? = null,
    val radiusKm: Int? = null
)

data class M22ServiceOffering(
    val id: String,
    val providerId: String,
    val branchId: String? = null,
    val name: String,
    val description: String,
    val priceType: M22PriceType,
    val priceAmount: Long? = null,
    val currency: String = "ARS",
    val active: Boolean = true
)

data class M22PublicProviderListing(
    val displayName: String,
    val category: M22ProviderCategory,
    val description: String,
    val city: String,
    val branchCount: Int,
    val priceSummary: String? = null
)

data class M22PublicProviderDetail(
    val displayName: String,
    val category: M22ProviderCategory,
    val description: String,
    val city: String,
    val branches: List<M22PublicBranch>,
    val offerings: List<M22PublicServiceOffering>
)

data class M22PublicBranch(
    val name: String,
    val city: String,
    val neighborhood: String? = null,
    val coverage: String
)

data class M22PublicServiceOffering(
    val name: String,
    val description: String,
    val priceType: M22PriceType,
    val priceAmount: Long? = null,
    val currency: String = "ARS"
)

data class CreateM22ProviderInput(
    val displayName: String,
    val category: M22ProviderCategory,
    val description: String,
    val city: String,
    val organizationId: String? = null
)
data class UpdateM22ProviderInput(
    val providerId: String,
    val displayName: String? = null,
    val description: String? = null,
    val city: String? = null,
    val status: M22ProviderStatus? = null
)
data class UpsertM22BranchInput(
    val providerId: String,
    val branchId: String? = null,
    val name: String,
    val city: String,
    val neighborhood: String? = null,
    val coverage: M22CoverageArea,
    val status: M22BranchStatus = M22BranchStatus.ACTIVE
)
data class UpsertM22OfferingInput(
    val providerId: String,
    val offeringId: String? = null,
    val branchId: String? = null,
    val name: String,
    val description: String,
    val priceType: M22PriceType,
    val priceAmount: Long? = null,
    val currency: String = "ARS",
    val active: Boolean = true
)

object M22MockUsers {
    const val ADMIN = "mock_user_admin"
    const val PROVIDER = "mock_user_provider"
    const val OTHER_PROVIDER = "mock_user_other_provider"
    const val UNAUTHORIZED = "mock_user_unauthorized"
}

object M22MockProviderIds {
    const val ACTIVE_MULTI_BRANCH = "m22_provider_active_multi"
    const val DRAFT = "m22_provider_draft"
    const val SUSPENDED = "m22_provider_suspended"
    const val EMPTY_OFFERINGS = "m22_provider_empty"
}
