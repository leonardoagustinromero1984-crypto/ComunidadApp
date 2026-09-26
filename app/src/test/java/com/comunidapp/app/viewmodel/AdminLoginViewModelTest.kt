package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.repository.MockAdministrativeIdentityStore
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.domain.authorization.AdminSessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminLoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: MockAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = MockAuthRepository()
        repo.resetForTests()
        MockAdministrativeIdentityStore.seed(
            MockAdministrativeIdentityStore.Entry(
                username = "qa.admin",
                password = "test-admin-pass",
                user = User(
                    id = "admin-tech-1",
                    name = "",
                    email = "hidden-internal@invalid",
                    emailVerified = true
                ),
                session = AdminSessionInfo(
                    userId = "admin-tech-1",
                    mustChangePassword = false,
                    isRoot = true
                )
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
        MockAdministrativeIdentityStore.reset()
    }

    @Test
    fun validTechnicalCredentials_markLoggedIn() = runTest(dispatcher) {
        val vm = AdminLoginViewModel(repo)
        vm.onUsernameChange("qa.admin")
        vm.onPasswordChange("test-admin-pass")
        vm.login()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoggedIn)
        assertEquals(null, vm.uiState.value.error)
    }

    @Test
    fun wrongPassword_genericError() = runTest(dispatcher) {
        val vm = AdminLoginViewModel(repo)
        vm.onUsernameChange("qa.admin")
        vm.onPasswordChange("wrong")
        vm.login()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoggedIn)
        assertEquals("Usuario o contraseña incorrectos.", vm.uiState.value.error)
    }
}
