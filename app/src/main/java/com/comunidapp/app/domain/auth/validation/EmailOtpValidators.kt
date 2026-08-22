package com.comunidapp.app.domain.auth.validation

/**
 * Signup email OTP. Supabase token length is configurable (6–10).
 * Staging currently emits 8 digits. Do not pad, invent, or truncate a valid code.
 */
object EmailOtpValidators {

    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 10

    const val PROMPT_MESSAGE = "Ingresá el código de verificación que recibiste por correo"

    fun normalize(raw: String): String = raw.trim()

    /** UI input: digits only. Does not pad or cut a valid 6–10 code. >10 stays so verify stays disabled. */
    fun sanitizeInput(raw: String): String = raw.filter { it.isDigit() }

    fun isValid(normalizedDigits: String): Boolean =
        normalizedDigits.length in MIN_LENGTH..MAX_LENGTH &&
            normalizedDigits.all { it.isDigit() }

    fun validate(raw: String): Result<String> {
        val normalized = normalize(raw).filter { it.isDigit() }
        return when {
            normalized.isEmpty() ->
                Result.failure(IllegalArgumentException("otp blank"))
            normalized.length < MIN_LENGTH ->
                Result.failure(IllegalArgumentException("otp too short"))
            normalized.length > MAX_LENGTH ->
                Result.failure(IllegalArgumentException("otp too long"))
            else -> Result.success(normalized)
        }
    }
}
