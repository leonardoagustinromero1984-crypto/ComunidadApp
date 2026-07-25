package com.comunidapp.app.data.remote.supabase.m14

/**
 * LeoVer M14 — errores tipificados (Bloque 1 local; sin cliente remoto).
 */
class M14Exception(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M14ErrorMapper {
    fun userMessage(code: String): String = when (code) {
        "NOT_AUTHENTICATED" -> "Debés iniciar sesión."
        "PET_NOT_FOUND" -> "No encontramos esa mascota."
        "PASSPORT_NOT_FOUND" -> "No encontramos ese pasaporte."
        "PASSPORT_ALREADY_EXISTS" -> "Ya existe un pasaporte activo para esta mascota."
        "INVALID_PASSPORT_STATUS" -> "El estado del pasaporte no permite esta acción."
        "INVALID_TRANSITION" -> "Esa transición de estado no está permitida."
        "UNAUTHORIZED" -> "No tenés autorización para esta acción."
        "INVALID_CREDENTIAL" -> "Revisá los datos de la credencial."
        "INVALID_CREDENTIAL_DATES" -> "Las fechas de la credencial no son coherentes."
        "INVALID_MEDIA_REFERENCE" -> "La referencia de media no es segura."
        "CREDENTIAL_NOT_FOUND" -> "No encontramos esa credencial."
        "VERIFICATION_NOT_ALLOWED" -> "No está permitida la verificación (sin autoverificación)."
        "VERIFICATION_ALREADY_FINAL" -> "La solicitud de verificación ya está resuelta."
        "PUBLIC_PROJECTION_REDACTED" -> "Solo está disponible la vista pública resumida."
        "INFRASTRUCTURE_UNAVAILABLE" ->
            "La persistencia remota de pasaportes requiere la migración 050 (aún no creada)."
        "CONFLICT" -> "Conflicto al guardar el pasaporte o la credencial."
        "M14_REPOSITORY_FAILURE" -> "No pudimos completar la operación (M14)."
        "INVALID_PASSPORT_INPUT" -> "Revisá los datos del pasaporte."
        else -> "Ocurrió un error en el pasaporte (M14)."
    }

    private val knownCodes = listOf(
        "NOT_AUTHENTICATED",
        "PET_NOT_FOUND",
        "PASSPORT_NOT_FOUND",
        "PASSPORT_ALREADY_EXISTS",
        "INVALID_PASSPORT_STATUS",
        "INVALID_TRANSITION",
        "UNAUTHORIZED",
        "INVALID_CREDENTIAL",
        "INVALID_CREDENTIAL_DATES",
        "INVALID_MEDIA_REFERENCE",
        "CREDENTIAL_NOT_FOUND",
        "VERIFICATION_NOT_ALLOWED",
        "VERIFICATION_ALREADY_FINAL",
        "PUBLIC_PROJECTION_REDACTED",
        "INFRASTRUCTURE_UNAVAILABLE",
        "CONFLICT",
        "M14_REPOSITORY_FAILURE",
        "INVALID_PASSPORT_INPUT"
    )

    fun codeOf(error: Throwable): String {
        val blob = buildString {
            append(error.message.orEmpty())
            generateSequence(error.cause) { it.cause }.forEach { append(' ').append(it.message.orEmpty()) }
        }
        knownCodes.forEach { code ->
            if (blob.contains(code, ignoreCase = false)) return code
        }
        return (error as? M14Exception)?.code ?: "M14_UNKNOWN"
    }

    fun failure(t: Throwable): Result<Nothing> {
        val code = codeOf(t)
        return Result.failure(M14Exception(code, userMessage(code), t))
    }
}
