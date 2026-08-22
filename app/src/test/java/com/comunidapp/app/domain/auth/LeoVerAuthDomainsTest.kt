package com.comunidapp.app.domain.auth

import com.comunidapp.app.data.remote.supabase.SupabaseAuthConfig
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.qa.PhysicalQaFix02Contracts
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LeoVerAuthDomainsTest {

    @Test
    fun officialDomainIsLeoverComArNotInvented() {
        assertEquals("leover.com.ar", LeoVerOfficialDomain.APEX)
        assertEquals("auth-staging.leover.com.ar", LeoVerAuthDomains.STAGING_AUTH_DOMAIN)
        assertEquals("auth.leover.com.ar", LeoVerAuthDomains.PROD_AUTH_DOMAIN)
        assertEquals(
            LeoVerAuthDomains.STAGING_AUTH_DOMAIN,
            SupabaseAuthConfig.STAGING_AUTH_DOMAIN
        )
        assertEquals(
            LeoVerAuthDomains.PROD_AUTH_DOMAIN,
            SupabaseAuthConfig.PROD_AUTH_DOMAIN
        )
    }

    @Test
    fun customDomainInactiveKeepsApiUrl() {
        val api = "https://example-project.supabase.co"
        assertEquals(
            api,
            LeoVerAuthDomains.supabaseUrlForClient(
                apiUrl = api,
                customAuthUrl = "https://auth-staging.leover.com.ar",
                customActive = false
            )
        )
        assertFalse(PhysicalQaFix02Contracts.STAGING_CUSTOM_AUTH_DOMAIN_ACTIVE)
        assertTrue(PhysicalQaFix02Contracts.EXTERNAL_DNS_OR_GOOGLE_ACTION_REQUIRED)
    }

    @Test
    fun customDomainActiveUsesOfficialStagingAuthHost() {
        val api = "https://example-project.supabase.co"
        assertEquals(
            "https://auth-staging.leover.com.ar",
            LeoVerAuthDomains.supabaseUrlForClient(
                apiUrl = api,
                customAuthUrl = LeoVerAuthDomains.STAGING_AUTH_DOMAIN,
                customActive = true
            )
        )
    }

    @Test
    fun blankOrInvalidCustomDomainDoesNotReplaceApi() {
        val api = "https://example-project.supabase.co"
        assertEquals(
            api,
            LeoVerAuthDomains.supabaseUrlForClient(api, "", true)
        )
        assertEquals(
            api,
            LeoVerAuthDomains.supabaseUrlForClient(api, "http://localhost", true)
        )
    }

    @Test
    fun googleRedirectUsesCustomDomainNotProjectRef() {
        assertEquals(
            "https://auth-staging.leover.com.ar/auth/v1/callback",
            LeoVerAuthDomains.stagingGoogleRedirectCustomDomain()
        )
        assertFalse(
            LeoVerAuthDomains.stagingGoogleRedirectCustomDomain()
                .contains(CanonicalBackend.STAGING_PROJECT_REF)
        )
        val transitional = LeoVerAuthDomains.googleRedirectForApiUrl(CanonicalBackend.STAGING_URL)
        assertTrue(transitional.endsWith("/auth/v1/callback"))
        assertTrue(LeoVerAuthDomains.isTechnicalSupabaseHost(CanonicalBackend.STAGING_URL))
        assertFalse(LeoVerAuthDomains.isTechnicalSupabaseHost(LeoVerAuthDomains.STAGING_AUTH_DOMAIN))
    }

    @Test
    fun canonicalStagingRecognizesCustomAuthHost() {
        assertTrue(CanonicalBackend.isCanonicalStagingUrl(CanonicalBackend.STAGING_URL))
        assertTrue(
            CanonicalBackend.isCanonicalStagingUrl("https://auth-staging.leover.com.ar")
        )
        assertFalse(
            CanonicalBackend.isCanonicalStagingUrl("https://wystsapjfpdtoprlmizz.supabase.co")
        )
    }

    @Test
    fun clientDoesNotHardcodeProjectRefForAuthUrl() {
        val text = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/SupabaseClientProvider.kt"
        ).readText()
        assertTrue(text.contains("LeoVerAuthDomains.supabaseUrlForClient"))
        assertTrue(text.contains("BuildConfig.AUTH_DOMAIN"))
        assertFalse(text.contains("tobqbddfcyitwgbkthhy.supabase.co"))
    }

    @Test
    fun googleBrandingNameIsLeoVerAndOfficialLogoExists() {
        assertEquals("LeoVer", LeoVerAuthDomains.GOOGLE_AUTH_APP_NAME)
        val logo = File(LeoVerAuthDomains.GOOGLE_AUTH_LOGO_ASSET)
        val fromAppModule = File("../${LeoVerAuthDomains.GOOGLE_AUTH_LOGO_ASSET}")
        assertTrue(logo.isFile || fromAppModule.isFile)
    }
}
