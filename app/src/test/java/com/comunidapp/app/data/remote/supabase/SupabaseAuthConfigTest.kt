package com.comunidapp.app.data.remote.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthConfigTest {

    @Test
    fun canonical_redirect_is_non_null_and_stable() {
        val url = SupabaseAuthConfig.requireRedirectUrl()
        assertEquals("com.comunidapp.app://login-callback", url)
        assertEquals(SupabaseAuthConfig.SCHEME, "com.comunidapp.app")
        assertEquals(SupabaseAuthConfig.HOST, "login-callback")
        assertFalse(SupabaseAuthConfig.isForbiddenRedirect(url))
    }

    @Test
    fun forbidden_redirects_include_null_literals() {
        assertTrue(SupabaseAuthConfig.isForbiddenRedirect(null))
        assertTrue(SupabaseAuthConfig.isForbiddenRedirect(""))
        assertTrue(SupabaseAuthConfig.isForbiddenRedirect("null"))
        assertTrue(SupabaseAuthConfig.isForbiddenRedirect("anull"))
        assertTrue(SupabaseAuthConfig.isForbiddenRedirect("undefined"))
        assertTrue(SupabaseAuthConfig.isForbiddenRedirect("com.comunidapp.app://null"))
        assertTrue(SupabaseAuthConfig.isForbiddenRedirect("https://example.com://anull"))
        assertFalse(SupabaseAuthConfig.isForbiddenRedirect("com.comunidapp.app://login-callback"))
    }
}
