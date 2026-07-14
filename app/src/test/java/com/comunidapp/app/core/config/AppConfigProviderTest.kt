package com.comunidapp.app.core.config

import com.comunidapp.app.core.featureflags.FeatureFlagOverrides
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de defaults seguros. BuildConfig de test refleja el entorno de compilación unitaria (debug).
 */
class AppConfigProviderTest {

    @After
    fun tearDown() {
        AppConfigProvider.resetForTests()
    }

    @Test
    fun get_exposesVersionAndEnvironment() {
        val config = AppConfigProvider.get()
        assertNotNull(config.appVersionName)
        assertTrue(config.appVersionCode >= 0)
        assertEquals(AppEnvironment.DEBUG, config.environment)
        assertTrue(config.isDebug)
    }

    @Test
    fun get_whenOverridesForceMock_useRemoteIsFalse() {
        AppConfigProvider.resetForTests()
        val config = AppConfigProvider.get(
            FeatureFlagOverrides(useSupabase = false)
        )
        assertFalse(config.useRemoteBackend)
        assertTrue(config.isMockMode)
        assertFalse(config.flags.useSupabase)
        assertFalse(config.flags.enablePaymentsStub)
        assertFalse(config.flags.enableMapsExperimental)
    }

    @Test
    fun get_missingCredentialsMessage_isSafeWhenMock() {
        AppConfigProvider.resetForTests()
        val config = AppConfigProvider.get(FeatureFlagOverrides(useSupabase = false))
        // Con override a mock no exigimos message; con credenciales reales puede ser null.
        if (!config.useRemoteBackend && config.supabaseUrl.isNullOrBlank()) {
            assertNotNull(config.missingConfigMessage)
            assertFalse(config.missingConfigMessage!!.contains("eyJ"))
        }
    }

    @Test
    fun featureFlags_defaults_disableExperimentalPaymentsAndMaps() {
        val flags = AppConfigProvider.featureFlags(
            FeatureFlagOverrides(useSupabase = false)
        )
        assertFalse(flags.enablePaymentsStub)
        assertFalse(flags.enableMapsExperimental)
        assertFalse(flags.useSupabase)
    }

    @Test
    fun featureFlags_override_canEnableVerbose() {
        val flags = AppConfigProvider.featureFlags(
            FeatureFlagOverrides(enableVerboseLogging = true)
        )
        assertTrue(flags.enableVerboseLogging)
    }
}
