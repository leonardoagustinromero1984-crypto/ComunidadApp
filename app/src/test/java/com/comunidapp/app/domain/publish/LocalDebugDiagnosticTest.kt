package com.comunidapp.app.domain.publish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDebugDiagnosticTest {

    @Test
    fun copyIsDisabledInReleaseAndProduction() {
        assertFalse(LocalDebugDiagnostic.isCopyEnabled(debug = false, env = "production"))
        assertFalse(LocalDebugDiagnostic.isCopyEnabled(debug = false, env = "local"))
        assertFalse(LocalDebugDiagnostic.isCopyEnabled(debug = true, env = "production"))
        assertTrue(LocalDebugDiagnostic.isCopyEnabled(debug = true, env = "staging"))
    }

    @Test
    fun copyIsEnabledOnlyForLocalDebug() {
        assertTrue(LocalDebugDiagnostic.isCopyEnabled(debug = true, env = "local"))
    }

    @Test
    fun lostFoundDiagnosticIsSanitizedAndTyped() {
        val error = IllegalStateException(
            """
            URL: https://abcd.supabase.co/rest/v1/lost_found_posts
            Headers: {Authorization=Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.aaa.bbb, apikey=secret}
            function gen_random_bytes(integer) does not exist
            SQLSTATE 42883
            """.trimIndent()
        )
        val text = LocalDebugDiagnostic.lostFoundCreate(
            type = "LOST",
            error = error,
            appVersion = "1.1-local",
            nowMillis = 1_700_000_000_000L
        )
        assertTrue(text.startsWith("LEOVER_DIAGNOSTIC"))
        assertTrue(text.contains("operation=lost_found_create"))
        assertTrue(text.contains("type=LOST"))
        assertTrue(text.contains("errorCode=LF-CREATE-DB"))
        assertTrue(text.contains("sqlstate=42883"))
        assertTrue(text.contains("gen_random_bytes"))
        assertFalse(text.contains("Authorization", ignoreCase = true))
        assertFalse(text.contains("Bearer", ignoreCase = true))
        assertFalse(text.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertFalse(text.contains("apikey=secret", ignoreCase = true))
        assertFalse(text.contains("https://abcd.supabase.co", ignoreCase = true))
        assertFalse(text.contains("token", ignoreCase = true))
        assertFalse(text.contains("headers", ignoreCase = true))
    }

    @Test
    fun foundTypeIsEmitted() {
        val text = LocalDebugDiagnostic.lostFoundCreate(
            type = "FOUND",
            error = IllegalStateException("function gen_random_bytes(integer) does not exist"),
            appVersion = "1.1-local",
            nowMillis = 1_700_000_000_000L
        )
        assertTrue(text.contains("type=FOUND"))
        assertFalse(text.contains("type=LOST"))
    }

    @Test
    fun forOperationSanitizesTokens() {
        val error = IllegalStateException(
            "Authorization: Bearer secret-token SerializationException unexpected JSON token"
        )
        val text = LocalDebugDiagnostic.forOperation(
            operation = "pet_create",
            error = error,
            extra = mapOf("rpc" to "m08_create_pet_with_principal", "token" to "should-not-appear"),
            appVersion = "1.1-local",
            nowMillis = 1_700_000_000_000L
        )
        assertTrue(text.contains("operation=pet_create"))
        assertTrue(text.contains("rpc=m08_create_pet_with_principal"))
        assertFalse(text.contains("Bearer", ignoreCase = true))
        assertFalse(text.contains("secret-token"))
        assertFalse(text.contains("should-not-appear"))
        assertFalse(text.contains("Authorization", ignoreCase = true))
    }

    @Test
    fun userFacingLostFoundMessageStaysFriendly() {
        val message = PublishUiErrorMapper.lostFoundUserMessage()
        assertEquals("No pudimos publicar la alerta.\nIntentá nuevamente.", message)
        assertFalse(message.contains("gen_random_bytes", ignoreCase = true))
        assertFalse(message.contains("supabase", ignoreCase = true))
    }
}
