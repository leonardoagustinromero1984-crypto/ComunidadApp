package com.comunidapp.app.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer — política de URL Supabase para APK físico (hotfix localDebug).
 * Rechaza localhost / emulador (10.0.2.2) / cleartext http:// y sólo acepta HTTPS remoto.
 */
class SupabaseUrlPolicyTest {

    @Test
    fun blank_or_null_url_is_not_usable() {
        assertFalse(SupabaseUrlPolicy.isUsableRemoteUrl(null))
        assertFalse(SupabaseUrlPolicy.isUsableRemoteUrl(""))
        assertFalse(SupabaseUrlPolicy.isUsableRemoteUrl("   "))
    }

    @Test
    fun blank_or_null_url_is_forbidden_host() {
        assertTrue(SupabaseUrlPolicy.isForbiddenHost(null))
        assertTrue(SupabaseUrlPolicy.isForbiddenHost(""))
    }

    @Test
    fun credentials_absent_when_anon_key_blank() {
        val present = SupabaseUrlPolicy.credentialsPresent(
            url = "https://project.supabase.co",
            anonKey = "",
            enabledFlag = true
        )
        assertFalse(present)
    }

    @Test
    fun credentials_absent_when_url_blank_even_with_key() {
        val present = SupabaseUrlPolicy.credentialsPresent(
            url = "",
            anonKey = "some-anon-key",
            enabledFlag = true
        )
        assertFalse(present)
    }

    @Test
    fun credentials_absent_when_disabled_flag() {
        val present = SupabaseUrlPolicy.credentialsPresent(
            url = "https://project.supabase.co",
            anonKey = "some-anon-key",
            enabledFlag = false
        )
        assertFalse(present)
    }

    @Test
    fun credentials_present_when_https_remote_key_and_enabled() {
        val present = SupabaseUrlPolicy.credentialsPresent(
            url = "https://project.supabase.co",
            anonKey = "some-anon-key",
            enabledFlag = true
        )
        assertTrue(present)
    }

    @Test
    fun valid_https_supabase_url_is_usable() {
        assertTrue(SupabaseUrlPolicy.isUsableRemoteUrl("https://abcdefgh.supabase.co"))
        assertFalse(SupabaseUrlPolicy.isForbiddenHost("https://abcdefgh.supabase.co"))
    }

    @Test
    fun emulator_host_10_0_2_2_is_forbidden() {
        assertTrue(SupabaseUrlPolicy.isForbiddenHost("http://10.0.2.2:55321"))
        assertTrue(SupabaseUrlPolicy.isForbiddenHost("https://10.0.2.2:55321"))
        assertFalse(SupabaseUrlPolicy.isUsableRemoteUrl("http://10.0.2.2:55321"))
        assertFalse(SupabaseUrlPolicy.isUsableRemoteUrl("https://10.0.2.2:55321"))
    }

    @Test
    fun localhost_is_forbidden() {
        assertTrue(SupabaseUrlPolicy.isForbiddenHost("http://localhost:55321"))
        assertTrue(SupabaseUrlPolicy.isForbiddenHost("https://localhost"))
        assertTrue(SupabaseUrlPolicy.isForbiddenHost("http://127.0.0.1:55321"))
        assertFalse(SupabaseUrlPolicy.isUsableRemoteUrl("http://localhost:55321"))
        assertFalse(SupabaseUrlPolicy.isUsableRemoteUrl("https://localhost"))
    }

    @Test
    fun cleartext_http_scheme_is_forbidden() {
        assertTrue(SupabaseUrlPolicy.isForbiddenHost("http://project.supabase.co"))
        assertFalse(SupabaseUrlPolicy.isUsableRemoteUrl("http://project.supabase.co"))
    }

    @Test
    fun hostOf_extracts_host_without_secrets() {
        val host = SupabaseUrlPolicy.hostOf("https://abcdefgh.supabase.co/auth/v1?apikey=SECRET")
        assertEquals("abcdefgh.supabase.co", host)
        assertNull(SupabaseUrlPolicy.hostOf(null))
        assertNull(SupabaseUrlPolicy.hostOf(""))
    }

    @Test
    fun hostOf_never_returns_the_apikey_query_value() {
        val host = SupabaseUrlPolicy.hostOf("https://abcdefgh.supabase.co/rest/v1?apikey=SECRET_KEY_VALUE")
        assertNotNullAndNoSecret(host)
    }

    private fun assertNotNullAndNoSecret(host: String?) {
        assertTrue(host != null && host.isNotBlank())
        assertFalse(host!!.contains("SECRET_KEY_VALUE"))
    }
}
