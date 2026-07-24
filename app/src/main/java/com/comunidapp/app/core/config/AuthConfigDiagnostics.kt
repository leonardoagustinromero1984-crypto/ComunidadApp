package com.comunidapp.app.core.config

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.auth.auth

/**
 * Diagnóstico seguro de auth/Supabase para debug (LeoVer).
 * Nunca registra anon key, access/refresh tokens ni Authorization.
 */
data class AuthConfigDiagnosticsSnapshot(
    val buildType: String,
    val flavor: String,
    val useSupabase: Boolean,
    val urlPresent: Boolean,
    val supabaseHost: String?,
    val anonKeyPresent: Boolean,
    val anonKeyLength: Int,
    val credentialSource: String,
    val sessionPresent: Boolean,
    val accessTokenPresent: Boolean,
    val accessTokenExpiresAt: String?,
    val exceptionClass: String? = null,
    val httpStatus: Int? = null
)

object AuthConfigDiagnostics {

    private const val TAG = "AuthConfigDiag"

    fun snapshot(
        exceptionClass: String? = null,
        httpStatus: Int? = null
    ): AuthConfigDiagnosticsSnapshot {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        val flags = AppConfigProvider.featureFlags()
        val session = if (flags.useSupabase && SupabaseUrlPolicy.isUsableRemoteUrl(url)) {
            runCatching { supabase.auth.currentSessionOrNull() }.getOrNull()
        } else {
            null
        }
        return AuthConfigDiagnosticsSnapshot(
            buildType = if (BuildConfig.DEBUG) "debug" else "release",
            flavor = BuildConfig.LEOVER_ENV,
            useSupabase = flags.useSupabase,
            urlPresent = url.isNotBlank(),
            supabaseHost = SupabaseUrlPolicy.hostOf(url),
            anonKeyPresent = key.isNotBlank(),
            anonKeyLength = key.length,
            credentialSource = runCatching { BuildConfig.SUPABASE_CREDENTIAL_SOURCE }.getOrDefault("UNKNOWN"),
            sessionPresent = session != null,
            accessTokenPresent = !session?.accessToken.isNullOrBlank(),
            accessTokenExpiresAt = session?.expiresAt?.toString(),
            exceptionClass = exceptionClass,
            httpStatus = httpStatus
        )
    }

    fun logSafe(prefix: String = "auth", exceptionClass: String? = null, httpStatus: Int? = null) {
        val s = snapshot(exceptionClass, httpStatus)
        AppLog.info(
            TAG,
            "$prefix buildType=${s.buildType} flavor=${s.flavor} useSupabase=${s.useSupabase} " +
                "urlPresent=${s.urlPresent} host=${s.supabaseHost} anonKeyPresent=${s.anonKeyPresent} " +
                "anonKeyLength=${s.anonKeyLength} source=${s.credentialSource} " +
                "sessionPresent=${s.sessionPresent} accessTokenPresent=${s.accessTokenPresent} " +
                "expiresAt=${s.accessTokenExpiresAt} exceptionClass=${s.exceptionClass} httpStatus=${s.httpStatus}"
        )
    }
}
