package com.comunidapp.app.domain.auth

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.core.logging.AppLog

/**
 * STAGING-only Google breadcrumbs. Never logs tokens, emails, or account ids.
 */
object GoogleAuthTrace {
    private const val TAG = "GoogleAuth"

    fun event(name: String) {
        if (!isStaging) return
        AppLog.info(TAG, name)
    }

    fun error(type: String) {
        event("GOOGLE-ERROR=$type")
    }

    fun errorType(code: AuthErrorCode?): String = when (code) {
        AuthErrorCode.GOOGLE_AUTH_CANCELLED -> "CANCELLED"
        AuthErrorCode.NETWORK_UNAVAILABLE -> "NETWORK"
        AuthErrorCode.CONFIGURATION_ERROR -> "CONFIGURATION"
        AuthErrorCode.OTP_INVALID, AuthErrorCode.OTP_EXPIRED -> "OTP"
        AuthErrorCode.RECOVERY_LINK_INVALID,
        AuthErrorCode.RECOVERY_LINK_EXPIRED,
        AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE -> "RECOVERY"
        AuthErrorCode.GOOGLE_AUTH_FAILED -> "FAILED"
        null -> "UNKNOWN"
        else -> code.name
    }

    private val isStaging: Boolean
        get() = BuildConfig.LEOVER_ENV.equals("staging", ignoreCase = true)
}
