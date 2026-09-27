package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.repository.AdminMfaFactorState
import com.comunidapp.app.data.repository.MockAdminMfaRepository
import com.comunidapp.app.data.repository.MockAdminSessionRepository
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.auth.AuthState
import com.comunidapp.app.domain.authorization.AdminSessionInfo
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.user.SessionResolvedPerson
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
        SessionResolvedPerson.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
        SessionResolvedPerson.clear()
    }

    @Test
    fun initializing_then_unauthenticated_when_no_session() = runTest(dispatcher) {
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        assertTrue(vm.authState.value is AuthState.Unauthenticated || vm.authState.value is AuthState.Initializing)
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
    fun googleExistingCompletePerson_transientLoadingThenHome() = runTest(dispatcher) {
        val person = completePerson()
        userRepository = FakeUserRepository(person)
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
        authRepository.emitSessionForTests(jwtStub(person))
        advanceUntilIdle()
        assertEquals(SessionState.LoggedIn, vm.sessionState.value)
        assertTrue(vm.authState.value is AuthState.Authenticated)
        assertEquals(person.id, SessionResolvedPerson.current()?.id)
        assertEquals(
            SessionState.LoggedIn,
            SessionNavDisplay.resolve(vm.sessionState.value, lastReadyWasLoggedIn = false)
        )
    }

    @Test
    fun googleSessionWhilePersonHydrates_isLoadingNotLoginOrCompleteProfile() = runTest(dispatcher) {
        userRepository = FakeUserRepository(person = null, getUserDelayMs = 10_000)
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        authRepository.emitSessionForTests(jwtStub(completePerson()))
        advanceTimeBy(100)
        assertEquals(SessionState.Loading, vm.sessionState.value)
        assertTrue(vm.authState.value is AuthState.Initializing)
        assertNotEquals(SessionState.LoggedOut, vm.sessionState.value)
        assertNotEquals(SessionState.ProfileSetupRequired, vm.sessionState.value)
        assertEquals(
            SessionState.Loading,
            SessionNavDisplay.resolve(SessionState.Loading, lastReadyWasLoggedIn = false)
        )
    }

    @Test
    fun googlePersonResolvesAfterDelay_homeNotInfiniteLoading() = runTest(dispatcher) {
        val person = completePerson()
        userRepository = FakeUserRepository(person = null)
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        authRepository.emitSessionForTests(jwtStub(person))
        advanceTimeBy(800)
        assertEquals(SessionState.Loading, vm.sessionState.value)
        userRepository.publish(person)
        advanceUntilIdle()
        assertEquals(SessionState.LoggedIn, vm.sessionState.value)
        assertTrue(vm.authState.value is AuthState.Authenticated)
        assertNotEquals(SessionState.Loading, vm.sessionState.value)
    }

    @Test
    fun googlePersonRpcError_authErrorNotInfiniteSpinner() = runTest(dispatcher) {
        userRepository = FakeUserRepository(person = null, getUserError = IOException("person rpc"))
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        authRepository.emitSessionForTests(jwtStub(completePerson()))
        advanceUntilIdle()
        assertTrue(vm.authState.value is AuthState.AuthError)
        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
        assertNotEquals(SessionState.Loading, vm.sessionState.value)
        assertNotEquals(SessionState.ProfileSetupRequired, vm.sessionState.value)
    }

    @Test
    fun googleNewIncompletePerson_completeProfile() = runTest(dispatcher) {
        val person = completePerson().copy(
            username = null,
            birthDate = null,
            homeLocalityId = null,
            onboardingStatus = "NOT_STARTED"
        )
        userRepository = FakeUserRepository(person)
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        authRepository.emitSessionForTests(jwtStub(person))
        advanceUntilIdle()
        assertEquals(SessionState.ProfileSetupRequired, vm.sessionState.value)
        assertTrue(vm.authState.value is AuthState.ProfileSetupRequired)
        assertNotEquals(SessionState.Loading, vm.sessionState.value)
        assertNotEquals(SessionState.LoggedIn, vm.sessionState.value)
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
    fun logout_cancelsJobsAndDoesNotRehydrate() = runTest(dispatcher) {
        val person = completePerson()
        userRepository = FakeUserRepository(person)
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        authRepository.login(person.email, MockAuthDatabase.DEMO_PASSWORD)
        advanceUntilIdle()
        assertEquals(SessionState.LoggedIn, vm.sessionState.value)
        vm.logout()
        advanceUntilIdle()
        assertTrue(vm.authState.value is AuthState.Unauthenticated)
        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
        assertNull(SessionResolvedPerson.current())
        assertNull(vm.currentUser.value)
        userRepository.publish(person)
        advanceUntilIdle()
        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
        assertNull(SessionResolvedPerson.current())
    }

    @Test
    fun googleALogoutThenBComplete_homeIsBNotA() = runTest(dispatcher) {
        val personA = completePerson()
        val personB = completePerson().copy(
            id = "user_b_google",
            email = "beta@email.com",
            username = "beta.qa",
            displayName = "Beta"
        )
        userRepository = FakeUserRepository(personA)
        authRepository.grantConsentForTests(personB.email)
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        authRepository.emitSessionForTests(jwtStub(personA))
        advanceUntilIdle()
        assertEquals(SessionState.LoggedIn, vm.sessionState.value)
        assertEquals(personA.id, SessionResolvedPerson.current()?.id)
        vm.logout()
        advanceUntilIdle()
        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
        assertNull(SessionResolvedPerson.current())
        assertNull(vm.currentUser.value)
        userRepository.publish(personB)
        authRepository.emitSessionForTests(jwtStub(personB))
        advanceUntilIdle()
        assertEquals(SessionState.LoggedIn, vm.sessionState.value)
        assertTrue(vm.authState.value is AuthState.Authenticated)
        assertEquals(personB.id, SessionResolvedPerson.current()?.id)
        assertEquals(personB.id, vm.currentUser.value?.id)
        assertNotEquals(personA.id, vm.currentUser.value?.id)
        assertNull(SessionResolvedPerson.current()?.takeIf { it.id == personA.id })
    }

    @Test
    fun googleALogoutThenBIncomplete_completeProfileNotA() = runTest(dispatcher) {
        val personA = completePerson()
        val personB = User(
            id = "user_b_new",
            name = "Beta Nueva",
            email = "beta.nueva@email.com",
            emailVerified = true,
            onboardingStatus = "NOT_STARTED"
        )
        userRepository = FakeUserRepository(personA)
        authRepository.grantConsentForTests(personB.email)
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        authRepository.emitSessionForTests(jwtStub(personA))
        advanceUntilIdle()
        assertEquals(SessionState.LoggedIn, vm.sessionState.value)
        vm.logout()
        advanceUntilIdle()
        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
        assertNull(SessionResolvedPerson.current())
        userRepository.publish(personB)
        authRepository.emitSessionForTests(jwtStub(personB))
        advanceUntilIdle()
        assertEquals(SessionState.ProfileSetupRequired, vm.sessionState.value)
        assertTrue(vm.authState.value is AuthState.ProfileSetupRequired)
        assertNotEquals(personA.id, vm.currentUser.value?.id)
        assertEquals(personB.id, vm.currentUser.value?.id)
        assertNull(SessionResolvedPerson.current()?.takeIf { it.id == personA.id })
    }

    @Test
    fun existingCompleteHome_laterJwtStubHydration_staysHome() = runTest(dispatcher) {
        val person = completePerson()
        userRepository = FakeUserRepository(person)
        val vm = SessionViewModel(authRepository, userRepository)
        advanceUntilIdle()
        authRepository.emitSessionForTests(jwtStub(person))
        advanceUntilIdle()
        assertEquals(SessionState.LoggedIn, vm.sessionState.value)
        assertTrue(vm.authState.value is AuthState.Authenticated)
        userRepository.publish(jwtStub(person))
        advanceUntilIdle()
        assertEquals(SessionState.LoggedIn, vm.sessionState.value)
        assertTrue(vm.authState.value is AuthState.Authenticated)
        assertNotEquals(SessionState.ProfileSetupRequired, vm.sessionState.value)
        assertEquals(person.username, vm.currentUser.value?.username)
        assertEquals(person.homeLocalityId, vm.currentUser.value?.homeLocalityId)
    }

    @Test
    fun adminIdentity_goesToAdminSession_withoutPersonOrCompleteProfile() = runTest(dispatcher) {
        userRepository = FakeUserRepository(person = null)
        val adminUser = technicalAdminUser()
        val adminSessions = MockAdminSessionRepository()
        adminSessions.seed(AdminSessionInfo(adminUser.id, mustChangePassword = false, isRoot = true))
        val permissions = MockPermissionRepository()
        permissions.setRolesForTests(adminUser.id, setOf(PlatformRoleCode.SUPERADMIN))
        val vm = SessionViewModel(authRepository, userRepository, adminSessions, permissions, MockAdminMfaRepository())
        advanceUntilIdle()
        authRepository.emitSessionForTests(adminUser)
        advanceUntilIdle()
        assertEquals(SessionState.AdminSession, vm.sessionState.value)
        assertTrue(vm.authState.value is AuthState.AdminAuthenticated)
        assertNull(SessionResolvedPerson.current())
        assertNotEquals(SessionState.ProfileSetupRequired, vm.sessionState.value)
        assertNotEquals(SessionState.LoggedIn, vm.sessionState.value)
    }

    @Test
    fun adminIdentity_mustChangePassword_beforeHub() = runTest(dispatcher) {
        userRepository = FakeUserRepository(person = null)
        val adminUser = technicalAdminUser()
        val adminSessions = MockAdminSessionRepository()
        adminSessions.seed(AdminSessionInfo(adminUser.id, mustChangePassword = true, isRoot = true))
        val permissions = MockPermissionRepository()
        permissions.setRolesForTests(adminUser.id, setOf(PlatformRoleCode.SUPERADMIN))
        val vm = SessionViewModel(authRepository, userRepository, adminSessions, permissions, MockAdminMfaRepository())
        advanceUntilIdle()
        authRepository.emitSessionForTests(adminUser)
        advanceUntilIdle()
        assertEquals(SessionState.AdminPasswordChangeRequired, vm.sessionState.value)
        vm.onAdminPasswordChanged()
        advanceUntilIdle()
        assertEquals(SessionState.AdminSession, vm.sessionState.value)
    }

    @Test
    fun adminIdentityWithoutPermissions_isDeniedNotCompleteProfile() = runTest(dispatcher) {
        userRepository = FakeUserRepository(person = null)
        val adminUser = technicalAdminUser()
        val adminSessions = MockAdminSessionRepository()
        adminSessions.seed(AdminSessionInfo(adminUser.id, mustChangePassword = false, isRoot = false))
        val permissions = MockPermissionRepository()
        permissions.setRolesForTests(adminUser.id, setOf(PlatformRoleCode.USER))
        val vm = SessionViewModel(authRepository, userRepository, adminSessions, permissions, MockAdminMfaRepository())
        advanceUntilIdle()
        authRepository.emitSessionForTests(adminUser)
        advanceUntilIdle()
        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
        assertTrue(vm.authState.value is AuthState.AuthError)
        assertNotEquals(SessionState.ProfileSetupRequired, vm.sessionState.value)
        assertNull(SessionResolvedPerson.current())
    }

    @Test
    fun adminIdentity_withoutVerifiedFactor_requiresEnrollmentNotHub() = runTest(dispatcher) {
        userRepository = FakeUserRepository(person = null)
        val adminUser = technicalAdminUser()
        val adminSessions = MockAdminSessionRepository()
        adminSessions.seed(AdminSessionInfo(adminUser.id, mustChangePassword = false, isRoot = true, mfaRequired = true))
        val permissions = MockPermissionRepository()
        permissions.setRolesForTests(adminUser.id, setOf(PlatformRoleCode.SUPERADMIN))
        val mfa = MockAdminMfaRepository()
        mfa.factorState = AdminMfaFactorState.ENROLLMENT_REQUIRED
        val vm = SessionViewModel(authRepository, userRepository, adminSessions, permissions, mfa)
        advanceUntilIdle()
        authRepository.emitSessionForTests(adminUser)
        advanceUntilIdle()
        assertEquals(SessionState.AdminMfaEnrollmentRequired, vm.sessionState.value)
        assertNotEquals(SessionState.AdminSession, vm.sessionState.value)
        assertNull(SessionResolvedPerson.current())
        mfa.verifySucceeds = true
        val result = vm.verifyAdminMfaCode("123456")
        advanceUntilIdle()
        assertTrue(result.isSuccess)
        assertEquals(SessionState.AdminSession, vm.sessionState.value)
    }

    @Test
    fun adminIdentity_withVerifiedFactor_requiresChallengeNotHub() = runTest(dispatcher) {
        userRepository = FakeUserRepository(person = null)
        val adminUser = technicalAdminUser()
        val adminSessions = MockAdminSessionRepository()
        adminSessions.seed(AdminSessionInfo(adminUser.id, mustChangePassword = false, isRoot = false, mfaRequired = true))
        val permissions = MockPermissionRepository()
        permissions.setRolesForTests(adminUser.id, setOf(PlatformRoleCode.MODERATOR))
        val mfa = MockAdminMfaRepository()
        mfa.factorState = AdminMfaFactorState.CHALLENGE_REQUIRED
        val vm = SessionViewModel(authRepository, userRepository, adminSessions, permissions, mfa)
        advanceUntilIdle()
        authRepository.emitSessionForTests(adminUser)
        advanceUntilIdle()
        assertEquals(SessionState.AdminMfaChallengeRequired, vm.sessionState.value)
        mfa.verifySucceeds = false
        val denied = vm.verifyAdminMfaCode("000000")
        advanceUntilIdle()
        assertTrue(denied.isFailure)
        assertEquals(SessionState.AdminMfaChallengeRequired, vm.sessionState.value)
        mfa.verifySucceeds = true
        val ok = vm.verifyAdminMfaCode("654321")
        advanceUntilIdle()
        assertTrue(ok.isSuccess)
        assertEquals(SessionState.AdminSession, vm.sessionState.value)
    }

    @Test
    fun adminFirstLogin_passwordThenEnrollmentBeforeHub() = runTest(dispatcher) {
        userRepository = FakeUserRepository(person = null)
        val adminUser = technicalAdminUser()
        val adminSessions = MockAdminSessionRepository()
        adminSessions.seed(AdminSessionInfo(adminUser.id, mustChangePassword = true, isRoot = false, mfaRequired = true))
        val permissions = MockPermissionRepository()
        permissions.setRolesForTests(adminUser.id, setOf(PlatformRoleCode.SUPPORT))
        val mfa = MockAdminMfaRepository()
        mfa.factorState = AdminMfaFactorState.ENROLLMENT_REQUIRED
        val vm = SessionViewModel(authRepository, userRepository, adminSessions, permissions, mfa)
        advanceUntilIdle()
        authRepository.emitSessionForTests(adminUser)
        advanceUntilIdle()
        assertEquals(SessionState.AdminPasswordChangeRequired, vm.sessionState.value)
        assertNotEquals(SessionState.AdminSession, vm.sessionState.value)
        adminSessions.clearMustChangePassword()
        vm.onAdminPasswordChanged()
        advanceUntilIdle()
        assertEquals(SessionState.AdminMfaEnrollmentRequired, vm.sessionState.value)
        vm.verifyAdminMfaCode("111111")
        advanceUntilIdle()
        assertEquals(SessionState.AdminSession, vm.sessionState.value)
    }

    @Test
    fun adminLogout_thenPersonLogin_doesNotKeepAdminSession() = runTest(dispatcher) {
        userRepository = FakeUserRepository(person = null)
        val adminUser = technicalAdminUser()
        val adminSessions = MockAdminSessionRepository()
        adminSessions.seed(AdminSessionInfo(adminUser.id, mustChangePassword = false, isRoot = true))
        val permissions = MockPermissionRepository()
        permissions.setRolesForTests(adminUser.id, setOf(PlatformRoleCode.SUPERADMIN))
        val vm = SessionViewModel(authRepository, userRepository, adminSessions, permissions, MockAdminMfaRepository())
        advanceUntilIdle()
        authRepository.emitSessionForTests(adminUser)
        advanceUntilIdle()
        assertEquals(SessionState.AdminSession, vm.sessionState.value)
        vm.logout()
        advanceUntilIdle()
        assertEquals(SessionState.LoggedOut, vm.sessionState.value)
        assertNull(SessionResolvedPerson.current())
        userRepository = FakeUserRepository(completePerson())
        val personVm = SessionViewModel(authRepository, userRepository, adminSessions, permissions, MockAdminMfaRepository())
        advanceUntilIdle()
        personVm.signIn(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        advanceUntilIdle()
        assertEquals(SessionState.LoggedIn, personVm.sessionState.value)
        assertTrue(personVm.authState.value is AuthState.Authenticated)
        assertNotEquals(SessionState.AdminSession, personVm.sessionState.value)
    }

    @Test
    fun transientAuthStatesAreFlagged() {
        assertTrue(AuthState.Authenticating.isTransient)
        assertTrue(AuthState.Registering.isTransient)
        assertTrue(AuthState.SigningOut.isTransient)
        assertTrue(AuthState.Initializing.isTransient)
        assertTrue(!AuthState.Unauthenticated.isTransient)
        assertTrue(!AuthState.Authenticated(com.comunidapp.app.domain.auth.AuthUser("1")).isTransient)
    }

    private fun technicalAdminUser(): User = User(
        id = "admin-tech-1",
        name = "",
        email = "hidden-internal@invalid",
        emailVerified = true,
        onboardingStatus = "IN_PROGRESS"
    )

    private fun completePerson(): User = MockData.currentUser.copy(
        birthDate = "1990-01-15",
        homeLocalityId = "loc-caba-1",
        onboardingStatus = "COMPLETED"
    )

    private fun jwtStub(person: User): User = User(
        id = person.id,
        name = person.name,
        email = person.email,
        emailVerified = true,
        onboardingStatus = "IN_PROGRESS"
    )

    private class FakeUserRepository(
        person: User? = MockData.currentUser,
        private val getUserError: Throwable? = null,
        private val getUserDelayMs: Long = 0L
    ) : UserRepository {
        private val people = MutableStateFlow(
            listOfNotNull(person).associateBy { it.id }
        )

        fun publish(value: User?) {
            people.value = if (value == null) {
                emptyMap()
            } else {
                people.value + (value.id to value)
            }
        }

        override suspend fun getUser(userId: String): User? {
            if (getUserDelayMs > 0) delay(getUserDelayMs)
            getUserError?.let { throw it }
            return people.value[userId]
        }

        override suspend fun createUser(user: User) = Result.success(Unit)
        override suspend fun updateUser(user: User) = Result.success(Unit)
        override suspend fun searchUsers(query: String, excludeUserId: String): List<User> = emptyList()
        override fun observeUser(userId: String): Flow<User?> = people.map { it[userId] }
        override fun observeUsers(): Flow<List<User>> = people.map { it.values.toList() }
    }
}
