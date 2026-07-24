package com.comunidapp.app.core.config

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.core.featureflags.FeatureFlagOverrides
import com.comunidapp.app.core.featureflags.FeatureFlags
import com.comunidapp.app.core.featureflags.LocalFeatureFlags

object AppConfigProvider {

    @Volatile
    private var cached: AppConfig? = null

    @Volatile
    private var flagsCached: FeatureFlags? = null

    /**
     * Configuración actual. Si faltan credenciales Supabase usables, modo mock seguro.
     */
    fun get(
        overrides: FeatureFlagOverrides = FeatureFlagOverrides.NONE
    ): AppConfig {
        cached?.let { if (overrides == FeatureFlagOverrides.NONE) return it }
        val config = resolve(overrides)
        if (overrides == FeatureFlagOverrides.NONE) {
            cached = config
        }
        return config
    }

    fun featureFlags(
        overrides: FeatureFlagOverrides = FeatureFlagOverrides.NONE
    ): FeatureFlags {
        flagsCached?.let { if (overrides == FeatureFlagOverrides.NONE) return it }
        val flags = LocalFeatureFlags.from(get(overrides), overrides)
        if (overrides == FeatureFlagOverrides.NONE) {
            flagsCached = flags
        }
        return flags
    }

    /** Solo tests: limpia caché. */
    internal fun resetForTests() {
        cached = null
        flagsCached = null
    }

    private fun resolve(overrides: FeatureFlagOverrides): AppConfig {
        val isDebug = BuildConfig.DEBUG
        val environment = when (BuildConfig.LEOVER_ENV.lowercase()) {
            "local" -> AppEnvironment.LOCAL
            "staging" -> AppEnvironment.STAGING
            "production" -> AppEnvironment.PRODUCTION
            else -> if (isDebug) AppEnvironment.LOCAL else AppEnvironment.PRODUCTION
        }
        val rawUrl = BuildConfig.SUPABASE_URL.trim().ifBlank { null }
        val hasKey = BuildConfig.SUPABASE_ANON_KEY.trim().isNotBlank()
        val urlUsable = SupabaseUrlPolicy.isUsableRemoteUrl(rawUrl)
        val credentialsOk = BuildConfig.SUPABASE_ENABLED && urlUsable && hasKey
        val useRemote = overrides.useSupabase ?: credentialsOk

        val missingMessage = when {
            credentialsOk -> null
            rawUrl != null && SupabaseUrlPolicy.isForbiddenHost(rawUrl) ->
                "Supabase URL local/emulador no usable en este APK; se requiere HTTPS remoto."
            environment == AppEnvironment.STAGING ->
                "Supabase staging no configurado: completá SUPABASE_STAGING_URL y " +
                    "SUPABASE_STAGING_PUBLISHABLE_KEY (o ANON) en local.properties."
            environment == AppEnvironment.PRODUCTION ->
                "Supabase production no configurado: modo mock."
            rawUrl.isNullOrBlank() && !hasKey ->
                "Supabase no configurado: modo mock. Completá SUPABASE_URL (HTTPS remoto) " +
                    "o SUPABASE_STAGING_* en local.properties."
            else ->
                "Credenciales Supabase incompletas o inválidas: modo mock."
        }

        val verboseDefault = isDebug
        val verbose = overrides.enableVerboseLogging ?: verboseDefault
        val flags = ActiveFeatureFlags(
            useSupabase = useRemote,
            enablePaymentsStub = overrides.enablePaymentsStub ?: false,
            enableMapsExperimental = overrides.enableMapsExperimental ?: false,
            enableVerboseLogging = verbose
        )

        return AppConfig(
            environment = environment,
            isDebug = isDebug,
            useRemoteBackend = useRemote,
            supabaseUrl = if (urlUsable) rawUrl else null,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            logging = LoggingConfig(
                enabled = isDebug || verbose,
                verbose = verbose && isDebug
            ),
            flags = flags,
            missingConfigMessage = missingMessage
        )
    }
}
