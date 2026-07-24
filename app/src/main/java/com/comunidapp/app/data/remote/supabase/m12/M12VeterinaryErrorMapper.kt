package com.comunidapp.app.data.remote.supabase.m12

/**
 * LeoVer M12 — errores tipados de veterinarias (Bloque 1).
 * No expone mensajes técnicos de Supabase al usuario.
 */
class M12VeterinaryException(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M12VeterinaryErrorMapper {

    private val knownCodes = listOf(
        "VETERINARY_CLINIC_NOT_FOUND",
        "VETERINARY_CLINIC_FORBIDDEN",
        "VETERINARY_CLINIC_INVALID",
        "VETERINARY_CLINIC_INACTIVE",
        "VETERINARY_CLINIC_UNVERIFIED",
        "VETERINARY_PROFESSIONAL_NOT_FOUND",
        "VETERINARY_PROFESSIONAL_INVALID",
        "VETERINARY_PROFESSIONAL_UNVERIFIED",
        "VETERINARY_SERVICE_NOT_FOUND",
        "VETERINARY_OPENING_HOURS_INVALID",
        "VETERINARY_PUBLIC_CONTACT_DISABLED",
        "VETERINARY_MEDIA_REF_INVALID",
        "VETERINARY_REPOSITORY_FAILURE",
        "ORGANIZATION_NOT_ELIGIBLE",
        "ORGANIZATION_MEMBERSHIP_REQUIRED",
        "NOT_AUTHENTICATED",
        "FORBIDDEN",
        "NETWORK",
        "TIMEOUT",
        "SERIALIZATION"
    )

    fun codeOf(throwable: Throwable): String {
        if (throwable is M12VeterinaryException) return throwable.code
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
            else -> "VETERINARY_REPOSITORY_FAILURE"
        }
    }

    fun userMessage(code: String): String = when (code) {
        "VETERINARY_CLINIC_NOT_FOUND" -> "No encontramos esa veterinaria."
        "VETERINARY_CLINIC_FORBIDDEN" -> "No tenés permiso para gestionar esta veterinaria."
        "VETERINARY_CLINIC_INVALID" -> "Los datos de la veterinaria no son válidos."
        "VETERINARY_CLINIC_INACTIVE" -> "Esa veterinaria no está activa."
        "VETERINARY_CLINIC_UNVERIFIED" -> "Esa veterinaria todavía no está verificada."
        "VETERINARY_PROFESSIONAL_NOT_FOUND" -> "No encontramos ese profesional."
        "VETERINARY_PROFESSIONAL_INVALID" -> "Los datos del profesional no son válidos."
        "VETERINARY_PROFESSIONAL_UNVERIFIED" -> "Ese profesional todavía no está verificado."
        "VETERINARY_SERVICE_NOT_FOUND" -> "No encontramos ese servicio."
        "VETERINARY_OPENING_HOURS_INVALID" -> "Los horarios no son válidos."
        "VETERINARY_PUBLIC_CONTACT_DISABLED" -> "El contacto público no está habilitado."
        "VETERINARY_MEDIA_REF_INVALID" -> "La referencia de media no es válida."
        "VETERINARY_REPOSITORY_FAILURE" -> "No pudimos completar la operación de veterinarias."
        "ORGANIZATION_NOT_ELIGIBLE" -> "Esa organización no puede operar una veterinaria."
        "ORGANIZATION_MEMBERSHIP_REQUIRED" -> "Necesitás ser miembro activo de la organización."
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        "FORBIDDEN" -> "No tenés permiso para esta acción."
        "NETWORK" -> "Problema de conexión. Probá de nuevo."
        "TIMEOUT" -> "La operación tardó demasiado. Probá de nuevo."
        "SERIALIZATION" -> "No pudimos interpretar la respuesta."
        else -> "No pudimos completar la operación de veterinarias."
    }

    fun <T> fail(code: String): Result<T> =
        Result.failure(M12VeterinaryException(code, userMessage(code)))

    fun <T> failure(t: Throwable): Result<T> {
        val code = codeOf(t)
        return Result.failure(M12VeterinaryException(code, userMessage(code), t))
    }
}
