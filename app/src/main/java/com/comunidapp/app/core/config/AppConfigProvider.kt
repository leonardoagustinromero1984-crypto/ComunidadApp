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
     * Configuración actual. Si faltan credenciales Supabase, modo mock seguro.
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
        val environment = if (isDebug) AppEnvironment.DEBUG else AppEnvironment.RELEASE
        val url = BuildConfig.SUPABASE_URL.trim().ifBlank { null }
        val hasKey = BuildConfig.SUPABASE_ANON_KEY.trim().isNotBlank()
        val credentialsOk = !url.isNullOrBlank() && hasKey && BuildConfig.SUPABASE_ENABLED
        val useRemote = overrides.useSupabase ?: credentialsOk

        val missingMessage = when {
            credentialsOk -> null
            url.isNullOrBlank() && !hasKey ->
                "Supabase no configurado: modo mock. Completá SUPABASE_URL y SUPABASE_ANON_KEY en local.properties."
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
            supabaseUrl = url,
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
