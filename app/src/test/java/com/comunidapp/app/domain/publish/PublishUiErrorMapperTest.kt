package com.comunidapp.app.domain.publish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishUiErrorMapperTest {

    @Test
    fun lostFoundUserMessage_isFriendlyAndHasNoTechnicalLeak() {
        val message = PublishUiErrorMapper.lostFoundUserMessage()
        assertEquals(PublishUiErrorMapper.LOST_FOUND_USER_MESSAGE, message)
        assertFalse(message.contains("http", ignoreCase = true))
        assertFalse(message.contains("Authorization", ignoreCase = true))
        assertFalse(message.contains("Bearer", ignoreCase = true))
        assertFalse(message.contains("supabase", ignoreCase = true))
        assertFalse(message.contains("gen_random_bytes", ignoreCase = true))
        assertFalse(message.contains("PostgREST", ignoreCase = true))
    }

    @Test
    fun supabaseDump_isUnsafeToShow() {
        val dump = """
            URL: https://abcd.supabase.co/rest/v1/lost_found_posts
            Headers: {Authorization=Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.aaa.bbb, apikey=secret}
            function gen_random_bytes(integer) does not exist
        """.trimIndent()
        assertTrue(PublishUiErrorMapper.isUnsafeToShow(dump))
    }

    @Test
    fun userFacing_hidesTechnicalDump_keepsValidation() {
        assertEquals(
            "Completá los campos obligatorios",
            PublishUiErrorMapper.userFacing("Completá los campos obligatorios", "fallback")
        )
        assertEquals(
            "No pudimos publicar la alerta.\nIntentá nuevamente.",
            PublishUiErrorMapper.userFacing(
                "function gen_random_bytes(integer) does not exist",
                PublishUiErrorMapper.LOST_FOUND_USER_MESSAGE
            )
        )
    }

    @Test
    fun sanitizeTechnical_redactsAuthorizationAndUrl() {
        val error = IllegalStateException(
            "POST https://abcd.supabase.co/rest/v1/lost_found_posts " +
                "Authorization=Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.aaa.bbb " +
                "function gen_random_bytes(integer) does not exist"
        )
        val sanitized = PublishUiErrorMapper.sanitizeTechnical(error)
        assertFalse(sanitized.contains("Bearer eyJ", ignoreCase = true))
        assertFalse(sanitized.contains("https://abcd.supabase.co", ignoreCase = true))
        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9", ignoreCase = true))
    }
}
