package com.comunidapp.shared.location

/**
 * Ubicación segura para UI pública — sin lat/lng.
 */
data class ApproximateLocation(
    val locality: String,
    val region: String? = null,
    val country: String? = null
) {
    init {
        require(locality.isNotBlank()) { "APPROX_LOCATION_LOCALITY_BLANK" }
    }

    fun displayLabel(): String = buildString {
        append(locality.trim())
        region?.trim()?.takeIf { it.isNotEmpty() }?.let { append(", ").append(it) }
        country?.trim()?.takeIf { it.isNotEmpty() }?.let { append(", ").append(it) }
    }
}
