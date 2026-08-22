package com.comunidapp.app.domain.organization

import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.ProductOrganizationCategory
import java.util.Locale
import java.util.UUID

/**
 * Minimal V1 organization creation. Type is identity, not a subscription.
 */
object OrganizationCreatePolicy {

    val COMMERCIAL_CATEGORIES: List<ProductOrganizationCategory> =
        ProductOrganizationCategory.commercialVisible

    const val OTHER_BUSINESS_IN_COMMERCIAL_SELECTOR = false
    const val REFUGE_IN_COMMERCIAL_SELECTOR = false
    const val REASON_SOCIAL_INITIAL = false
    const val PUBLIC_IDENTIFIER_INITIAL = false
    const val COUNTRY_ISO_INITIAL = false

    fun genericInitialFields(): List<String> = listOf("category", "name")

    fun specificInitialFields(): List<String> = listOf("name")

    fun preselectFromKind(kind: OrganizationKindOption?): ProductOrganizationCategory? =
        kind?.let { ProductOrganizationCategory.fromOrganizationKind(it) }

    fun generateInternalSlug(publicName: String): String {
        val base = publicName.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(24)
            .ifBlank { "org" }
        val suffix = UUID.randomUUID().toString().take(8)
        return "$base-$suffix"
    }

    fun rpcCapability(category: ProductOrganizationCategory): String = category.capability
}
