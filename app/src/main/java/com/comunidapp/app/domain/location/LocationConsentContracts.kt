package com.comunidapp.app.domain.location

/**
 * Versioned location treatment. Legal text lives in legal_documents.
 * Términos acceptance is not an Android location permission.
 */
object LocationConsentContracts {
    const val CONSENT_CODE = "LOCATION_TREATMENT"
    const val CONSENT_VERSION = "1"
    const val TERMS_VERSION = "0.1-draft"
    const val PRIVACY_VERSION = "0.1-draft"
    const val BACKGROUND_LOCATION = false

    const val TITLE = "Ubicación en LeoVer"
    const val BODY =
        "Usamos tu ubicación para mostrar servicios cercanos, " +
            "ubicar alertas de animales perdidos o encontrados " +
            "y conectar casos con refugios y rescatistas cercanos."
    const val TREATMENT =
        "LeoVer puede utilizar la ubicación proporcionada por " +
            "el dispositivo o seleccionada en el mapa para mostrar " +
            "servicios cercanos, registrar alertas y encontrar " +
            "refugios o rescatistas próximos al caso."
    const val NO_BACKGROUND =
        "LeoVer no realiza seguimiento continuo de tu ubicación en segundo plano."
    const val RESPONDER_BASE =
        "La ubicación base o zona de actividad registrada podrá " +
            "utilizarse para calcular cercanía y enviar alertas " +
            "relacionadas con animales que necesiten asistencia."
    const val CTA_ALLOW = "Permitir ubicación"
    const val CTA_ENABLE = "Activar ubicación"
    const val DENIED_HINT =
        "Podés seguir usando LeoVer. Las funciones que necesitan ubicación te van a pedir activarla."
}

data class LocationConsentRecord(
    val personId: String,
    val consentCode: String = LocationConsentContracts.CONSENT_CODE,
    val version: String = LocationConsentContracts.CONSENT_VERSION,
    val recordedAtEpochMs: Long
)
