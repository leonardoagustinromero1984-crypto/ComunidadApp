package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.domain.auth.AuthErrorCode
import com.comunidapp.app.domain.auth.AuthException
import com.comunidapp.app.domain.auth.ConsentMetadata
import com.comunidapp.app.domain.auth.LegalDocumentConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MockAuthRepositoryTest {

    private lateinit var repo: MockAuthRepository

    private val demoConsent = ConsentMetadata.forRegistration()

    @Before
    fun setUp() {
        repo = MockAuthRepository()
        repo.resetForTests()
    }

    @After
    fun tearDown() {
        runBlocking { repo.logout() }
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun login_fixture_ok() = runBlocking {
        val result = repo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        assertTrue(result.isSuccess)
        assertEquals(MockData.currentUser.email.lowercase(), result.getOrNull()?.email?.lowercase())
    }

    @Test
    fun login_wrong_password() = runBlocking {
        val result = repo.login(MockData.currentUser.email, "wrongpass")
        assertTrue(result.isFailure)
        assertEquals(
            AuthErrorCode.INVALID_CREDENTIALS.name,
            (result.exceptionOrNull() as AuthException).code
        )
    }

    @Test
    fun login_unknown_email_rejected() = runBlocking {
        val result = repo.login("desconocido@email.com", "demo1234")
        assertTrue(result.isFailure)
        assertEquals(
            AuthErrorCode.INVALID_CREDENTIALS.name,
            (result.exceptionOrNull() as AuthException).code
        )
    }

    @Test
    fun register_and_duplicate() = runBlocking {
        val email = "nueva@email.com"
        val first = repo.register(
            "Nueva",
            email,
            "password1",
            demoConsent,
            com.comunidapp.app.data.model.AccountType.SHELTER
        )
        assertTrue(first.isSuccess)
        assertEquals(com.comunidapp.app.data.model.AccountType.PERSON, first.getOrNull()?.accountType)
        val saved = repo.consentFor(email)
        assertEquals(LegalDocumentConfig.terms.version, saved?.termsVersion)
        assertEquals(LegalDocumentConfig.privacy.version, saved?.privacyVersion)

        val dup = repo.register("Otra", email, "password1", demoConsent)
        assertTrue(dup.isFailure)
        assertEquals(
            AuthErrorCode.EMAIL_ALREADY_REGISTERED.name,
            (dup.exceptionOrNull() as AuthException).code
        )
    }

    @Test
    fun register_short_password() = runBlocking {
        val result = repo.register("X", "corta@email.com", "1234567", demoConsent)
        assertTrue(result.isFailure)
        assertEquals(
            AuthErrorCode.WEAK_PASSWORD.name,
            (result.exceptionOrNull() as AuthException).code
        )
    }

    @Test
    fun verification_and_login() = runBlocking {
        val email = "verify@email.com"
        repo.register("V", email, "password1", demoConsent)
        assertNull(repo.getCurrentUser())
        assertFalse(repo.isEmailVerified(email))

        repo.verifyEmailOtp(email, "123456")
        assertTrue(repo.isEmailVerified(email))
        val login = repo.login(email, "password1")
        assertTrue(login.isSuccess)
    }

    @Test
    fun verifyEmailOtp_accepts_eight_digits_complete() = runBlocking {
        val email = "otp8@email.com"
        repo.register("O", email, "password1", demoConsent)
        val result = repo.verifyEmailOtp(email, "  87654321  ")
        assertTrue(result.isSuccess)
        assertTrue(repo.isEmailVerified(email))
    }

    @Test
    fun verifyEmailOtp_rejects_five_and_eleven_digits() = runBlocking {
        val email = "otpbad@email.com"
        repo.register("O", email, "password1", demoConsent)
        assertTrue(repo.verifyEmailOtp(email, "12345").isFailure)
        assertTrue(repo.verifyEmailOtp(email, "12345678901").isFailure)
        assertFalse(repo.isEmailVerified(email))
    }

    @Test
    fun recovery_antienumeration_and_reset() = runBlocking {
        val unknown = repo.sendPasswordResetEmail("nadie@email.com")
        assertTrue(unknown.isSuccess)

        val known = repo.sendPasswordResetEmail(MockData.currentUser.email)
        assertTrue(known.isSuccess)
        val token = MockAuthDatabase.findByEmail(MockData.currentUser.email)?.resetToken
        assertTrue(!token.isNullOrBlank())

        val reset = repo.resetPassword(MockData.currentUser.email, token!!, "newpass12")
        assertTrue(reset.isSuccess)
        assertTrue(repo.login(MockData.currentUser.email, "newpass12").isSuccess)
    }

    @Test
    fun logout_clears_session() = runBlocking {
        repo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        assertTrue(repo.getCurrentUser() != null)
        repo.logout()
        assertNull(repo.getCurrentUser())
    }
}
