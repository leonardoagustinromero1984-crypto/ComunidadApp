package com.comunidapp.app.viewmodel.verification

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockOrganizationVerificationRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.organization.OrganizationVerificationStatus
import com.comunidapp.app.domain.verification.OrganizationVerificationDecision
import com.comunidapp.app.domain.verification.OrganizationVerificationReview
import com.comunidapp.app.domain.verification.OrganizationVerificationReviewStatus
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
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
class OrganizationVerificationViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var auth: MockAuthRepository
    private lateinit var permissions: MockPermissionRepository
    private lateinit var repo: MockOrganizationVerificationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        auth = MockAuthRepository()
        auth.resetForTests()
        permissions = MockPermissionRepository()
        permissions.resetForTests()
        repo = MockOrganizationVerificationRepository()
        repo.resetForTests()
        repo.seed(
            OrganizationVerificationReview(
                id = "v1",
                organizationId = "org1",
                requestedByUserId = "u2",
                status = OrganizationVerificationReviewStatus.PENDING_REVIEW,
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
    fun queue_denied_for_moderator() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.MODERATOR))
        val vm = OrganizationVerificationQueueViewModel(repo, auth, permissions)
        advanceUntilIdle()
        assertEquals(AdministrativeScreenPhase.AccessDenied, vm.uiState.value.phase)
    }

    @Test
    fun conflict_when_org_member_reviews() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.ADMIN))
        val vm = OrganizationVerificationReviewViewModel(
            reviewId = "v1",
            repository = repo,
            authRepository = auth,
            permissionRepository = permissions,
            clock = { 10L },
            orgStatusProvider = { OrganizationVerificationStatus.PENDING },
            isOrgMemberProvider = { true }
        )
        advanceUntilIdle()
        vm.decide(OrganizationVerificationDecision.APPROVE, "ok")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.message!!.contains("Conflicto", ignoreCase = true))
    }
}
