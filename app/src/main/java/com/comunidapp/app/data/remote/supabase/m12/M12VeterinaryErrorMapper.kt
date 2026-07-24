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
        // M12 Bloque 3 — agenda / turnos (specific before generic parents)
        "VETERINARY_SCHEDULE_SETTINGS_INVALID",
        "VETERINARY_TIMEZONE_INVALID",
        "VETERINARY_AVAILABILITY_RULE_OVERLAP",
        "VETERINARY_AVAILABILITY_RULE_INVALID",
        "VETERINARY_AVAILABILITY_EXCEPTION_INVALID",
        "VETERINARY_SLOT_CAPACITY_EXHAUSTED",
        "VETERINARY_SLOT_NOT_AVAILABLE",
        "VETERINARY_APPOINTMENT_NOT_FOUND",
        "VETERINARY_APPOINTMENT_FORBIDDEN",
        "VETERINARY_APPOINTMENT_PET_FORBIDDEN",
        "VETERINARY_APPOINTMENT_INVALID_TRANSITION",
        "VETERINARY_APPOINTMENT_PAST_SLOT",
        "VETERINARY_APPOINTMENT_CANCELLATION_WINDOW",
        "VETERINARY_APPOINTMENT_ALREADY_FINAL",
        // M12 Bloque 4 — recordatorios, métricas y hardening (específicos antes que genéricos)
        "VETERINARY_REMINDER_NOT_ELIGIBLE",
        "VETERINARY_REMINDER_ALREADY_SCHEDULED",
        "VETERINARY_REMINDER_INFRASTRUCTURE_UNAVAILABLE",
        "VETERINARY_APPOINTMENT_RETRY_CONFLICT",
        "VETERINARY_APPOINTMENT_TIMEZONE_CHANGED",
        "VETERINARY_APPOINTMENT_SERVICE_INACTIVE",
        "VETERINARY_APPOINTMENT_PROFESSIONAL_INACTIVE",
        "VETERINARY_APPOINTMENT_METRICS_INVALID_RANGE",
        "VETERINARY_APPOINTMENT_EXPIRED",
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
        "VETERINARY_SCHEDULE_SETTINGS_INVALID" -> "La configuración de agenda no es válida."
        "VETERINARY_TIMEZONE_INVALID" -> "La zona horaria no es válida."
        "VETERINARY_AVAILABILITY_RULE_INVALID" -> "La regla de disponibilidad no es válida."
        "VETERINARY_AVAILABILITY_RULE_OVERLAP" -> "Esa regla se solapa con otra existente."
        "VETERINARY_AVAILABILITY_EXCEPTION_INVALID" -> "La excepción de disponibilidad no es válida."
        "VETERINARY_SLOT_NOT_AVAILABLE" -> "Ese horario ya no está disponible."
        "VETERINARY_SLOT_CAPACITY_EXHAUSTED" -> "No quedan cupos en ese horario."
        "VETERINARY_APPOINTMENT_NOT_FOUND" -> "No encontramos ese turno."
        "VETERINARY_APPOINTMENT_FORBIDDEN" -> "No tenés permiso para ese turno."
        "VETERINARY_APPOINTMENT_PET_FORBIDDEN" ->
            "No tenés autoridad sobre esa mascota para solicitar el turno."
        "VETERINARY_APPOINTMENT_INVALID_TRANSITION" -> "No se puede cambiar el estado de ese turno así."
        "VETERINARY_APPOINTMENT_PAST_SLOT" -> "No se puede operar sobre un horario pasado."
        "VETERINARY_APPOINTMENT_CANCELLATION_WINDOW" ->
            "Ya pasó el plazo para cancelar ese turno."
        "VETERINARY_APPOINTMENT_ALREADY_FINAL" -> "Ese turno ya está finalizado."
        "VETERINARY_REMINDER_NOT_ELIGIBLE" ->
            "Ese turno no admite recordatorios en este estado."
        "VETERINARY_REMINDER_ALREADY_SCHEDULED" -> "Ese recordatorio ya estaba programado."
        "VETERINARY_REMINDER_INFRASTRUCTURE_UNAVAILABLE" ->
            "El envío de recordatorios no está disponible por ahora."
        "VETERINARY_APPOINTMENT_RETRY_CONFLICT" ->
            "El turno cambió de estado. Actualizá y volvé a intentar."
        "VETERINARY_APPOINTMENT_TIMEZONE_CHANGED" ->
            "Cambió la zona horaria de la agenda. Revisá los horarios."
        "VETERINARY_APPOINTMENT_SERVICE_INACTIVE" ->
            "Ese servicio no está activo para confirmar el turno."
        "VETERINARY_APPOINTMENT_PROFESSIONAL_INACTIVE" ->
            "Ese profesional no está activo para el turno."
        "VETERINARY_APPOINTMENT_METRICS_INVALID_RANGE" ->
            "El rango de fechas para las métricas no es válido."
        "VETERINARY_APPOINTMENT_EXPIRED" -> "Ese turno venció."
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
