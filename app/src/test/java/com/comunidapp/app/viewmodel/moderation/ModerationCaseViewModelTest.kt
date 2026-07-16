package com.comunidapp.app.viewmodel.moderation

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockModerationRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.moderation.ModerationCase
import com.comunidapp.app.domain.moderation.ModerationCaseStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ModerationCaseViewModelTest {

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
                title = "Caso uno",
                status = ModerationCaseStatus.OPEN,
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
    fun queue_denied_for_user() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.USER))
        val vm = ModerationCaseQueueViewModel(moderation, auth, permissions)
        advanceUntilIdle()
        assertEquals(AdministrativeScreenPhase.AccessDenied, vm.uiState.value.phase)
    }

    @Test
    fun detail_lists_case_and_blocks_double_assign() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.MODERATOR))
        val vm = ModerationCaseDetailViewModel("c1", moderation, auth, permissions) { 10L }
        advanceUntilIdle()
        assertEquals(AdministrativeScreenPhase.Content, vm.uiState.value.phase)
        vm.assignToMe()
        vm.assignToMe()
        advanceUntilIdle()
        assertEquals(MockData.currentUser.id, vm.uiState.value.detail?.case?.assignedToUserId)
        assertTrue(vm.uiState.value.message != null)
    }
}
