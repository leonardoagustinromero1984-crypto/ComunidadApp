package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.auth.AuthDeepLinkKind
import com.comunidapp.app.domain.auth.AuthState
import com.comunidapp.app.domain.auth.DeleteAccountCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSecurityViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: MockAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = MockAuthRepository()
        repo.resetForTests()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun change_password_success() = runTest(dispatcher) {
        repo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val vm = AccountSecurityViewModel(repo)
        vm.onCurrentPasswordChange(MockAuthDatabase.DEMO_PASSWORD)
        vm.onNewPasswordChange("password9")
        vm.onConfirmPasswordChange("password9")
        vm.changePassword()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.passwordChangeSuccess)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun delete_requires_confirmation_phrase() = runTest(dispatcher) {
        repo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val vm = AccountSecurityViewModel(repo)
        vm.onDeletePasswordChange(MockAuthDatabase.DEMO_PASSWORD)
        vm.onDeleteAcknowledgedChange(true)
        vm.onDeleteConfirmationTextChange("borrar")
        vm.deleteAccount()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.deleteSuccess)
        assertTrue(vm.uiState.value.errorMessage!!.contains(DeleteAccountCommand.CONFIRMATION_PHRASE))
    }

    @Test
    fun delete_success() = runTest(dispatcher) {
        repo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val vm = AccountSecurityViewModel(repo)
        vm.onDeletePasswordChange(MockAuthDatabase.DEMO_PASSWORD)
        vm.onDeleteAcknowledgedChange(true)
        vm.onDeleteConfirmationTextChange(DeleteAccountCommand.CONFIRMATION_PHRASE)
        vm.deleteAccount()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deleteSuccess)
        assertNull(repo.getCurrentUser())
    }

    @Test
    fun password_reset_active_submit() = runTest(dispatcher) {
        repo.activateRecoverySession(MockData.currentUser.email)
        val vm = PasswordResetActiveViewModel(repo)
        vm.onNewPasswordChange("password9")
        vm.onConfirmPasswordChange("password9")
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.success)
        // second submit blocked
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.success)
    }

    @Test
    fun session_password_reset_from_deep_link() = runTest(dispatcher) {
        val userRepo = object : UserRepository {
            private val profile = MockData.currentUser
            override suspend fun getUser(userId: String): User? =
                if (userId == profile.id) profile else null
            override suspend fun createUser(user: User) = Result.success(Unit)
            override suspend fun updateUser(user: User) = Result.success(Unit)
            override suspend fun searchUsers(query: String, excludeUserId: String) = emptyList<User>()
            override fun observeUser(userId: String): Flow<User?> =
                flowOf(if (userId == profile.id) profile else null)
            override fun observeUsers(): Flow<List<User>> = flowOf(listOf(profile))
        }
        val session = SessionViewModel(repo, userRepo)
        advanceUntilIdle()
        session.onAuthDeepLink(AuthDeepLinkKind.PasswordRecovery)
        assertTrue(session.authState.value is AuthState.PasswordResetActive)
        assertEquals(SessionState.PasswordResetActive, session.sessionState.value)
        session.clearPasswordResetActive()
        assertTrue(session.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun legal_consent_required_accept() = runTest(dispatcher) {
        val userRepo = object : UserRepository {
            private val profile = MockData.currentUser
            override suspend fun getUser(userId: String): User? =
                if (userId == profile.id) profile else null
            override suspend fun createUser(user: User) = Result.success(Unit)
            override suspend fun updateUser(user: User) = Result.success(Unit)
            override suspend fun searchUsers(query: String, excludeUserId: String) = emptyList<User>()
            override fun observeUser(userId: String): Flow<User?> =
                flowOf(if (userId == profile.id) profile else null)
            override fun observeUsers(): Flow<List<User>> = flowOf(listOf(profile))
        }
        repo.resetForTests()
        repo.clearConsentsForTests()
        val session = SessionViewModel(repo, userRepo)
        advanceUntilIdle()
        session.signIn(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        advanceUntilIdle()
        assertTrue(session.authState.value is AuthState.LegalConsentRequired)
        session.acceptLegalConsents(acceptedTerms = true, acceptedPrivacy = true)
        advanceUntilIdle()
        assertTrue(session.authState.value is AuthState.Authenticated)
    }
}
