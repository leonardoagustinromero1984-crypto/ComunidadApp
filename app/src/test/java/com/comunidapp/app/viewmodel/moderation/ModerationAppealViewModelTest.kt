package com.comunidapp.app.viewmodel.moderation

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockModerationRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.moderation.ModerationAction
import com.comunidapp.app.domain.moderation.ModerationActionType
import com.comunidapp.app.domain.moderation.ModerationAppeal
import com.comunidapp.app.domain.moderation.ModerationAppealStatus
import com.comunidapp.app.domain.moderation.ModerationCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModerationAppealViewModelTest {

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
                createdByUserId = "staff",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
        moderation.seedAction(
            ModerationAction(
                id = "a1",
                caseId = "c1",
                target = ModerationTargetRef(ModerationTargetType.USER_PROFILE, "u1"),
                actionType = ModerationActionType.WARNING,
                reasonCode = "policy_violation",
                appliedByUserId = MockData.currentUser.id,
                appliedAtEpochMs = 1L
            )
        )
        moderation.seedAppeal(
            ModerationAppeal(
                id = "ap1",
                actionId = "a1",
                submittedByUserId = "other",
                statement = "Esto es una declaración suficientemente larga",
                status = ModerationAppealStatus.SUBMITTED,
                createdAtEpochMs = 2L
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun conflict_when_applier_reviews() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.MODERATOR))
        val vm = ModerationAppealQueueViewModel(moderation, auth, permissions) { 10L }
        advanceUntilIdle()
        vm.review("ap1", ModerationAppealStatus.UPHELD, "motivo suficiente")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.message!!.contains("conflicto", ignoreCase = true) ||
            vm.uiState.value.message!!.contains("Operación", ignoreCase = true) ||
            vm.uiState.value.message!!.contains("válidos", ignoreCase = true))
    }

    @Test
    fun my_appeals_filters_client_side() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        moderation.seedAppeal(
            ModerationAppeal(
                id = "ap2",
                actionId = "a1",
                submittedByUserId = MockData.currentUser.id,
                statement = "Mi apelación con texto suficiente",
                status = ModerationAppealStatus.SUBMITTED,
                createdAtEpochMs = 3L
            )
        )
        val vm = MyModerationAppealsViewModel(moderation, auth) { 10L }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.appeals.size)
        assertEquals("ap2", vm.uiState.value.appeals.first().id)
    }
}
