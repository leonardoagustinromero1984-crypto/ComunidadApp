package com.comunidapp.app.data.remote.supabase.m15

import com.comunidapp.app.data.remote.supabase.m10.M10FosterException

/**
 * LeoVer M15 — errores tipificados de hogares de tránsito (Bloque 1–2).
 */
class M15Exception(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M15ErrorMapper {

    private val knownCodes = listOf(
        "M15_FOSTER_HOME_NOT_FOUND",
        "M15_FOSTER_HOME_ALREADY_EXISTS",
        "M15_FOSTER_HOME_NOT_ACTIVE",
        "M15_FOSTER_HOME_UNAVAILABLE",
        "M15_FOSTER_HOME_FULL",
        "M15_FOSTER_HOME_INCOMPATIBLE",
        "M15_FOSTER_HOME_CAPACITY_INVALID",
        "M15_FOSTER_REQUEST_NOT_FOUND",
        "M15_FOSTER_REQUEST_ALREADY_EXISTS",
        "M15_FOSTER_REQUEST_NOT_ACTIVE",
        "M15_FOSTER_REQUEST_INVALID_TRANSITION",
        "M15_FOSTER_REQUEST_FORBIDDEN",
        "M15_FOSTER_PLACEMENT_NOT_FOUND",
        "M15_FOSTER_PLACEMENT_ALREADY_ACTIVE",
        "M15_FOSTER_PLACEMENT_CAPACITY_EXCEEDED",
        "M15_FOSTER_PLACEMENT_NOT_ACTIVE",
        "M15_FOSTER_PLACEMENT_INVALID_TRANSITION",
        "M15_INVALID_FOSTER_INPUT",
        "M15_HOME_NOT_FOUND",
        "M15_HOME_ALREADY_EXISTS",
        "M15_HOME_NOT_ACTIVE",
        "M15_CAPACITY_INVALID",
        "M15_AVAILABILITY_CONFLICT",
        "M15_REQUEST_NOT_FOUND",
        "M15_REQUEST_INVALID_TRANSITION",
        "M15_REQUEST_ALREADY_PENDING",
        "M15_PLACEMENT_NOT_FOUND",
        "M15_PLACEMENT_CONFLICT",
        "M15_UNAUTHORIZED",
        "M15_INFRASTRUCTURE_UNAVAILABLE",
        "M15_REMOTE_VALIDATION_PENDING",
        "M15_EVOLUTION_NOT_ALLOWED",
        "M15_EVOLUTION_NOT_FOUND",
        "M15_DISCHARGE_NOT_ALLOWED",
        "M15_DISCHARGE_ALREADY_APPLIED",
        "M15_INVALID_DISCHARGE_REASON",
        "M15_TEMPORARY_CUSTODY_NOT_ALLOWED",
        "M15_EXPENSE_INVALID_AMOUNT",
        "M15_EXPENSE_INVALID_CURRENCY",
        "M15_EXPENSE_NOT_FOUND",
        "M15_HELP_REQUEST_NOT_ALLOWED",
        "M15_HELP_REQUEST_NOT_FOUND",
        "M15_HELP_REQUEST_ALREADY_FINAL",
        "M15_MEDIA_REFERENCE_INVALID",
        "M15_CONFLICT",
        "PET_NOT_ELIGIBLE_FOR_FOSTER",
        "PET_ALREADY_IN_FOSTER",
        "PET_NOT_FOUND",
        "NOT_AUTHENTICATED",
        "FORBIDDEN",
        "NETWORK",
        "TIMEOUT",
        "SERIALIZATION"
    )

    /** M10 persistente → código M15 canónico. */
    fun fromM10Code(m10Code: String): String = when (m10Code) {
        "FOSTER_HOME_NOT_FOUND" -> "M15_FOSTER_HOME_NOT_FOUND"
        "FOSTER_HOME_ALREADY_EXISTS" -> "M15_FOSTER_HOME_ALREADY_EXISTS"
        "FOSTER_HOME_NOT_ACTIVE" -> "M15_FOSTER_HOME_NOT_ACTIVE"
        "FOSTER_HOME_UNAVAILABLE" -> "M15_FOSTER_HOME_UNAVAILABLE"
        "FOSTER_HOME_FULL" -> "M15_FOSTER_HOME_FULL"
        "FOSTER_HOME_INCOMPATIBLE" -> "M15_FOSTER_HOME_INCOMPATIBLE"
        "FOSTER_HOME_CAPACITY_INVALID" -> "M15_FOSTER_HOME_CAPACITY_INVALID"
        "FOSTER_REQUEST_NOT_FOUND" -> "M15_FOSTER_REQUEST_NOT_FOUND"
        "FOSTER_REQUEST_ALREADY_EXISTS" -> "M15_FOSTER_REQUEST_ALREADY_EXISTS"
        "FOSTER_REQUEST_NOT_ACTIVE" -> "M15_FOSTER_REQUEST_NOT_ACTIVE"
        "FOSTER_REQUEST_INVALID_TRANSITION" -> "M15_FOSTER_REQUEST_INVALID_TRANSITION"
        "FOSTER_REQUEST_FORBIDDEN" -> "M15_FOSTER_REQUEST_FORBIDDEN"
        "FOSTER_PLACEMENT_NOT_FOUND" -> "M15_FOSTER_PLACEMENT_NOT_FOUND"
        "FOSTER_PLACEMENT_ALREADY_ACTIVE" -> "M15_FOSTER_PLACEMENT_ALREADY_ACTIVE"
        "FOSTER_PLACEMENT_CAPACITY_EXCEEDED" -> "M15_FOSTER_PLACEMENT_CAPACITY_EXCEEDED"
        "FOSTER_PLACEMENT_NOT_ACTIVE" -> "M15_FOSTER_PLACEMENT_NOT_ACTIVE"
        "FOSTER_PLACEMENT_INVALID_TRANSITION" -> "M15_FOSTER_PLACEMENT_INVALID_TRANSITION"
        "FORBIDDEN" -> "M15_UNAUTHORIZED"
        "FOSTER_EVOLUTION_FORBIDDEN" -> "M15_EVOLUTION_NOT_ALLOWED"
        "FOSTER_EVOLUTION_NOT_FOUND" -> "M15_EVOLUTION_NOT_FOUND"
        "FOSTER_EVOLUTION_INVALID_MEDIA_REF" -> "M15_MEDIA_REFERENCE_INVALID"
        "FOSTER_EXPENSE_INVALID_AMOUNT" -> "M15_EXPENSE_INVALID_AMOUNT"
        "FOSTER_EXPENSE_NOT_FOUND" -> "M15_EXPENSE_NOT_FOUND"
        "FOSTER_HELP_REQUEST_NOT_FOUND" -> "M15_HELP_REQUEST_NOT_FOUND"
        "FOSTER_HELP_REQUEST_NOT_EDITABLE" -> "M15_HELP_REQUEST_ALREADY_FINAL"
        "FOSTER_PLACEMENT_COMPLETION_FORBIDDEN" -> "M15_DISCHARGE_NOT_ALLOWED"
        "FOSTER_TEMPORARY_PERMISSION_REVOKE_FAILED" -> "M15_TEMPORARY_CUSTODY_NOT_ALLOWED"
        "NOT_AUTHENTICATED" -> "NOT_AUTHENTICATED"
        "NETWORK", "TIMEOUT" -> "M15_INFRASTRUCTURE_UNAVAILABLE"
        "SERIALIZATION" -> "M15_INFRASTRUCTURE_UNAVAILABLE"
        else -> m10Code
    }

    fun codeOf(throwable: Throwable): String {
        if (throwable is M15Exception) return throwable.code
        if (throwable is M10FosterException) return fromM10Code(throwable.code)
        val raw = buildString {
            append(throwable.message.orEmpty())
            append(' ')
            append(throwable.cause?.message.orEmpty())
        }
        knownCodes.forEach { code ->
            if (raw.contains(code, ignoreCase = true)) return code
        }
        // Detect M10 codes embedded in messages
        listOf(
            "FOSTER_HOME_NOT_FOUND",
            "FOSTER_REQUEST_NOT_FOUND",
            "FOSTER_PLACEMENT_NOT_FOUND",
            "FORBIDDEN",
            "NOT_AUTHENTICATED"
        ).forEach { m10 ->
            if (raw.contains(m10, ignoreCase = true)) return fromM10Code(m10)
        }
        val lower = raw.lowercase()
        return when {
            "timeout" in lower || "timed out" in lower -> "M15_INFRASTRUCTURE_UNAVAILABLE"
            "unable to resolve host" in lower || "failed to connect" in lower ||
                "network" in lower || "unreachable" in lower -> "M15_INFRASTRUCTURE_UNAVAILABLE"
            "json" in lower || "serializ" in lower || "decode" in lower ->
                "M15_INFRASTRUCTURE_UNAVAILABLE"
            else -> "UNKNOWN"
        }
    }

    fun userMessage(code: String): String = when (code) {
        "M15_FOSTER_HOME_NOT_FOUND", "M15_HOME_NOT_FOUND" ->
            "No encontramos ese hogar de tránsito."
        "M15_FOSTER_HOME_ALREADY_EXISTS", "M15_HOME_ALREADY_EXISTS" ->
            "Ya tenés un perfil de hogar de tránsito activo."
        "M15_FOSTER_HOME_NOT_ACTIVE", "M15_HOME_NOT_ACTIVE" ->
            "Ese hogar no está activo para recibir solicitudes."
        "M15_FOSTER_HOME_UNAVAILABLE", "M15_AVAILABILITY_CONFLICT" ->
            "Ese hogar no está disponible ahora."
        "M15_FOSTER_HOME_FULL" -> "Ese hogar no tiene capacidad libre."
        "M15_FOSTER_HOME_INCOMPATIBLE" -> "La mascota no es compatible con este hogar."
        "M15_FOSTER_HOME_CAPACITY_INVALID", "M15_CAPACITY_INVALID" ->
            "La capacidad debe ser mayor que cero."
        "M15_FOSTER_REQUEST_NOT_FOUND", "M15_REQUEST_NOT_FOUND" ->
            "No encontramos esa solicitud."
        "M15_FOSTER_REQUEST_ALREADY_EXISTS", "M15_REQUEST_ALREADY_PENDING" ->
            "Ya hay una solicitud activa para esa mascota en este hogar."
        "M15_FOSTER_REQUEST_NOT_ACTIVE" -> "Esta solicitud ya no está activa."
        "M15_FOSTER_REQUEST_INVALID_TRANSITION", "M15_REQUEST_INVALID_TRANSITION" ->
            "Ese cambio de estado no está permitido."
        "M15_FOSTER_REQUEST_FORBIDDEN", "M15_UNAUTHORIZED", "FORBIDDEN" ->
            "No tenés permiso para esta acción."
        "M15_FOSTER_PLACEMENT_NOT_FOUND", "M15_PLACEMENT_NOT_FOUND" ->
            "No encontramos ese alojamiento."
        "M15_FOSTER_PLACEMENT_ALREADY_ACTIVE", "M15_PLACEMENT_CONFLICT" ->
            "La mascota ya tiene un alojamiento activo."
        "M15_FOSTER_PLACEMENT_CAPACITY_EXCEEDED" ->
            "No hay capacidad para iniciar el ingreso."
        "M15_FOSTER_PLACEMENT_NOT_ACTIVE" -> "El alojamiento no está activo."
        "M15_FOSTER_PLACEMENT_INVALID_TRANSITION" ->
            "Ese cambio de estado del alojamiento no está permitido."
        "M15_INVALID_FOSTER_INPUT" -> "Revisá los datos ingresados."
        "M15_INFRASTRUCTURE_UNAVAILABLE" ->
            "El servicio no está disponible. Intentá más tarde."
        "M15_REMOTE_VALIDATION_PENDING" ->
            "La validación remota está pendiente."
        "M15_EVOLUTION_NOT_ALLOWED" -> "No se puede registrar evolución en este alojamiento."
        "M15_EVOLUTION_NOT_FOUND" -> "No encontramos ese registro de evolución."
        "M15_DISCHARGE_NOT_ALLOWED" -> "No se puede egresar este alojamiento ahora."
        "M15_DISCHARGE_ALREADY_APPLIED" -> "Este alojamiento ya fue finalizado."
        "M15_INVALID_DISCHARGE_REASON" -> "El motivo de egreso no es válido."
        "M15_TEMPORARY_CUSTODY_NOT_ALLOWED" -> "No se pudo revocar la custodia temporal."
        "M15_EXPENSE_INVALID_AMOUNT" -> "El importe del gasto debe ser mayor que cero."
        "M15_EXPENSE_INVALID_CURRENCY" -> "La moneda no es válida."
        "M15_EXPENSE_NOT_FOUND" -> "No encontramos ese gasto."
        "M15_HELP_REQUEST_NOT_ALLOWED" -> "No se puede crear ese pedido de ayuda."
        "M15_HELP_REQUEST_NOT_FOUND" -> "No encontramos ese pedido de ayuda."
        "M15_HELP_REQUEST_ALREADY_FINAL" -> "Ese pedido de ayuda ya está cerrado."
        "M15_MEDIA_REFERENCE_INVALID" -> "La referencia de media no es segura."
        "M15_CONFLICT" -> "Conflicto al actualizar el alojamiento."
        "PET_NOT_ELIGIBLE_FOR_FOSTER" -> "Esa mascota no puede ir a tránsito."
        "PET_ALREADY_IN_FOSTER" -> "Esa mascota ya está en un hogar de tránsito."
        "PET_NOT_FOUND" -> "No encontramos la mascota."
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        else -> "No se pudo completar la operación."
    }

    fun <T> failure(throwable: Throwable): Result<T> {
        val code = codeOf(throwable)
        return Result.failure(M15Exception(code, userMessage(code), throwable))
    }

    fun <T> fail(code: String): Result<T> =
        Result.failure(M15Exception(code, userMessage(code)))
}
