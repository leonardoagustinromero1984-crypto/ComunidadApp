package com.comunidapp.app.data.remote.supabase.m18

class M18Exception(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M18EventErrorMapper {

    private val knownCodes = listOf(
        "M18_EVENT_NOT_FOUND",
        "M18_ORGANIZATION_NOT_ELIGIBLE",
        "M18_PERMISSION_DENIED",
        "M18_INVALID_TITLE",
        "M18_INVALID_DESCRIPTION",
        "M18_INVALID_CAPACITY",
        "M18_CAPACITY_BELOW_REGISTERED",
        "M18_INVALID_DATE_RANGE",
        "M18_INVALID_CHECKIN_WINDOW",
        "M18_INVALID_STATE_TRANSITION",
        "M18_STATE_ALREADY_FINAL",
        "M18_EVENT_NOT_PUBLIC",
        "M18_EVENT_NOT_OPEN",
        "M18_EVENT_TERMINAL",
        "M18_EVENT_FULL",
        "M18_REGISTRATION_NOT_FOUND",
        "M18_DUPLICATE_REGISTRATION",
        "M18_INVALID_CHECKIN_STATE",
        "M18_CHECKIN_WINDOW_CLOSED",
        "M18_REMINDER_ALREADY_SCHEDULED",
        "M18_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE",
        "NOT_AUTHENTICATED"
    )

    fun userMessage(code: String): String = when (code) {
        "M18_EVENT_NOT_FOUND" -> "No encontramos ese evento."
        "M18_ORGANIZATION_NOT_ELIGIBLE" -> "Esta organización no puede crear eventos."
        "M18_PERMISSION_DENIED" -> "No tenés permiso para esta acción."
        "M18_INVALID_TITLE" -> "El título no es válido."
        "M18_INVALID_DESCRIPTION" -> "La descripción no es válida."
        "M18_INVALID_CAPACITY" -> "El cupo debe ser mayor a cero."
        "M18_CAPACITY_BELOW_REGISTERED" -> "El cupo no puede ser menor que las inscripciones confirmadas."
        "M18_INVALID_DATE_RANGE" -> "La fecha de fin debe ser posterior al inicio."
        "M18_INVALID_CHECKIN_WINDOW" -> "La ventana de check-in no es válida."
        "M18_INVALID_STATE_TRANSITION" -> "Ese cambio de estado no está permitido."
        "M18_STATE_ALREADY_FINAL" -> "Este evento ya está cerrado."
        "M18_EVENT_NOT_PUBLIC" -> "Este evento no está publicado."
        "M18_EVENT_NOT_OPEN" -> "Las inscripciones no están abiertas."
        "M18_EVENT_TERMINAL" -> "Este evento ya finalizó."
        "M18_EVENT_FULL" -> "No hay cupos disponibles."
        "M18_REGISTRATION_NOT_FOUND" -> "No encontramos esa inscripción."
        "M18_DUPLICATE_REGISTRATION" -> "Ya estás inscripto en este evento."
        "M18_INVALID_CHECKIN_STATE" -> "Esta inscripción no puede hacer check-in."
        "M18_CHECKIN_WINDOW_CLOSED" -> "La ventana de check-in está cerrada."
        "M18_REMINDER_ALREADY_SCHEDULED" -> "El recordatorio ya está programado."
        "M18_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE" ->
            "Las notificaciones no están disponibles para eventos."
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        else -> "No se pudo completar la operación."
    }

    fun codeOf(throwable: Throwable): String {
        if (throwable is M18Exception) return throwable.code
        knownCodes.forEach { code ->
            if (throwable.message?.contains(code, ignoreCase = true) == true) return code
        }
        return "UNKNOWN"
    }

    fun <T> failure(throwable: Throwable): Result<T> {
        val code = codeOf(throwable)
        return Result.failure(M18Exception(code, userMessage(code), throwable))
    }

    fun <T> fail(code: String): Result<T> =
        Result.failure(M18Exception(code, userMessage(code)))
}
