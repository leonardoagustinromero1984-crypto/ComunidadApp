package com.comunidapp.shared.location

import com.comunidapp.shared.permissions.PermissionStatus

/**
 * Ubicación aproximada para publicación — sin background / tracking / historial.
 * KMP-8: el formulario usa entrada manual; GPS queda deferred.
 */
interface ApproximateLocationProvider {
    suspend fun permissionStatus(): PermissionStatus
    suspend fun requestPermission(): PermissionStatus

    /**
     * Si la plataforma obtiene una zona aproximada, devolverla.
     * Nunca exponer lat/lng al caller de UI.
     */
    suspend fun currentApproximateLocation(): ApproximateLocation?
}

class ManualOnlyApproximateLocationProvider : ApproximateLocationProvider {
    override suspend fun permissionStatus(): PermissionStatus = PermissionStatus.UNKNOWN
    override suspend fun requestPermission(): PermissionStatus = PermissionStatus.UNKNOWN
    override suspend fun currentApproximateLocation(): ApproximateLocation? = null
}
