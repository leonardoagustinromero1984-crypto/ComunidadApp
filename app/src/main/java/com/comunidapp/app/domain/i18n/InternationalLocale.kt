package com.comunidapp.app.domain.i18n

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

data class SupportedLocale(
    val tag: String,
    val displayName: String,
    val activated: Boolean
)

object LocaleCatalog {
    const val DEFAULT_TAG = "es-AR"

    val all = listOf(
        SupportedLocale("es-AR", "Español (Argentina)", true),
        SupportedLocale("es-UY", "Español (Uruguay)", false),
        SupportedLocale("es-CL", "Español (Chile)", false),
        SupportedLocale("es-MX", "Español (México)", false),
        SupportedLocale("es-ES", "Español (España)", false),
        SupportedLocale("en-US", "English (United States)", false),
        SupportedLocale("pt-BR", "Português (Brasil)", false)
    )

    fun activated(): List<SupportedLocale> = all.filter { it.activated }

    fun toJavaLocale(tag: String = DEFAULT_TAG): Locale =
        Locale.forLanguageTag(tag.ifBlank { DEFAULT_TAG })
}

object CurrencyCatalog {
    const val DEFAULT_CODE = "ARS"

    fun codeForCountry(iso: String?): String =
        CountryCatalog.byIso(iso)?.defaultCurrency ?: DEFAULT_CODE

    fun currency(code: String = DEFAULT_CODE): Currency =
        runCatching { Currency.getInstance(code) }.getOrElse { Currency.getInstance(DEFAULT_CODE) }
}

object TimezoneCatalog {
    const val ARGENTINA = "America/Argentina/Buenos_Aires"

    fun zoneId(name: String?): ZoneId =
        runCatching { ZoneId.of(name?.takeIf { it.isNotBlank() } ?: ARGENTINA) }
            .getOrElse { ZoneId.of(ARGENTINA) }

    fun forCountry(iso: String?): ZoneId =
        zoneId(CountryCatalog.byIso(iso)?.defaultTimezone)

    /** Story expiry and server timestamps stay absolute instants. */
    fun formatInstant(epochMs: Long, zoneName: String?, localeTag: String = LocaleCatalog.DEFAULT_TAG): String {
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
            .withLocale(LocaleCatalog.toJavaLocale(localeTag))
            .withZone(zoneId(zoneName))
        return formatter.format(Instant.ofEpochMilli(epochMs))
    }
}

object InternationalFormatters {
    fun locale(tag: String? = null): Locale = LocaleCatalog.toJavaLocale(tag ?: LocaleCatalog.DEFAULT_TAG)
}
