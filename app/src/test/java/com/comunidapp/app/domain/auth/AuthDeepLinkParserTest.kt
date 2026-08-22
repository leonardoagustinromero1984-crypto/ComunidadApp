package com.comunidapp.app.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthDeepLinkParserTest {

    @Before
    fun setUp() {
        AuthDeepLinkParser.resetConsumedForTests()
    }

    @Test
    fun classify_recovery_from_fragment() {
        val uri =
            "com.comunidapp.app://login-callback#access_token=x&type=recovery&refresh_token=y"
        assertEquals(AuthDeepLinkKind.PasswordRecovery, AuthDeepLinkParser.classify(uri))
    }

    @Test
    fun classify_email_confirmation_from_query() {
        val uri = "com.comunidapp.app://login-callback?type=signup"
        assertEquals(AuthDeepLinkKind.EmailConfirmation, AuthDeepLinkParser.classify(uri))
    }

    @Test
    fun consume_once_second_call_null() {
        val uri = "com.comunidapp.app://login-callback#type=recovery&access_token=abc"
        assertEquals(AuthDeepLinkKind.PasswordRecovery, AuthDeepLinkParser.consumeOnce(uri))
        assertNull(AuthDeepLinkParser.consumeOnce(uri))
    }

    @Test
    fun extract_type_from_fragment() {
        assertEquals(
            "recovery",
            AuthDeepLinkParser.extractType(
                "com.comunidapp.app://login-callback#type=recovery&foo=1"
            )
        )
    }

    @Test
    fun classify_pkce_code_as_session_callback() {
        val uri = "com.comunidapp.app://login-callback?code=abc"
        assertEquals(AuthDeepLinkKind.SessionCallback, AuthDeepLinkParser.classify(uri))
    }

    @Test
    fun classify_expired_error_as_link_error() {
        val uri =
            "com.comunidapp.app://login-callback?error=access_denied&error_code=otp_expired"
        assertEquals(AuthDeepLinkKind.LinkError, AuthDeepLinkParser.classify(uri))
        assertEquals(
            "El enlace venció o ya fue utilizado.",
            AuthDeepLinkParser.userMessageFor(uri)
        )
    }

    @Test
    fun classify_null_literals_as_link_error() {
        assertEquals(AuthDeepLinkKind.LinkError, AuthDeepLinkParser.classify("null"))
        assertEquals(AuthDeepLinkKind.LinkError, AuthDeepLinkParser.classify("anull"))
        assertTrue(AuthDeepLinkParser.isForbiddenCallback("com.comunidapp.app://null"))
    }

    @Test
    fun notice_store_publishes_and_consumes() {
        AuthLinkNoticeStore.resetForTests()
        AuthLinkNoticeStore.publish(
            AuthDeepLinkKind.LinkError,
            "El enlace venció o ya fue utilizado."
        )
        val notice = AuthLinkNoticeStore.consume()
        assertEquals(AuthDeepLinkKind.LinkError, notice?.kind)
        assertEquals("El enlace venció o ya fue utilizado.", notice?.userMessage)
        assertNull(AuthLinkNoticeStore.consume())
    }
}
