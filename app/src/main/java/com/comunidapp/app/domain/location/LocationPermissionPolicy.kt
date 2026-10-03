package com.comunidapp.app.domain.location

/**
 * Android location permission next step for Lost/Found.
 *
 * Runtime permission is the normal path. Settings opens only when the permission
 * is no longer requestable, or when location services themselves need the user.
 * Returning from Settings must be revalidated by the caller (permission + fix).
 */
enum class LocationPermissionNext {
    ALREADY_GRANTED,
    REQUEST_RUNTIME,
    EXPLAIN_AND_REQUEST,
    OPEN_APP_SETTINGS,
    OPEN_LOCATION_SOURCE_SETTINGS
}

object LocationPermissionPolicy {
    fun next(
        granted: Boolean,
        shouldShowRationale: Boolean,
        hasRequestedBefore: Boolean,
        locationServicesEnabled: Boolean
    ): LocationPermissionNext = when {
        granted && !locationServicesEnabled -> LocationPermissionNext.OPEN_LOCATION_SOURCE_SETTINGS
        granted -> LocationPermissionNext.ALREADY_GRANTED
        shouldShowRationale -> LocationPermissionNext.EXPLAIN_AND_REQUEST
        !hasRequestedBefore -> LocationPermissionNext.REQUEST_RUNTIME
        else -> LocationPermissionNext.OPEN_APP_SETTINGS
    }
}
