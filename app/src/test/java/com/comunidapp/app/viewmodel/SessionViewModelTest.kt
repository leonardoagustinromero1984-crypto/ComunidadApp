package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.auth.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepository: MockAuthRepository
    private lateinit var userRepository: FakeUserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = MockAuthRepository()
        authRepository.resetForTests()
        userRepository = FakeUserRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun initializing_then_unauthenticated_when_no_session() = runTest(dispatcher) {
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        assertTrue(vm.authState.value is AuthState.Unauthenticated || vm.authState.value is AuthState.Initializing)
        // After observe emits null:
        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
        assertTrue(vm.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun signIn_success_to_authenticated() = runTest(dispatcher) {
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        vm.signIn(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        advanceUntilIdle()
        assertTrue(vm.authState.value is AuthState.Authenticated)
        assertEquals(SessionState.LoggedIn, vm.sessionState.value)
    }

    @Test
    fun signIn_error_maps_to_authError() = runTest(dispatcher) {
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        vm.signIn(MockData.currentUser.email, "badbadbad")
        advanceUntilIdle()
        assertTrue(vm.authState.value is AuthState.AuthError)
        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
    }

    @Test
    fun logout_to_unauthenticated() = runTest(dispatcher) {
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        vm.signIn(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        advanceUntilIdle()
        vm.logout()
        advanceUntilIdle()
        assertTrue(vm.authState.value is AuthState.Unauthenticated)
        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
    }

    @Test
    fun transient_states_block_duplicate_work() {
        assertTrue(AuthState.Authenticating.isTransient)
        assertTrue(AuthState.Registering.isTransient)
        assertTrue(AuthState.SigningOut.isTransient)
        assertTrue(AuthState.Initializing.isTransient)
        assertTrue(!AuthState.Unauthenticated.isTransient)
        assertTrue(!AuthState.Authenticated(com.comunidapp.app.domain.auth.AuthUser("1")).isTransient)
    }

    private class FakeUserRepository : UserRepository {
        private val profile = MockData.currentUser
        override suspend fun getUser(userId: String): User? =
            if (userId == profile.id) profile else null
        override suspend fun createUser(user: User) = Result.success(Unit)
        override suspend fun updateUser(user: User) = Result.success(Unit)
        override suspend fun searchUsers(query: String, excludeUserId: String): List<User> = emptyList()
        override fun observeUser(userId: String): Flow<User?> =
            flowOf(if (userId == profile.id) profile else null)
        override fun observeUsers(): Flow<List<User>> = flowOf(listOf(profile))
    }
}
