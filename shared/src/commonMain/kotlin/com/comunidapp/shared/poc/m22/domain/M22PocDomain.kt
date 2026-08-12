package com.comunidapp.shared.poc.m22.domain

import com.comunidapp.shared.poc.m22.model.M22PocDetail
import com.comunidapp.shared.poc.m22.model.M22PocListing
import com.comunidapp.shared.poc.m22.model.M22PocOffering
import com.comunidapp.shared.poc.m22.model.M22PocPriceType

/**
 * Domain rules adapted from M22PrivacySanitizer + M22ProviderResilience (production).
 */
object M22PocPrivacy {
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

    fun sanitizeListing(listing: M22PocListing): M22PocListing = listing.copy(
        displayName = scrubPublicText(listing.displayName),
        description = scrubPublicText(listing.description),
        city = scrubPublicText(listing.city)
    )

    fun sanitizeDetail(detail: M22PocDetail): M22PocDetail = detail.copy(
        displayName = scrubPublicText(detail.displayName),
        description = scrubPublicText(detail.description),
        city = scrubPublicText(detail.city),
        branches = detail.branches.map {
            it.copy(
                name = scrubPublicText(it.name),
                city = scrubPublicText(it.city),
                neighborhood = it.neighborhood?.let(::scrubPublicText),
                coverage = scrubPublicText(it.coverage)
            )
        },
        offerings = detail.offerings.map {
            it.copy(
                name = scrubPublicText(it.name),
                description = scrubPublicText(it.description)
            )
        }
    )
}

object M22PocResilience {
    fun safeUserMessage(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            "NOT_AUTHENTICATED" in raw -> "Tenés que iniciar sesión."
            "M22_" in raw -> "No pudimos cargar el catálogo de prestadores."
            raw.isBlank() -> "No pudimos completar la operación."
            else -> raw.replace(Regex("(?i)(provider|branch|offering|user)[_-]?id\\s*=\\s*\\S+"), "[redactado]")
        }
    }
}

object M22PocPricing {
    fun summary(offerings: List<M22PocOffering>): String? {
        val active = offerings.minByOrNull { it.priceAmount ?: Long.MAX_VALUE } ?: return null
        return when (active.priceType) {
            M22PocPriceType.QUOTE -> "A cotizar"
            M22PocPriceType.FROM -> "Desde ${active.currency} ${active.priceAmount}"
            M22PocPriceType.FIXED -> "${active.currency} ${active.priceAmount}"
        }
    }
}
