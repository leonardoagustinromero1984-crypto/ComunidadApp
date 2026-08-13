package com.comunidapp.shared.permissions

/**
 * Contratos mínimos de permiso (KMP-8).
 * No solicitar al arranque; solo contextualmente si una plataforma lo exige.
 * PHPicker / PickVisualMedia suelen no requerir PHOTO_LIBRARY.
 */
enum class PlatformPermission {
    PHOTO_LIBRARY,
    CAMERA,
    LOCATION_WHEN_IN_USE
}

enum class PermissionStatus {
    UNKNOWN,
    DENIED,
    GRANTED,
    RESTRICTED
}

interface PermissionRequester {
    suspend fun status(permission: PlatformPermission): PermissionStatus
    suspend fun request(permission: PlatformPermission): PermissionStatus
}

/** Default: no pide OS permissions (picker moderno / ubicación manual). */
class NoOpPermissionRequester : PermissionRequester {
    override suspend fun status(permission: PlatformPermission): PermissionStatus =
        PermissionStatus.UNKNOWN

    override suspend fun request(permission: PlatformPermission): PermissionStatus =
        PermissionStatus.UNKNOWN
}
