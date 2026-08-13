package com.comunidapp.shared.ui

/**
 * Mensajes seguros para UI — sin stack traces ni PII.
 */
object ErrorSanitizer {
    fun sanitize(error: Throwable): String {
        val raw = error.message?.trim().orEmpty()
        return when {
            raw.contains("NETWORK", ignoreCase = true) -> "Problema de conexión. Intentá nuevamente."
            raw.contains("PERMISSION", ignoreCase = true) -> "No tenés permiso para esta acción."
            raw.contains("NOT_FOUND", ignoreCase = true) -> "No encontramos ese contenido."
            raw.contains("SESSION", ignoreCase = true) ||
                raw.contains("AUTH", ignoreCase = true) -> "Tu sesión no está disponible."
            raw.startsWith("M23_") || raw.startsWith("PET_") || raw.startsWith("PROFILE_") ->
                "No pudimos completar la operación. Intentá nuevamente."
            raw.isBlank() -> "Ocurrió un problema temporal. Intentá nuevamente."
            raw.length > 120 -> "Ocurrió un problema temporal. Intentá nuevamente."
            '@' in raw || raw.contains("+54") -> "Ocurrió un problema temporal. Intentá nuevamente."
            else -> raw
        }
    }
}

sealed class VerticalLoadState<out T> {
    data object Loading : VerticalLoadState<Nothing>()
    data object Empty : VerticalLoadState<Nothing>()
    data class Content<T>(val data: T) : VerticalLoadState<T>()
    data class Error(val message: String) : VerticalLoadState<Nothing>()
}
