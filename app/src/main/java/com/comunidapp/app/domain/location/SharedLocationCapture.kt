package com.comunidapp.app.domain.location

import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState

/**
 * Shared rule for Lost, Found, walker and organization maps.
 * A granted Android permission is not asked again.
 * The Argentina map fallback is a camera placeholder, never a real fix.
 */
object SharedLocationCapture {
    fun shouldRequestPermission(alreadyGranted: Boolean): Boolean = !alreadyGranted

    fun isFallback(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        val fallback = LeoVerMapCameraState.ARGENTINA_FALLBACK
        return latitude == fallback.latitude && longitude == fallback.longitude
    }

    fun isFallback(point: LeoVerGeoPoint?): Boolean =
        point != null && isFallback(point.latitude, point.longitude)

    fun realFixOrNull(point: LeoVerGeoPoint?): LeoVerGeoPoint? =
        point?.takeUnless { isFallback(it) }
}
