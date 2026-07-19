package com.comunidapp.app.viewmodel.moderation

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockModerationRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.moderation.ModerationPriority
import com.comunidapp.app.domain.moderation.ModerationReport
import com.comunidapp.app.domain.moderation.ModerationReportStatus
import com.comunidapp.app.domain.moderation.ModerationTargetRef
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModerationQueueViewModelTest {

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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun deny_by_default_without_permission() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.USER))
        val vm = ModerationQueueViewModel(moderation, auth, permissions)
        advanceUntilIdle()
        assertEquals(AdministrativeScreenPhase.AccessDenied, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.reports.isEmpty())
    }

    @Test
    fun allow_moderator_and_list() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.MODERATOR))
        moderation.seedReport(
            ModerationReport(
                id = "r1",
                reporterId = "secret-reporter",
                target = ModerationTargetRef(ModerationTargetType.POST, "p1"),
                reasonCode = "spam",
                status = ModerationReportStatus.OPEN,
                priority = ModerationPriority.HIGH,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
        val vm = ModerationQueueViewModel(moderation, auth, permissions)
        advanceUntilIdle()
        assertEquals(AdministrativeScreenPhase.Content, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.filtered.size)
        assertEquals("", vm.uiState.value.filtered.first().reporterId)
        assertFalse(vm.uiState.value.canViewSensitive)
    }

    @Test
    fun filter_by_status() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.MODERATOR))
        moderation.seedReport(
            ModerationReport(
                id = "r1",
                reporterId = "a",
                target = ModerationTargetRef(ModerationTargetType.POST, "p1"),
                reasonCode = "spam",
                status = ModerationReportStatus.OPEN,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
        moderation.seedReport(
            ModerationReport(
                id = "r2",
                reporterId = "b",
                target = ModerationTargetRef(ModerationTargetType.POST, "p2"),
                reasonCode = "spam",
                status = ModerationReportStatus.TRIAGED,
                createdAtEpochMs = 2L,
                updatedAtEpochMs = 2L
            )
        )
        val vm = ModerationQueueViewModel(moderation, auth, permissions)
        advanceUntilIdle()
        vm.setStatusFilter(ModerationReportStatus.OPEN)
        assertEquals(1, vm.uiState.value.filtered.size)
        assertEquals("r1", vm.uiState.value.filtered.first().id)
    }
}
