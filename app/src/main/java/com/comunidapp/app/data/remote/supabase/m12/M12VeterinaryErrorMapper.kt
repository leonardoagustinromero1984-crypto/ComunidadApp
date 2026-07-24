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

    // Ordered so more specific codes match before their substrings in [codeOf]
    // (e.g. VETERINARY_CLINIC_INVALID_TRANSITION before VETERINARY_CLINIC_INVALID).
    private val knownCodes = listOf(
        "VETERINARY_CLINIC_NOT_FOUND",
        "VETERINARY_CLINIC_FORBIDDEN",
        "VETERINARY_CLINIC_INACTIVE",
        "VETERINARY_CLINIC_UNVERIFIED",
        "VETERINARY_CLINIC_ALREADY_EXISTS",
        "VETERINARY_CLINIC_INVALID_TRANSITION",
        "VETERINARY_CLINIC_ACTIVATION_REQUIREMENTS",
        "VETERINARY_CLINIC_INVALID",
        "VETERINARY_ORGANIZATION_REQUIRED",
        "VETERINARY_BRANCH_INVALID",
        "VETERINARY_VERIFICATION_FORBIDDEN",
        "VETERINARY_VERIFICATION_INVALID_TRANSITION",
        "VETERINARY_PROFESSIONAL_NOT_FOUND",
        "VETERINARY_PROFESSIONAL_ALREADY_LINKED",
        "VETERINARY_PROFESSIONAL_NOT_LINKED",
        "VETERINARY_PROFESSIONAL_UNVERIFIED",
        "VETERINARY_PROFESSIONAL_INVALID",
        "VETERINARY_SPECIALTY_INVALID",
        "VETERINARY_SERVICE_NOT_FOUND",
        "VETERINARY_SERVICE_DUPLICATE",
        "VETERINARY_SERVICE_INVALID",
        "VETERINARY_OPENING_HOURS_INCOMPLETE",
        "VETERINARY_OPENING_HOURS_DUPLICATE_DAY",
        "VETERINARY_OPENING_HOURS_INVALID",
        "VETERINARY_PUBLIC_CONTACT_DISABLED",
        "VETERINARY_PUBLIC_PROJECTION_FORBIDDEN",
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
        "VETERINARY_CLINIC_ALREADY_EXISTS" ->
            "Ya existe una veterinaria activa para esa organización y sede."
        "VETERINARY_CLINIC_INVALID_TRANSITION" -> "No se puede cambiar el estado de esa forma."
        "VETERINARY_CLINIC_ACTIVATION_REQUIREMENTS" ->
            "Para activar necesitás zona pública, horarios (o 24 h) y al menos un servicio activo."
        "VETERINARY_ORGANIZATION_REQUIRED" -> "Tenés que indicar la organización."
        "VETERINARY_BRANCH_INVALID" -> "La sede no pertenece a esa organización."
        "VETERINARY_VERIFICATION_FORBIDDEN" -> "No tenés permiso para revisar verificaciones."
        "VETERINARY_VERIFICATION_INVALID_TRANSITION" ->
            "No se puede cambiar la verificación de esa forma."
        "VETERINARY_PROFESSIONAL_NOT_FOUND" -> "No encontramos ese profesional."
        "VETERINARY_PROFESSIONAL_INVALID" -> "Los datos del profesional no son válidos."
        "VETERINARY_PROFESSIONAL_UNVERIFIED" -> "Ese profesional todavía no está verificado."
        "VETERINARY_PROFESSIONAL_ALREADY_LINKED" ->
            "Ese profesional ya está vinculado a la veterinaria."
        "VETERINARY_PROFESSIONAL_NOT_LINKED" ->
            "Ese profesional no está vinculado a la veterinaria."
        "VETERINARY_SPECIALTY_INVALID" -> "Alguna especialidad no es válida."
        "VETERINARY_SERVICE_NOT_FOUND" -> "No encontramos ese servicio."
        "VETERINARY_SERVICE_DUPLICATE" -> "Ya existe un servicio activo con ese nombre y categoría."
        "VETERINARY_SERVICE_INVALID" -> "Los datos del servicio no son válidos."
        "VETERINARY_OPENING_HOURS_INVALID" -> "Los horarios no son válidos."
        "VETERINARY_OPENING_HOURS_INCOMPLETE" -> "Faltan horarios de apertura o cierre."
        "VETERINARY_OPENING_HOURS_DUPLICATE_DAY" -> "Hay un día repetido en los horarios."
        "VETERINARY_PUBLIC_CONTACT_DISABLED" -> "El contacto público no está habilitado."
        "VETERINARY_PUBLIC_PROJECTION_FORBIDDEN" -> "No se puede mostrar esa información pública."
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
