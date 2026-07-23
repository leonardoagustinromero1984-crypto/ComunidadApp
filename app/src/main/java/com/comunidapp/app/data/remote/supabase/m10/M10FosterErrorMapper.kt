package com.comunidapp.app.data.remote.supabase.m10

/**
 * LeoVer M10 — errores tipados de hogares de tránsito.
 */
class M10FosterException(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M10FosterErrorMapper {

    private val knownCodes = listOf(
        "FOSTER_HOME_NOT_FOUND",
        "FOSTER_HOME_ALREADY_EXISTS",
        "FOSTER_HOME_NOT_ACTIVE",
        "FOSTER_HOME_UNAVAILABLE",
        "FOSTER_HOME_FULL",
        "FOSTER_HOME_INCOMPATIBLE",
        "FOSTER_HOME_CAPACITY_INVALID",
        "FOSTER_REQUEST_NOT_FOUND",
        "FOSTER_REQUEST_ALREADY_EXISTS",
        "FOSTER_REQUEST_NOT_ACTIVE",
        "FOSTER_REQUEST_INVALID_TRANSITION",
        "FOSTER_REQUEST_FORBIDDEN",
        "FOSTER_PLACEMENT_NOT_FOUND",
        "FOSTER_PLACEMENT_ALREADY_ACTIVE",
        "FOSTER_PLACEMENT_CAPACITY_EXCEEDED",
        "PET_NOT_ELIGIBLE_FOR_FOSTER",
        "PET_ALREADY_IN_FOSTER",
        "PET_NOT_FOUND",
        "NOT_AUTHENTICATED",
        "FORBIDDEN",
        "NETWORK",
        "TIMEOUT",
        "SERIALIZATION"
    )

    fun codeOf(throwable: Throwable): String {
        if (throwable is M10FosterException) return throwable.code
        val raw = buildString {
            append(throwable.message.orEmpty())
            append(' ')
            append(throwable.cause?.message.orEmpty())
        }
        knownCodes.forEach { code ->
            if (raw.contains(code, ignoreCase = true)) return code
        }
        val lower = raw.lowercase()
        return when {
            "timeout" in lower || "timed out" in lower -> "TIMEOUT"
            "unable to resolve host" in lower || "failed to connect" in lower ||
                "network" in lower || "unreachable" in lower -> "NETWORK"
            "json" in lower || "serializ" in lower || "decode" in lower -> "SERIALIZATION"
            else -> "UNKNOWN"
        }
    }

    fun userMessage(code: String): String = when (code) {
        "FOSTER_HOME_NOT_FOUND" -> "No encontramos ese hogar de tránsito."
        "FOSTER_HOME_ALREADY_EXISTS" -> "Ya tenés un perfil de hogar de tránsito activo."
        "FOSTER_HOME_NOT_ACTIVE" -> "Ese hogar no está activo para recibir solicitudes."
        "FOSTER_HOME_UNAVAILABLE" -> "Ese hogar no está disponible ahora."
        "FOSTER_HOME_FULL" -> "Ese hogar no tiene capacidad libre."
        "FOSTER_HOME_INCOMPATIBLE" -> "La mascota no es compatible con este hogar."
        "FOSTER_HOME_CAPACITY_INVALID" -> "La capacidad debe ser mayor que cero."
        "FOSTER_REQUEST_NOT_FOUND" -> "No encontramos esa solicitud."
        "FOSTER_REQUEST_ALREADY_EXISTS" -> "Ya hay una solicitud activa para esa mascota en este hogar."
        "FOSTER_REQUEST_NOT_ACTIVE" -> "Esta solicitud ya no está activa."
        "FOSTER_REQUEST_INVALID_TRANSITION" -> "Ese cambio de estado no está permitido."
        "FOSTER_REQUEST_FORBIDDEN" -> "No tenés permiso sobre esta solicitud."
        "FOSTER_PLACEMENT_NOT_FOUND" -> "No encontramos ese alojamiento."
        "FOSTER_PLACEMENT_ALREADY_ACTIVE" -> "La mascota ya tiene un alojamiento activo."
        "FOSTER_PLACEMENT_CAPACITY_EXCEEDED" -> "No hay capacidad para iniciar el ingreso."
        "PET_NOT_ELIGIBLE_FOR_FOSTER" -> "Esa mascota no puede ir a tránsito."
        "PET_ALREADY_IN_FOSTER" -> "Esa mascota ya está en un hogar de tránsito."
        "PET_NOT_FOUND" -> "No encontramos la mascota."
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        "FORBIDDEN" -> "No tenés permiso para esta acción."
        "NETWORK" -> "Problema de conexión. Intentá de nuevo."
        "TIMEOUT" -> "La operación tardó demasiado. Intentá de nuevo."
        "SERIALIZATION" -> "No pudimos interpretar la respuesta del servidor."
        else -> "No se pudo completar la operación."
    }

    fun <T> failure(throwable: Throwable): Result<T> {
        val code = codeOf(throwable)
        return Result.failure(M10FosterException(code, userMessage(code), throwable))
    }

    fun <T> fail(code: String): Result<T> =
        Result.failure(M10FosterException(code, userMessage(code)))
}
