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

    // Specific codes first: matching is substring-based, so codes that embed a
    // generic token (e.g. *_FORBIDDEN) must be checked before the generic one.
    private val knownCodes = listOf(
        "PET_OWNER_ID_DIRECT_FORBIDDEN",
        "PET_PRINCIPAL_REVOKE_FORBIDDEN",
        "PET_TRANSFER_EXPIRE_CLIENT_FORBIDDEN",
        "PET_TRANSFER_FORBIDDEN",
        "PET_TRANSFER_NOT_FOUND",
        "PET_TRANSFER_NOT_PENDING",
        "PET_TRANSFER_PENDING_EXISTS",
        "PET_TRANSFER_EXPIRES_INVALID",
        "PET_TRANSFER_EXPIRED",
        "PET_TRANSFER_DEST_XOR_REQUIRED",
        "PET_TRANSFER_SAME_PRINCIPAL",
        "PET_DECEASED_NOT_TRANSFERABLE",
        "PET_ARCHIVED_NOT_TRANSFERABLE",
        "PET_PRINCIPAL_USE_TRANSFER",
        "PET_PRINCIPAL_MISSING",
        "PET_RESPONSIBILITY_DUPLICATE_ACTIVE",
        "PET_RESPONSIBILITY_NOT_ACTIVE",
        "PET_RESPONSIBILITY_NOT_FOUND",
        "PET_TEMP_CUSTODY_ENDS_REQUIRED",
        "PET_ACTOR_XOR_REQUIRED",
        "PET_ROLE_INVALID",
        "PET_AUTH_CAPABILITIES_INVALID",
        "PET_AUTH_PERSON_REQUIRED",
        "PET_AUTHORIZATION_NOT_ACTIVE",
        "PET_AUTHORIZATION_NOT_FOUND",
        "NOT_AUTHENTICATED",
        "FORBIDDEN",
        "PET_NOT_FOUND",
        "PET_NOT_ACTIVE",
        "PET_NAME_REQUIRED",
        "PET_MICROCHIP_ACTIVE_CONFLICT",
        "PET_STATUS_FILTER_INVALID",
        "PET_CREATE_PARTIAL",
        "M08_FEATURE_UNAVAILABLE",
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
        "PET_PRINCIPAL_REVOKE_FORBIDDEN" ->
            "No se puede revocar al responsable principal."
        "PET_PRINCIPAL_USE_TRANSFER" ->
            "Para cambiar el responsable principal usá una transferencia."
        "PET_PRINCIPAL_MISSING" ->
            "La mascota no tiene responsable principal activo."
        "PET_RESPONSIBILITY_NOT_FOUND" -> "No encontramos ese vínculo de responsabilidad."
        "PET_RESPONSIBILITY_NOT_ACTIVE" -> "Ese vínculo de responsabilidad ya no está activo."
        "PET_RESPONSIBILITY_DUPLICATE_ACTIVE" ->
            "Esa persona u organización ya tiene ese rol activo para la mascota."
        "PET_TEMP_CUSTODY_ENDS_REQUIRED" ->
            "La custodia temporal necesita una fecha de fin."
        "PET_ACTOR_XOR_REQUIRED" ->
            "Elegí una persona o una organización (no ambas)."
        "PET_ROLE_INVALID" -> "El rol de responsabilidad no es válido."
        "PET_AUTH_PERSON_REQUIRED" -> "La autorización requiere una persona destinataria."
        "PET_AUTH_CAPABILITIES_INVALID" ->
            "Alguna de las capacidades seleccionadas no se puede otorgar."
        "PET_AUTHORIZATION_NOT_FOUND" -> "No encontramos esa autorización."
        "PET_AUTHORIZATION_NOT_ACTIVE" -> "La autorización ya no está activa."
        "PET_TRANSFER_NOT_FOUND" -> "No encontramos esa transferencia."
        "PET_TRANSFER_NOT_PENDING" ->
            "La transferencia ya no está pendiente y no admite cambios."
        "PET_TRANSFER_PENDING_EXISTS" ->
            "Ya hay una transferencia pendiente para esta mascota."
        "PET_TRANSFER_EXPIRED" -> "La transferencia venció."
        "PET_TRANSFER_EXPIRES_INVALID" -> "La fecha de vencimiento no es válida."
        "PET_TRANSFER_FORBIDDEN" -> "No tenés permiso sobre esta transferencia."
        "PET_TRANSFER_EXPIRE_CLIENT_FORBIDDEN" ->
            "El vencimiento lo administra el servidor."
        "PET_TRANSFER_DEST_XOR_REQUIRED" ->
            "Elegí una persona o una organización de destino (no ambas)."
        "PET_TRANSFER_SAME_PRINCIPAL" ->
            "El destino elegido ya es el responsable principal."
        "PET_DECEASED_NOT_TRANSFERABLE" ->
            "No se puede transferir una mascota fallecida."
        "PET_ARCHIVED_NOT_TRANSFERABLE" ->
            "No se puede transferir una mascota archivada."
        "M08_FEATURE_UNAVAILABLE" ->
            "Esta función no está disponible en este entorno."
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
