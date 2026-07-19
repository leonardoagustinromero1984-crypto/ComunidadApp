package com.comunidapp.app.core.featureflags

/**
 * Flags tipadas locales. No reemplazan permisos ni seguridad.
 */
interface FeatureFlags {
    val useSupabase: Boolean
    val enablePaymentsStub: Boolean
    val enableMapsExperimental: Boolean
    val enableVerboseLogging: Boolean
}

data class FeatureFlagOverrides(
    val useSupabase: Boolean? = null,
    val enablePaymentsStub: Boolean? = null,
    val enableMapsExperimental: Boolean? = null,
    val enableVerboseLogging: Boolean? = null
) {
    companion object {
        val NONE = FeatureFlagOverrides()
    }
}

data class LocalFeatureFlags(
    override val useSupabase: Boolean,
    override val enablePaymentsStub: Boolean,
    override val enableMapsExperimental: Boolean,
    override val enableVerboseLogging: Boolean
) : FeatureFlags {
    companion object {
        fun from(
            config: com.comunidapp.app.core.config.AppConfig,
            overrides: FeatureFlagOverrides = FeatureFlagOverrides.NONE
        ): LocalFeatureFlags {
            val snapshot = config.flags
            // Defaults seguros: pagos/mapas experimentales OFF en rama limpia.
            return LocalFeatureFlags(
                useSupabase = overrides.useSupabase ?: snapshot.useSupabase,
                enablePaymentsStub = overrides.enablePaymentsStub ?: snapshot.enablePaymentsStub,
                enableMapsExperimental = overrides.enableMapsExperimental
                    ?: snapshot.enableMapsExperimental,
                enableVerboseLogging = overrides.enableVerboseLogging
                    ?: snapshot.enableVerboseLogging
            )
        }
    }
}
