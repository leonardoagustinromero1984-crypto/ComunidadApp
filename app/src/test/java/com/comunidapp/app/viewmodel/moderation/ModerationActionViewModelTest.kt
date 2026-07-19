package com.comunidapp.app.viewmodel.moderation

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockModerationRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.moderation.ModerationActionType
import com.comunidapp.app.domain.moderation.ModerationCase
import com.comunidapp.app.domain.moderation.ModerationTargetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ModerationActionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var auth: MockAuthRepository
    private lateinit var permissions: MockPermissionRepository
    private lateinit var moderation: MockModerationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        auth = MockAuthRepository()
        auth.resetForTests()
        permissions = MockPermissionRepository()
        permissions.resetForTests()
        moderation = MockModerationRepository()
        moderation.resetForTests()
        moderation.seedCase(
            ModerationCase(
                id = "c1",
                title = "Caso",
                createdByUserId = "u1",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun temporary_requires_expiry() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.ADMIN))
        val now = 1000L
        val vm = ModerationActionViewModel("c1", moderation, auth, permissions) { now }
        advanceUntilIdle()
        vm.setActionType(ModerationActionType.ACCOUNT_SUSPENDED)
        vm.setTarget(ModerationTargetType.USER_PROFILE, "user-1")
        vm.setExpiry(null)
        assertFalse(vm.validateLocal())
        assertEquals("TEMPORARY_REQUIRES_EXPIRY", vm.uiState.value.validationError)
        vm.setExpiry(now + 60_000)
        assertTrue(vm.validateLocal())
    }

    @Test
    fun submit_requires_confirmation_and_blocks_double() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.ADMIN))
        val now = 1000L
        val vm = ModerationActionViewModel("c1", moderation, auth, permissions) { now }
        advanceUntilIdle()
        vm.setActionType(ModerationActionType.WARNING)
        vm.setTarget(ModerationTargetType.USER_PROFILE, "user-1")
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.message!!.contains("Confirmá"))
        vm.setConfirmed(true)
        vm.submit()
        advanceUntilIdle()
        assertEquals("Medida aplicada", vm.uiState.value.message)
        vm.submit()
        advanceUntilIdle()
        assertEquals("Confirmá la medida antes de aplicarla.", vm.uiState.value.message)
    }
}
