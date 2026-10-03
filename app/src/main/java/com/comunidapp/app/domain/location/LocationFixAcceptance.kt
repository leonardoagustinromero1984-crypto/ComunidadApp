package com.comunidapp.app.domain.location

import com.comunidapp.app.domain.map.LeoVerGeoPoint

/**
 * Confirmed device position for the Lost/Found pin.
 *
 * A fresh fused fix is accepted when the coordinate parses and is not the
 * Argentina camera placeholder.
 *
 * [fromLastKnown] is accepted only when the reading has an age, that age is
 * at most [LAST_KNOWN_MAX_AGE_MS], and accuracy is not coarser than
 * [LAST_KNOWN_MAX_ACCURACY_METERS]. A missing timestamp or a stale/coarse
 * last-known reading is not a confirmed position.
 */
object LocationFixAcceptance {
    const val LAST_KNOWN_MAX_AGE_MS = 3 * 60 * 1000L
    const val LAST_KNOWN_MAX_ACCURACY_METERS = 1_000f

    fun confirmedOrNull(
        latitude: Double?,
        longitude: Double?,
        ageMs: Long?,
        accuracyMeters: Float?,
        fromLastKnown: Boolean
    ): LeoVerGeoPoint? {
        val point = LeoVerGeoPoint.parseOrNull(latitude, longitude) ?: return null
        if (SharedLocationCapture.isFallback(point)) return null
        if (!fromLastKnown) return point
        val age = ageMs ?: return null
        if (age < 0L || age > LAST_KNOWN_MAX_AGE_MS) return null
        if (accuracyMeters != null && accuracyMeters > LAST_KNOWN_MAX_ACCURACY_METERS) return null
        return point
    }
}

/**
 * The map camera moves only toward a real coordinate.
 * The Argentina fallback may remain a viewport placeholder, never a claimed fix.
 */
object MapCameraPolicy {
    fun shouldMoveTo(point: LeoVerGeoPoint?): Boolean =
        SharedLocationCapture.realFixOrNull(point) != null

    fun presentsConfirmedPosition(point: LeoVerGeoPoint?, confirmed: Boolean): Boolean =
        confirmed && shouldMoveTo(point)
}
