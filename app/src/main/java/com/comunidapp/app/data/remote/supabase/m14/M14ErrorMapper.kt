package com.comunidapp.app.data.remote.supabase.m14

/**
 * LeoVer M14 — errores tipificados (Bloque 1–3).
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
        "PET_NOT_ELIGIBLE" -> "La mascota no está habilitada para tener pasaporte."
        "PASSPORT_NOT_FOUND" -> "No encontramos ese pasaporte."
        "PASSPORT_ALREADY_EXISTS" -> "Ya existe un pasaporte activo para esta mascota."
        "PASSPORT_ALREADY_ACTIVE" -> "El pasaporte ya está activo."
        "PASSPORT_NUMBER_GENERATION_FAILED" -> "No pudimos generar el número de pasaporte."
        "PUBLIC_CODE_GENERATION_FAILED" -> "No pudimos generar el código público."
        "INVALID_PASSPORT_STATUS" -> "El estado del pasaporte no permite esta acción."
        "INVALID_TRANSITION" -> "Esa transición de estado no está permitida."
        "UNAUTHORIZED" -> "No tenés autorización para esta acción."
        "INVALID_CREDENTIAL" -> "Revisá los datos de la credencial."
        "INVALID_CREDENTIAL_DATES" -> "Las fechas de la credencial no son coherentes."
        "INVALID_MEDIA_REFERENCE" -> "La referencia de media no es segura."
        "CREDENTIAL_NOT_FOUND" -> "No encontramos esa credencial."
        "CREDENTIAL_NOT_ELIGIBLE" -> "La credencial no cumple los requisitos para esta acción."
        "VERIFICATION_NOT_ALLOWED" -> "No está permitida la verificación (sin autoverificación)."
        "VERIFICATION_NOT_FOUND" -> "No encontramos esa solicitud de verificación."
        "VERIFICATION_REVIEW_NOT_ALLOWED" -> "No podés revisar esta solicitud de verificación."
        "VERIFICATION_ALREADY_UNDER_REVIEW" -> "La solicitud ya está en revisión."
        "VERIFICATION_ALREADY_FINAL" -> "La solicitud de verificación ya está resuelta."
        "VERIFICATION_REQUEST_ALREADY_PENDING" -> "Ya hay una solicitud de verificación pendiente."
        "DECISION_ALREADY_EXISTS" -> "Ya existe una decisión final para esta solicitud."
        "DECISION_NOT_FOUND" -> "No encontramos la decisión de verificación."
        "ISSUER_NOT_AUTHORIZED" -> "El emisor no está autorizado para verificar esta credencial."
        "SELF_VERIFICATION_NOT_ALLOWED" -> "No podés autoverificar esta credencial."
        "CREDENTIAL_ALREADY_FINAL" -> "La credencial ya está en un estado final."
        "CREDENTIAL_REVOCATION_NOT_ALLOWED" -> "No podés revocar esta credencial."
        "PUBLIC_CODE_ROTATION_NOT_ALLOWED" -> "No podés rotar el código público de este pasaporte."
        "INVALID_QR_PAYLOAD" -> "El enlace o código QR no es válido (sin datos personales)."
        "PUBLIC_PASSPORT_NOT_AVAILABLE" -> "Este pasaporte no está disponible públicamente."
        "PUBLIC_PROJECTION_REDACTED" -> "Solo está disponible la vista pública resumida."
        "INFRASTRUCTURE_UNAVAILABLE" ->
            "La operación remota de pasaportes no está disponible en este momento."
        "CONFLICT" -> "Conflicto al guardar el pasaporte o la credencial."
        "M14_REPOSITORY_FAILURE" -> "No pudimos completar la operación (M14)."
        "INVALID_PASSPORT_INPUT" -> "Revisá los datos del pasaporte."
        else -> "Ocurrió un error en el pasaporte (M14)."
    }

    private val knownCodes = listOf(
        "NOT_AUTHENTICATED",
        "PET_NOT_FOUND",
        "PET_NOT_ELIGIBLE",
        "PASSPORT_NOT_FOUND",
        "PASSPORT_ALREADY_EXISTS",
        "PASSPORT_ALREADY_ACTIVE",
        "PASSPORT_NUMBER_GENERATION_FAILED",
        "PUBLIC_CODE_GENERATION_FAILED",
        "INVALID_PASSPORT_STATUS",
        "INVALID_TRANSITION",
        "UNAUTHORIZED",
        "INVALID_CREDENTIAL",
        "INVALID_CREDENTIAL_DATES",
        "INVALID_MEDIA_REFERENCE",
        "CREDENTIAL_NOT_FOUND",
        "CREDENTIAL_NOT_ELIGIBLE",
        "VERIFICATION_NOT_ALLOWED",
        "VERIFICATION_NOT_FOUND",
        "VERIFICATION_REVIEW_NOT_ALLOWED",
        "VERIFICATION_ALREADY_UNDER_REVIEW",
        "VERIFICATION_ALREADY_FINAL",
        "VERIFICATION_REQUEST_ALREADY_PENDING",
        "DECISION_ALREADY_EXISTS",
        "DECISION_NOT_FOUND",
        "ISSUER_NOT_AUTHORIZED",
        "SELF_VERIFICATION_NOT_ALLOWED",
        "CREDENTIAL_ALREADY_FINAL",
        "CREDENTIAL_REVOCATION_NOT_ALLOWED",
        "PUBLIC_CODE_ROTATION_NOT_ALLOWED",
        "INVALID_QR_PAYLOAD",
        "PUBLIC_PASSPORT_NOT_AVAILABLE",
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
        // Prefer longer / more specific codes first when overlapping substrings exist.
        knownCodes.sortedByDescending { it.length }.forEach { code ->
            if (blob.contains(code, ignoreCase = false)) return code
        }
        return (error as? M14Exception)?.code ?: "M14_UNKNOWN"
    }

    fun failure(t: Throwable): Result<Nothing> {
        val code = codeOf(t)
        return Result.failure(M14Exception(code, userMessage(code), t))
    }
}
