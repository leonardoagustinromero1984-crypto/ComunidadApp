package com.comunidapp.app.domain.i18n

/**
 * Initial-market UX: Argentina is operational; country is not a user-facing field.
 * International Country / locale / currency / catalog architecture stays intact.
 */
object MarketUxPolicy {
    const val COUNTRY_UI_VISIBLE = false
    const val COUNTRY_ONBOARDING_VISIBLE = false
    const val COUNTRY_PROFILE_VISIBLE = false
    const val COUNTRY_ORGANIZATION_VISIBLE = false
    const val COUNTRY_SOCIAL_LOCATION_VISIBLE = false
    const val COUNTRY_VITACORA_IMPORT_USER_INPUT = false
    const val PROVINCE_VISIBLE = true
    const val LOCALITY_VISIBLE = true
    const val INTERNATIONAL_ARCHITECTURE_PRESERVED = true

    fun initialCountryIso(): String = CountryCatalog.INITIAL_COUNTRY_ISO

    fun initialCountry(): Country = CountryCatalog.ARGENTINA

    fun resolveCountryIso(raw: String?): String {
        val iso = CountryCatalog.byIso(raw)?.isoAlpha2
        return iso ?: initialCountryIso()
    }

    fun defaultCountryDisplayName(): String = initialCountry().displayName
}
