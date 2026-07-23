package com.comunidapp.app.data.remote.supabase.m11

/**
 * LeoVer M11 — errores tipados de operación de refugios.
 */
class M11ShelterException(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M11ShelterErrorMapper {

    private val knownCodes = listOf(
        "SHELTER_NOT_FOUND",
        "SHELTER_ALREADY_EXISTS",
        "SHELTER_NOT_ACTIVE",
        "SHELTER_FULL",
        "SHELTER_FORBIDDEN",
        "SHELTER_PET_NOT_FOUND",
        "SHELTER_PET_ALREADY_ACTIVE",
        "SHELTER_PET_NOT_ELIGIBLE",
        "SHELTER_PET_INVALID_TRANSITION",
        "SHELTER_CAPACITY_EXCEEDED",
        "SHELTER_VOLUNTEER_NOT_FOUND",
        "SHELTER_VOLUNTEER_ALREADY_ASSIGNED",
        "SHELTER_VOLUNTEER_INVALID_TRANSITION",
        "SHELTER_VOLUNTEER_FORBIDDEN",
        "ORGANIZATION_NOT_ELIGIBLE",
        "ORGANIZATION_MEMBERSHIP_REQUIRED",
        "PET_NOT_FOUND",
        "NOT_AUTHENTICATED",
        "FORBIDDEN",
        "NETWORK",
        "TIMEOUT",
        "SERIALIZATION"
    )

    fun codeOf(throwable: Throwable): String {
        if (throwable is M11ShelterException) return throwable.code
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
        "SHELTER_NOT_FOUND" -> "No encontramos ese refugio."
        "SHELTER_ALREADY_EXISTS" -> "Esa organización ya tiene un refugio activo en esa sede."
        "SHELTER_NOT_ACTIVE" -> "Ese refugio no está operativo."
        "SHELTER_FULL" -> "El refugio no tiene capacidad libre."
        "SHELTER_FORBIDDEN" -> "No tenés permiso para operar este refugio."
        "SHELTER_PET_NOT_FOUND" -> "No encontramos ese alojamiento en el refugio."
        "SHELTER_PET_ALREADY_ACTIVE" -> "Esa mascota ya está alojada en un refugio."
        "SHELTER_PET_NOT_ELIGIBLE" -> "Esa mascota no puede ingresar al refugio."
        "SHELTER_PET_INVALID_TRANSITION" -> "Ese cambio de estado no está permitido."
        "SHELTER_CAPACITY_EXCEEDED" -> "No hay capacidad suficiente en el refugio."
        "SHELTER_VOLUNTEER_NOT_FOUND" -> "No encontramos esa asignación de voluntario."
        "SHELTER_VOLUNTEER_ALREADY_ASSIGNED" -> "Ese voluntario ya tiene una asignación activa con ese rol."
        "SHELTER_VOLUNTEER_INVALID_TRANSITION" -> "Ese cambio de asignación no está permitido."
        "SHELTER_VOLUNTEER_FORBIDDEN" -> "No tenés permiso sobre voluntarios de este refugio."
        "ORGANIZATION_NOT_ELIGIBLE" -> "Esa organización no puede operar un refugio."
        "ORGANIZATION_MEMBERSHIP_REQUIRED" -> "Necesitás ser miembro activo de la organización."
        "PET_NOT_FOUND" -> "No encontramos esa mascota."
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        "FORBIDDEN" -> "No tenés permiso para esta acción."
        "NETWORK" -> "Problema de conexión. Probá de nuevo."
        "TIMEOUT" -> "La operación tardó demasiado. Probá de nuevo."
        "SERIALIZATION" -> "No pudimos interpretar la respuesta."
        else -> "No pudimos completar la operación del refugio."
    }

    fun <T> fail(code: String): Result<T> =
        Result.failure(M11ShelterException(code, userMessage(code)))

    fun <T> failure(t: Throwable): Result<T> {
        val code = codeOf(t)
        return Result.failure(M11ShelterException(code, userMessage(code), t))
    }
}
