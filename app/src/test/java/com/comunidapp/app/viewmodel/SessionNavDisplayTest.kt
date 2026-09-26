package com.comunidapp.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionNavDisplayTest {

    @Test
    fun googleHydrateAfterLoginDoesNotStayOnLogin() {
        val lastReady = SessionNavDisplay.rememberLoggedIn(SessionState.LoggedOut, false)
        assertFalse(lastReady)
        assertEquals(
            SessionState.Loading,
            SessionNavDisplay.resolve(SessionState.Loading, lastReady)
        )
    }

    @Test
    fun refreshWhileLoggedInKeepsHome() {
        val lastReady = SessionNavDisplay.rememberLoggedIn(
            SessionState.LoggedIn,
            false,
            sessionUserId = "user-a",
            rememberedUserId = "user-a"
        )
        assertTrue(lastReady)
        assertEquals(
            SessionState.LoggedIn,
            SessionNavDisplay.resolve(SessionState.Loading, lastReady)
        )
        assertTrue(
            SessionNavDisplay.rememberLoggedIn(
                SessionState.Loading,
                true,
                sessionUserId = "user-a",
                rememberedUserId = "user-a"
            )
        )
    }

    @Test
    fun accountSwitchLoadingDifferentUserDoesNotKeepPreviousHome() {
        val lastReady = SessionNavDisplay.rememberLoggedIn(
            SessionState.Loading,
            true,
            sessionUserId = "user-b",
            rememberedUserId = "user-a"
        )
        assertFalse(lastReady)
        assertEquals(
            SessionState.Loading,
            SessionNavDisplay.resolve(SessionState.Loading, lastReady)
        )
    }

    @Test
    fun accountSwitchLoadingDoesNotKeepPreviousHome() {
        val lastReady = SessionNavDisplay.rememberLoggedIn(
            SessionState.Loading,
            true,
            sessionUserId = null
        )
        assertFalse(lastReady)
        assertEquals(
            SessionState.Loading,
            SessionNavDisplay.resolve(SessionState.Loading, lastReady)
        )
    }

    @Test
    fun adminSessionIsNotRememberedAsSocialHome() {
        assertFalse(
            SessionNavDisplay.rememberLoggedIn(SessionState.AdminSession, true)
        )
        assertFalse(
            SessionNavDisplay.rememberLoggedIn(SessionState.AdminPasswordChangeRequired, true)
        )
        assertFalse(
            SessionNavDisplay.rememberLoggedIn(SessionState.AdminMfaEnrollmentRequired, true)
        )
        assertFalse(
            SessionNavDisplay.rememberLoggedIn(SessionState.AdminMfaChallengeRequired, true)
        )
        assertEquals(
            SessionState.AdminSession,
            SessionNavDisplay.resolve(SessionState.AdminSession, lastReadyWasLoggedIn = true)
        )
    }

    @Test
    fun logoutClearsRememberedHome() {
        val afterLogout = SessionNavDisplay.rememberLoggedIn(SessionState.LoggedOut, true)
        assertFalse(afterLogout)
        assertEquals(
            SessionState.Loading,
            SessionNavDisplay.resolve(SessionState.Loading, afterLogout)
        )
    }
}
