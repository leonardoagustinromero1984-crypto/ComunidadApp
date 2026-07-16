package com.comunidapp.app.viewmodel.moderation

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockModerationRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.moderation.ModerationReport
import com.comunidapp.app.domain.moderation.ModerationReportStatus
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import com.comunidapp.app.domain.moderation.ModerationTargetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModerationReportDetailViewModelTest {

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
        moderation.seedReport(
            ModerationReport(
                id = "r1",
                reporterId = "reporter-secret",
                target = ModerationTargetRef(ModerationTargetType.POST, "p1"),
                reasonCode = "spam",
                status = ModerationReportStatus.OPEN,
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
    fun deny_without_permission() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.USER))
        val vm = ModerationReportDetailViewModel("r1", moderation, auth, permissions)
        advanceUntilIdle()
        assertEquals(AdministrativeScreenPhase.AccessDenied, vm.uiState.value.phase)
    }

    @Test
    fun triage_and_block_double_submit() = runTest {
        val standard = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(standard)
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.MODERATOR))
        val vm = ModerationReportDetailViewModel("r1", moderation, auth, permissions) { 100L }
        advanceUntilIdle()
        assertNull(vm.uiState.value.visibleReporterId)
        vm.triage(ModerationReportStatus.TRIAGED)
        vm.triage(ModerationReportStatus.DISMISSED)
        advanceUntilIdle()
        assertEquals(ModerationReportStatus.TRIAGED, vm.uiState.value.report?.status)
        assertTrue(vm.uiState.value.message != null)
        Dispatchers.setMain(dispatcher)
    }
}
