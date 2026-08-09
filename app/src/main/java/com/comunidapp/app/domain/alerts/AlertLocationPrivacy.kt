package com.comunidapp.app.domain.alerts

import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Representación pública de ubicación de alertas: nunca expone coordenadas exactas
 * al usuario general. El valor persistido no se modifica.
 */
data class PublicAlertLocation(
    val zoneLabel: String,
    /** Coordenadas aproximadas solo para dibujar marcadores (redondeadas). */
    val displayLatitude: Double?,
    val displayLongitude: Double?,
    val hasValidCoordinates: Boolean
)

object AlertLocationPrivacy {

    /** ~110 m de precisión (3 decimales) — zona aproximada. */
    private const val ROUND_DECIMALS = 3

    fun publicLocation(post: LostFoundPost): PublicAlertLocation {
        val lat = post.latitude
        val lng = post.longitude
        val valid = isValidCoordinate(lat, lng)
        return PublicAlertLocation(
            zoneLabel = post.location.trim().ifBlank { "Zona no especificada" },
            displayLatitude = if (valid) round(lat!!) else null,
            displayLongitude = if (valid) round(lng!!) else null,
            hasValidCoordinates = valid
        )
    }

    fun isValidCoordinate(lat: Double?, lng: Double?): Boolean {
        if (lat == null || lng == null) return false
        if (lat.isNaN() || lng.isNaN()) return false
        if (lat == 0.0 && lng == 0.0) return false
        return lat in -90.0..90.0 && lng in -180.0..180.0
    }

    fun isMapEligible(post: LostFoundPost): Boolean =
        post.status == LostFoundStatus.ACTIVE &&
            isValidCoordinate(post.latitude, post.longitude)

    fun distanceKm(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): Double {
        val r = 6371.0
        val dLat = Math.toRadians(toLat - fromLat)
        val dLng = Math.toRadians(toLng - fromLng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun round(value: Double): Double {
        var factor = 1.0
        repeat(ROUND_DECIMALS) { factor *= 10 }
        return kotlin.math.round(value * factor) / factor
    }
}

enum class AlertMapTypeFilter { ALL, LOST, FOUND }

enum class AlertMapViewMode { MAP, LIST }

enum class AlertDateFilter { ANY, LAST_7_DAYS, LAST_30_DAYS }

data class AlertZoneOption(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double
)

/** Centros públicos de zonas (no son domicilios). Solo para viewport / distancia. */
object AlertZoneCatalog {
    val zones: List<AlertZoneOption> = listOf(
        AlertZoneOption("caba_centro", "Centro / Microcentro, CABA", -34.6037, -58.3816),
        AlertZoneOption("palermo", "Palermo, CABA", -34.5889, -58.4300),
        AlertZoneOption("villa_crespo", "Villa Crespo, CABA", -34.5980, -58.4410),
        AlertZoneOption("la_plata", "La Plata", -34.9214, -57.9544),
        AlertZoneOption("cordoba", "Córdoba capital", -31.4201, -64.1888)
    )
}
