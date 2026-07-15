package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.domain.auth.AuthErrorCode
import com.comunidapp.app.domain.auth.AuthException
import com.comunidapp.app.domain.auth.ConsentMetadata
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MockAuthRepositoryStage4Test {

    private lateinit var repo: MockAuthRepository

    @Before
    fun setUp() {
        repo = MockAuthRepository()
        repo.resetForTests()
    }

    @After
    fun tearDown() {
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun recovery_session_updates_password_and_clears_session() = runTest {
        repo.activateRecoverySession(MockData.currentUser.email)
        assertTrue(repo.isRecoverySessionActive())
        val result = repo.updatePasswordFromRecovery("nuevapass1")
        assertTrue(result.isSuccess)
        assertFalse(repo.isRecoverySessionActive())
        assertNull(repo.getCurrentUser())
        val loginOld = repo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        assertTrue(loginOld.isFailure)
        val loginNew = repo.login(MockData.currentUser.email, "nuevapass1")
        assertTrue(loginNew.isSuccess)
    }

    @Test
    fun updatePasswordFromRecovery_without_session_fails() = runTest {
        val result = repo.updatePasswordFromRecovery("nuevapass1")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as AuthException
        assertEquals(AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE.name, ex.code)
    }

    @Test
    fun changePassword_requires_current() = runTest {
        repo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val bad = repo.changePassword("wrongpass", "nuevapass1")
        assertTrue(bad.isFailure)
        val ok = repo.changePassword(MockAuthDatabase.DEMO_PASSWORD, "nuevapass1")
        assertTrue(ok.isSuccess)
        repo.logout()
        assertTrue(repo.login(MockData.currentUser.email, "nuevapass1").isSuccess)
    }

    @Test
    fun legal_consent_gate_and_accept() = runTest {
        repo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val user = repo.getCurrentUser()!!
        assertTrue(repo.hasCurrentLegalConsent(user.id))
        repo.clearConsentsForTests()
        assertFalse(repo.hasCurrentLegalConsent(user.id))
        assertTrue(
            repo.acceptLegalConsents(ConsentMetadata.forPostLoginGate()).isSuccess
        )
        assertTrue(repo.hasCurrentLegalConsent(user.id))
    }

    @Test
    fun delete_account_removes_identity() = runTest {
        repo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        assertTrue(repo.deleteAccount("req-1").isSuccess)
        assertNull(repo.getCurrentUser())
        assertTrue(repo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD).isFailure)
    }

    @Test
    fun logout_clears_user() = runTest {
        repo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        repo.logout()
        assertNull(repo.getCurrentUser())
    }
}
