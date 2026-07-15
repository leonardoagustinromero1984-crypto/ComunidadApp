package com.comunidapp.app.domain.auth

/**
 * Comandos tipados de autenticación.
 * [SignUpCommand] no incluye AccountType (D-M01-05 → M02).
 */
data class SignInCommand(
    val email: String,
    val password: String
)

data class SignUpCommand(
    val name: String,
    val email: String,
    val password: String,
    val confirmPassword: String,
    val acceptedTerms: Boolean,
    val acceptedPrivacy: Boolean,
    val termsVersion: String,
    val privacyVersion: String
)

data class RequestPasswordRecoveryCommand(
    val email: String
)

data class ResetPasswordCommand(
    val email: String,
    val token: String,
    val newPassword: String,
    val confirmPassword: String
)

data class VerifyEmailCommand(
    val email: String,
    val otpCode: String? = null
)

data class ResendVerificationCommand(
    val email: String
)

data class UpdatePasswordFromRecoveryCommand(
    val newPassword: String,
    val confirmPassword: String
)

data class ChangePasswordCommand(
    val currentPassword: String,
    val newPassword: String,
    val confirmPassword: String
)

data class AcceptLegalConsentCommand(
    val acceptedTerms: Boolean,
    val acceptedPrivacy: Boolean,
    val termsVersion: String,
    val privacyVersion: String,
    val locale: String? = null
)

data class DeleteAccountCommand(
    val currentPassword: String,
    val acknowledged: Boolean,
    val confirmationText: String,
    val idempotencyKey: String
) {
    companion object {
        const val CONFIRMATION_PHRASE = "ELIMINAR"
    }
}
