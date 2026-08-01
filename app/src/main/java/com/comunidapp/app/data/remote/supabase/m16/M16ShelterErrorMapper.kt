package com.comunidapp.app.data.remote.supabase.m16

class M16Exception(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M16ShelterErrorMapper {

    private val knownCodes = listOf(
        "M16_SHELTER_NOT_FOUND",
        "M16_ORGANIZATION_NOT_ELIGIBLE",
        "M16_PROFILE_ALREADY_EXISTS",
        "M16_PERMISSION_DENIED",
        "M16_INVALID_CAPACITY",
        "M16_OCCUPANCY_EXCEEDS_CAPACITY",
        "M16_INVALID_OPENING_HOURS",
        "M16_INVALID_PUBLIC_CONTACT",
        "M16_INVALID_STATE_TRANSITION",
        "M16_STATE_ALREADY_FINAL",
        "M16_VERIFICATION_MANAGED_EXTERNALLY",
        "M16_REMOTE_VALIDATION_PENDING",
        "M16_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE",
        "M16_INVALID_SHELTER_INPUT",
        "NOT_AUTHENTICATED"
    )

    fun userMessage(code: String): String = when (code) {
        "M16_SHELTER_NOT_FOUND" -> "No encontramos ese refugio."
        "M16_ORGANIZATION_NOT_ELIGIBLE" -> "Esta organización no puede crear un perfil de refugio."
        "M16_PROFILE_ALREADY_EXISTS" -> "Ya existe un perfil de refugio para esta organización."
        "M16_PERMISSION_DENIED" -> "No tenés permiso para esta acción."
        "M16_INVALID_CAPACITY" -> "La capacidad declarada no es válida."
        "M16_OCCUPANCY_EXCEEDS_CAPACITY" -> "La ocupación supera la capacidad declarada."
        "M16_INVALID_OPENING_HOURS" -> "Los horarios de atención no son válidos."
        "M16_INVALID_PUBLIC_CONTACT" -> "El contacto público no es válido."
        "M16_INVALID_STATE_TRANSITION" -> "Ese cambio de estado no está permitido."
        "M16_STATE_ALREADY_FINAL" -> "Este refugio ya está cerrado permanentemente."
        "M16_VERIFICATION_MANAGED_EXTERNALLY" ->
            "La verificación se gestiona desde moderación."
        "M16_REMOTE_VALIDATION_PENDING" -> "La validación remota está pendiente."
        "M16_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE" ->
            "Las notificaciones no están disponibles para refugios."
        "M16_INVALID_SHELTER_INPUT" -> "Revisá los datos ingresados."
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        else -> "No se pudo completar la operación."
    }

    fun codeOf(throwable: Throwable): String {
        if (throwable is M16Exception) return throwable.code
        knownCodes.forEach { code ->
            if (throwable.message?.contains(code, ignoreCase = true) == true) return code
        }
        return "UNKNOWN"
    }

    fun <T> failure(throwable: Throwable): Result<T> {
        val code = codeOf(throwable)
        return Result.failure(M16Exception(code, userMessage(code), throwable))
    }

    fun <T> fail(code: String): Result<T> =
        Result.failure(M16Exception(code, userMessage(code)))
}
