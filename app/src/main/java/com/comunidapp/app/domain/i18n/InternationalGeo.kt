package com.comunidapp.app.domain.i18n

/**
 * International geography from V1. Operational launch is Argentina only.
 * Domain names are Country / AdministrativeArea / Locality — never assume
 * "Provincia" / "Localidad" as universal identifiers.
 */
data class Country(
    val isoAlpha2: String,
    val isoAlpha3: String? = null,
    val displayName: String,
    val defaultLocale: String,
    val defaultCurrency: String,
    val defaultTimezone: String? = null,
    val callingCode: String,
    val enabled: Boolean,
    val onboardingEnabled: Boolean,
    val commercialEnabled: Boolean,
    val locationNodeId: String,
    val administrativeAreaLabel: String,
    val localityLabel: String,
    val mapFallbackLat: Double,
    val mapFallbackLng: Double
)

object CountryCatalog {
    val ARGENTINA = Country(
        isoAlpha2 = "AR",
        isoAlpha3 = "ARG",
        displayName = "Argentina",
        defaultLocale = "es-AR",
        defaultCurrency = "ARS",
        defaultTimezone = "America/Argentina/Buenos_Aires",
        callingCode = "54",
        enabled = true,
        onboardingEnabled = true,
        commercialEnabled = true,
        locationNodeId = "loc-ar",
        administrativeAreaLabel = "Provincia",
        localityLabel = "Localidad",
        mapFallbackLat = -34.6037,
        mapFallbackLng = -58.3816
    )

    private val FUTURE = listOf(
        Country("UY", "URY", "Uruguay", "es-UY", "UYU", "America/Montevideo", "598", false, false, false, "loc-uy", "Departamento", "Localidad", -34.9011, -56.1645),
        Country("CL", "CHL", "Chile", "es-CL", "CLP", "America/Santiago", "56", false, false, false, "loc-cl", "Región", "Comuna", -33.4489, -70.6693),
        Country("MX", "MEX", "México", "es-MX", "MXN", "America/Mexico_City", "52", false, false, false, "loc-mx", "Estado", "Municipio", 19.4326, -99.1332),
        Country("ES", "ESP", "España", "es-ES", "EUR", "Europe/Madrid", "34", false, false, false, "loc-es", "Comunidad autónoma", "Municipio", 40.4168, -3.7038),
        Country("US", "USA", "United States", "en-US", "USD", "America/New_York", "1", false, false, false, "loc-us", "State", "City", 38.9072, -77.0369),
        Country("BR", "BRA", "Brasil", "pt-BR", "BRL", "America/Sao_Paulo", "55", false, false, false, "loc-br", "Estado", "Município", -23.5505, -46.6333)
    )

    val all: List<Country> = listOf(ARGENTINA) + FUTURE

    fun byIso(iso: String?): Country? =
        all.firstOrNull { it.isoAlpha2.equals(iso?.trim(), ignoreCase = true) }

    fun byLocationNodeId(id: String?): Country? =
        all.firstOrNull { it.locationNodeId == id }

    fun operational(): List<Country> = all.filter { it.enabled && it.onboardingEnabled }

    fun canSelectForOnboarding(iso: String?): Boolean =
        byIso(iso)?.onboardingEnabled == true

    const val INITIAL_MARKET = "ARGENTINA"
    const val INITIAL_COUNTRY_ISO = "AR"
    const val OTHER_COUNTRIES_OPERATIONALLY_ENABLED = false
}

data class GeoPlace(
    val countryIso: String,
    val countryNodeId: String? = null,
    val administrativeAreaId: String? = null,
    val localityId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val displayLabel: String? = null,
    val address: String? = null
)

object GeoDivisionLabels {
    fun countryLabel(): String = "País"
    fun administrativeAreaLabel(countryIso: String?): String =
        CountryCatalog.byIso(countryIso)?.administrativeAreaLabel ?: "División administrativa"
    fun localityLabel(countryIso: String?): String =
        CountryCatalog.byIso(countryIso)?.localityLabel ?: "Localidad"

    fun formatDisplay(
        locality: String?,
        administrativeArea: String?,
        country: String?
    ): String = listOfNotNull(
        locality?.takeIf { it.isNotBlank() },
        administrativeArea?.takeIf { it.isNotBlank() },
        country?.takeIf { it.isNotBlank() }
    ).distinct().joinToString(", ")
}

object PhoneNumberE164 {
    data class Parsed(
        val countryCallingCode: String,
        val nationalNumber: String,
        val e164: String
    )

    fun normalize(raw: String?, defaultCountryIso: String? = null): Parsed? {
        val digits = raw.orEmpty().filter { it.isDigit() }
        if (digits.isBlank()) return null
        val country = CountryCatalog.byIso(defaultCountryIso) ?: CountryCatalog.ARGENTINA
        val calling = country.callingCode
        val national = when {
            digits.startsWith(calling) -> digits.removePrefix(calling)
            digits.startsWith("0") && defaultCountryIso.equals("AR", true) ->
                digits.trimStart('0')
            else -> digits
        }.trimStart('0').ifBlank { digits }
        if (national.length < 6) return null
        return Parsed(calling, national, "+$calling$national")
    }
}
