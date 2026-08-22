package com.comunidapp.app.domain.map

/**
 * Provider-neutral map types. Feature code must not import Google/MapLibre classes.
 * If the base SDK later requires usage charges, swap the Android renderer only.
 */
data class LeoVerGeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range" }
        require(longitude in -180.0..180.0) { "longitude out of range" }
    }

    companion object {
        fun parseOrNull(latitude: Double?, longitude: Double?): LeoVerGeoPoint? {
            val lat = latitude ?: return null
            val lng = longitude ?: return null
            if (lat.isNaN() || lng.isNaN() || lat.isInfinite() || lng.isInfinite()) return null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
            return LeoVerGeoPoint(lat, lng)
        }
    }
}

data class LeoVerMapMarker(
    val id: String,
    val position: LeoVerGeoPoint,
    val title: String,
    val subtitle: String? = null,
    val category: String? = null,
    val rating: Double? = null,
    val locality: String? = null,
    val distanceKm: Double? = null
)

data class LeoVerMapCameraState(
    val center: LeoVerGeoPoint,
    val zoom: Float = DEFAULT_ZOOM
) {
    companion object {
        const val DEFAULT_ZOOM = 13f
        val ARGENTINA_FALLBACK = LeoVerGeoPoint(-34.6037, -58.3816)

        fun cameraOrFallback(latitude: Double?, longitude: Double?, zoom: Float = 14f): LeoVerMapCameraState {
            val point = LeoVerGeoPoint.parseOrNull(latitude, longitude) ?: ARGENTINA_FALLBACK
            return LeoVerMapCameraState(point, zoom = zoom)
        }

        fun defaultCenter(countryIso: String?): LeoVerGeoPoint {
            val country = com.comunidapp.app.domain.i18n.CountryCatalog.byIso(countryIso)
                ?: com.comunidapp.app.domain.i18n.CountryCatalog.ARGENTINA
            return LeoVerGeoPoint(country.mapFallbackLat, country.mapFallbackLng)
        }
    }
}

data class LeoVerMapBounds(
    val southwest: LeoVerGeoPoint,
    val northeast: LeoVerGeoPoint
)

enum class LeoVerMapProviderId {
    GOOGLE_MAPS_RENDER_ONLY,
    MAPLIBRE_CANDIDATE
}

object LeoVerMapPolicy {
    const val COST_POLICY = "FREE_ONLY"
    const val PRODUCTION_MAP_POLICY = "FREE_ONLY"
    const val PRODUCTION_REQUIRES_PAID_MAP_API = false
    const val PAID_MAP_API_DEPENDENCIES = 0
    const val SEARCH_AUTHORITY = "LEOVER_CANONICAL_DATA"
    const val NEARBY_CALCULATION = "SUPABASE_POSTGIS"
    const val ADDRESS_GEOCODING_DEPENDENCY = "NONE"
    const val PROVIDER_REPLACEABLE = true
    const val NO_AUTOMATIC_PAID_FALLBACK = true
    const val GOOGLE_PLACES_ENABLED = false
    const val GOOGLE_AUTOCOMPLETE_ENABLED = false
    const val GOOGLE_GEOCODING_ENABLED = false
    const val GOOGLE_ROUTES_ENABLED = false
    const val GOOGLE_NAVIGATION_ENABLED = false
    const val GOOGLE_STREET_VIEW_ENABLED = false
    const val PRIVATE_HOME_COORDINATE_PUBLIC = false

    val currentAndroidProvider: LeoVerMapProviderId = LeoVerMapProviderId.GOOGLE_MAPS_RENDER_ONLY

    fun isFixedPublicPremisesCategory(storageCategory: String): Boolean =
        storageCategory.uppercase() in FIXED_PREMISES

    fun isMobilePersonCategory(storageCategory: String): Boolean =
        storageCategory.uppercase() in MOBILE_PERSON

    private val FIXED_PREMISES = setOf("VETERINARY", "BOARDING", "GROOMING", "SHOP", "PET_FRIENDLY")
    private val MOBILE_PERSON = setOf("WALKING", "TRAINING", "CARE")
}
