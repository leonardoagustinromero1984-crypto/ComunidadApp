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
}
