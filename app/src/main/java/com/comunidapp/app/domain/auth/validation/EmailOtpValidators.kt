package com.comunidapp.app.domain.auth.validation

/**
 * Validación de OTP de correo (Supabase puede enviar 6 u 8 dígitos según configuración).
 * No registra el código; solo normaliza y valida longitud/formato.
 */
object EmailOtpValidators {

    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 10

    /** Texto genérico de UI (sin fijar longitud). */
    const val PROMPT_MESSAGE = "Ingresá el código que recibiste por correo"

    fun normalize(raw: String): String = raw.trim()

    /** Entrada de UI: solo dígitos, sin truncar antes de [MAX_LENGTH]. */
    fun sanitizeInput(raw: String): String =
        raw.filter { it.isDigit() }.take(MAX_LENGTH)

    fun isValid(normalizedDigits: String): Boolean =
        normalizedDigits.length in MIN_LENGTH..MAX_LENGTH &&
            normalizedDigits.all { it.isDigit() }

    fun validate(raw: String): Result<String> {
        val normalized = normalize(raw)
        return when {
            normalized.isEmpty() ->
                Result.failure(IllegalArgumentException("otp blank"))
            !normalized.all { it.isDigit() } ->
                Result.failure(IllegalArgumentException("otp non-digit"))
            normalized.length < MIN_LENGTH ->
                Result.failure(IllegalArgumentException("otp too short"))
            normalized.length > MAX_LENGTH ->
                Result.failure(IllegalArgumentException("otp too long"))
            else -> Result.success(normalized)
        }
    }
}
