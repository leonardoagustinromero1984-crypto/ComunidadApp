package com.comunidapp.app.core.config

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.MockPetRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * testLocalDebugUnitTest must stay on the mock backend even when
 * local.properties contains real Supabase credentials. The Gradle task
 * prepends a BuildConfig copy with credentials removed. The local and
 * staging APKs keep their own BuildConfig.
 */
class LocalDebugUnitTestSupabaseIsolationTest {

    @Before
    fun setUp() {
        AppConfigProvider.resetForTests()
    }

    @Test
    fun unitTestsIgnoreLocalPropertiesSupabaseCredentials() {
        assertFalse(buildConfigBoolean("SUPABASE_ENABLED"))
        assertTrue(buildConfigString("SUPABASE_URL").isBlank())
        assertTrue(buildConfigString("SUPABASE_ANON_KEY").isBlank())
        assertTrue(buildConfigString("SUPABASE_CREDENTIAL_SOURCE") == "UNIT_TEST_MOCK")
        assertTrue(buildConfigString("LEOVER_ENV") == "local")
        assertFalse(AppConfigProvider.featureFlags().useSupabase)
        assertFalse(DataProvider.useSupabase)
        assertTrue(DataProvider.petRepository is MockPetRepository)
    }

    private fun buildConfigString(name: String): String =
        BuildConfig::class.java.getField(name).get(null) as String

    private fun buildConfigBoolean(name: String): Boolean =
        BuildConfig::class.java.getField(name).get(null) as Boolean
}
