package com.comunidapp.app.domain.m22

import com.comunidapp.app.data.model.M22CoverageType
import com.comunidapp.app.data.model.M22ProviderBranch
import com.comunidapp.app.data.model.M22ProviderProfile
import com.comunidapp.app.data.model.M22PublicBranch
import com.comunidapp.app.data.model.M22PublicProviderDetail
import com.comunidapp.app.data.model.M22PublicProviderListing
import com.comunidapp.app.data.model.M22PublicServiceOffering
import com.comunidapp.app.data.model.M22ServiceOffering

object M22PrivacySanitizer {
    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")
    private val addressPattern = Regex("(?i)(calle|av\\.?|avenida|pasaje)\\s+[\\w\\s\\d]+")
    private val documentPattern = Regex("(?i)(dni|cuil|cuit|documento)\\s*[:#]?\\s*[\\w\\d-]+")

    fun scrubPublicText(text: String): String = text
        .replace(emailPattern, "[redactado]")
        .replace(phonePattern, "[redactado]")
        .replace(addressPattern, "[redactado]")
        .replace(documentPattern, "[redactado]")
        .trim()

    fun toPublicListing(
        provider: M22ProviderProfile,
        branches: List<M22ProviderBranch>,
        offerings: List<M22ServiceOffering>
    ): M22PublicProviderListing = M22PublicProviderListing(
        displayName = scrubPublicText(provider.displayName),
        category = provider.category,
        description = scrubPublicText(provider.description),
        city = scrubPublicText(provider.city),
        branchCount = branches.count { it.status.name == "ACTIVE" },
        priceSummary = offerings.filter { it.active }.minByOrNull { it.priceAmount ?: Long.MAX_VALUE }?.let { offering ->
            when (offering.priceType.name) {
                "QUOTE" -> "A cotizar"
                "FROM" -> "Desde ${offering.currency} ${offering.priceAmount}"
                else -> "${offering.currency} ${offering.priceAmount}"
            }
        }
    )

    fun toPublicDetail(
        provider: M22ProviderProfile,
        branches: List<M22ProviderBranch>,
        offerings: List<M22ServiceOffering>
    ): M22PublicProviderDetail = M22PublicProviderDetail(
        displayName = scrubPublicText(provider.displayName),
        category = provider.category,
        description = scrubPublicText(provider.description),
        city = scrubPublicText(provider.city),
        branches = branches.filter { it.status.name == "ACTIVE" }.map(::toPublicBranch),
        offerings = offerings.filter { it.active }.map(::toPublicOffering)
    )

    private fun toPublicBranch(branch: M22ProviderBranch): M22PublicBranch = M22PublicBranch(
        name = scrubPublicText(branch.name),
        city = scrubPublicText(branch.city),
        neighborhood = branch.neighborhood?.let(::scrubPublicText),
        coverage = when (branch.coverage.type) {
            M22CoverageType.CITY -> "Ciudad: ${scrubPublicText(branch.coverage.city)}"
            M22CoverageType.NEIGHBORHOOD -> "Barrio: ${scrubPublicText(branch.coverage.neighborhood.orEmpty())}"
            M22CoverageType.RADIUS -> "Radio de ${branch.coverage.radiusKm} km"
        }
    )

    private fun toPublicOffering(offering: M22ServiceOffering): M22PublicServiceOffering =
        M22PublicServiceOffering(
            name = scrubPublicText(offering.name),
            description = scrubPublicText(offering.description),
            priceType = offering.priceType,
            priceAmount = offering.priceAmount,
            currency = offering.currency
        )
}
