package com.comunidapp.app.data.remote.supabase.m08

/**
 * LeoVer M08 — typed pet operation failure with stable domain code.
 */
class M08PetException(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Create succeeded for the pet row but a secondary stage (profile/health/avatar) failed.
 * Do not delete the pet; caller may retry the failed stage.
 */
class PetCreatePartialException(
    val petId: String,
    val failedStage: String,
    cause: Throwable? = null
) : Exception(
    "PET_CREATE_PARTIAL:$failedStage:$petId",
    cause
)

object M08PetErrorMapper {

    private val knownCodes = listOf(
        "NOT_AUTHENTICATED",
        "FORBIDDEN",
        "PET_NOT_FOUND",
        "PET_NOT_ACTIVE",
        "PET_NAME_REQUIRED",
        "PET_MICROCHIP_ACTIVE_CONFLICT",
        "PET_OWNER_ID_DIRECT_FORBIDDEN",
        "PET_STATUS_FILTER_INVALID",
        "PET_TRANSFER_NOT_FOUND",
        "PET_TRANSFER_NOT_PENDING",
        "PET_TRANSFER_EXPIRED",
        "PET_TRANSFER_FORBIDDEN",
        "PET_RESPONSIBILITY_NOT_FOUND",
        "PET_AUTHORIZATION_NOT_FOUND",
        "PET_CREATE_PARTIAL",
        "NETWORK",
        "TIMEOUT",
        "SERIALIZATION"
    )

    fun codeOf(throwable: Throwable): String {
        if (throwable is M08PetException) return throwable.code
        if (throwable is PetCreatePartialException) return "PET_CREATE_PARTIAL"
        val raw = sequenceOf(throwable.message, throwable.cause?.message)
            .filterNotNull()
            .joinToString(" ")
        val upper = raw.uppercase()
        knownCodes.firstOrNull { upper.contains(it) }?.let { return it }
        return when {
            upper.contains("JWT") || upper.contains("AUTH") || upper.contains("401") ->
                "NOT_AUTHENTICATED"
            upper.contains("403") || upper.contains("PERMISSION") || upper.contains("RLS") ->
                "FORBIDDEN"
            upper.contains("TIMEOUT") || upper.contains("TIMED OUT") -> "TIMEOUT"
            upper.contains("UNABLE TO CONNECT") ||
                upper.contains("NETWORK") ||
                upper.contains("UNKNOWNHOST") ||
                upper.contains("RESOLVE HOST") ||
                upper.contains("FAILED TO CONNECT") -> "NETWORK"
            upper.contains("SERIALIZ") || upper.contains("JSON") || upper.contains("DECODE") ->
                "SERIALIZATION"
            else -> "UNKNOWN"
        }
    }

    fun userMessage(code: String): String = when (code) {
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión para continuar."
        "FORBIDDEN" -> "No tenés permiso para esta acción."
        "PET_NOT_FOUND" -> "No encontramos esa mascota."
        "PET_NOT_ACTIVE" -> "La mascota no está activa."
        "PET_NAME_REQUIRED" -> "El nombre de la mascota es obligatorio."
        "PET_MICROCHIP_ACTIVE_CONFLICT" ->
            "Ya hay otra mascota activa con ese microchip."
        "PET_OWNER_ID_DIRECT_FORBIDDEN" ->
            "No se puede asignar el dueño directamente."
        "PET_CREATE_PARTIAL" ->
            "La mascota se creó, pero no se pudo completar un paso. Reintentá."
        "NETWORK" -> "Problema de conexión. Intentá de nuevo."
        "TIMEOUT" -> "La operación tardó demasiado. Intentá de nuevo."
        "SERIALIZATION" -> "No se pudo interpretar la respuesta del servidor."
        else -> "No se pudo completar la operación de mascota."
    }

    fun toException(throwable: Throwable): M08PetException {
        if (throwable is M08PetException) return throwable
        val code = codeOf(throwable)
        return M08PetException(code, userMessage(code), throwable)
    }

    fun <T> failure(throwable: Throwable): Result<T> =
        Result.failure(toException(throwable))

    fun <T> failureCode(code: String, cause: Throwable? = null): Result<T> =
        Result.failure(M08PetException(code, userMessage(code), cause))
}
