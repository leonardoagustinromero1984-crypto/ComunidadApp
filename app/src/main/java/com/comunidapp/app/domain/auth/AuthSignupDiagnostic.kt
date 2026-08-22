package com.comunidapp.app.domain.auth

/**
 * Staging / localDebug QA codes. Never include tokens, passwords, or OTP.
 */
object AuthSignupDiagnostic {

    fun qaCode(code: AuthErrorCode, technicalMessage: String = ""): String {
        val lower = technicalMessage.lowercase()
        if (lower.contains("smtp") || lower.contains("gomail") || lower.contains("error sending")) {
            return "AUTH_SIGNUP_SMTP"
        }
        return when (code) {
            AuthErrorCode.RATE_LIMITED -> "AUTH_SIGNUP_RATE_LIMIT"
            AuthErrorCode.EMAIL_ALREADY_REGISTERED -> "AUTH_SIGNUP_ALREADY_REGISTERED"
            AuthErrorCode.NETWORK_UNAVAILABLE -> "AUTH_SIGNUP_NETWORK"
            AuthErrorCode.SIGNUP_FAILED,
            AuthErrorCode.UNKNOWN_AUTH_ERROR -> "AUTH_SIGNUP_UNKNOWN"
            else -> "AUTH_SIGNUP_${code.name}"
        }
    }

    fun debugDetail(code: AuthErrorCode, technicalMessage: String, debugBuild: Boolean): String? {
        if (!debugBuild) return null
        return qaCode(code, technicalMessage)
    }
}
