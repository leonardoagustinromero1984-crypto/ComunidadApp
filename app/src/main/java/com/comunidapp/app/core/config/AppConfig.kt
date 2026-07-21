package com.comunidapp.app.core.config

/**
 * Ambiente de ejecución LeoVer (product flavors: local / staging / production).
 */
enum class AppEnvironment {
    LOCAL,
    STAGING,
    PRODUCTION
}

data class LoggingConfig(
    val enabled: Boolean,
    val verbose: Boolean,
    val minTag: String = "LeoVer"
)

/** Snapshot de flags activas expuesto por [AppConfig] (sin I/O). */
data class ActiveFeatureFlags(
    val useSupabase: Boolean,
    val enablePaymentsStub: Boolean,
    val enableMapsExperimental: Boolean,
    val enableVerboseLogging: Boolean
)

/**
 * Fuente única tipada de configuración. La UI no debe leer [BuildConfig] directamente.
 * Flags tipadas también vía [AppConfigProvider.featureFlags].
 */
data class AppConfig(
    val environment: AppEnvironment,
    val isDebug: Boolean,
    val useRemoteBackend: Boolean,
    /** Host público de Supabase (sin path de keys). Nunca loguear anon key. */
    val supabaseUrl: String?,
    val appVersionName: String,
    val appVersionCode: Int,
    val logging: LoggingConfig,
    val flags: ActiveFeatureFlags,
    val missingConfigMessage: String?
) {
    val isMockMode: Boolean get() = !useRemoteBackend
}
